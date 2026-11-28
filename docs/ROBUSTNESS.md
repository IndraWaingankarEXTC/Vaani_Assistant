# The ten ways a normal user breaks an app — audited against Vaani

The list comes from a video about vibe-coded **web** apps. Three of the ten describe a server, and
Vaani has no server, so those are answered as "does not apply" rather than quietly ignored. The
other seven land squarely on this app, and two of them were real bugs.

"Probability" below is a judgement about how often a real user would hit the problem, not a
measurement.

| # | The failure | Applies? | Probability before | After |
|---|---|---|---|---|
| 1 | Double tapped pay | **Yes** | Medium | Low |
| 2 | Capital letter email | **Yes** | High | Low |
| 3 | Apostrophe in a name | **Yes** | High | Low |
| 4 | iPhone photo upload | No, but its cousin does | — | — |
| 5 | Password reset email | No | — | — |
| 6 | Another time zone | Weakly | Low | Low |
| 7 | Lost signal after paying | **Yes** | Medium | Low |
| 8 | No delete button | **Yes** | High | None |
| 9 | Hid my email | **Yes** | Medium | Low |
| 10 | 10,000 at once | No, but its cousins do | — | — |

---

## 1. Double tapped pay — fixed

The summary card already ignored a second tap (`SummaryPopup.decided`), but the *gate* underneath
did not. A retried step, a re-entrant plan, or a second command arriving in the same second could
have pressed "Place order" twice.

`Executor.authorise` is now single-entry: an `AtomicBoolean` is claimed before the summary is even
built and released in a `finally`. A second caller logs `commit already in flight` and returns.

## 2. Capital letter email — fixed, two separate bugs

Dictation capitalises the first letter of a sentence, which is right until the sentence *is* an
address.

- `indra@gmail.com` was becoming **`Indra@Gmail.Com`** — the capitaliser treated every dot as a
  sentence end, and capitalised inside the token.
- `gmail.com` was becoming **`gmail. com`** — a tidy-up rule inserted a space after punctuation
  before a letter, which is correct for `hello,world` and wrong for a domain.

Now a dot only ends a sentence when whitespace or end-of-text follows it, and a token containing
`@`, `://` or starting `www.` is never capitalised. Covered by `PunctuationTest`.

## 3. Apostrophe in a name — fixed

Speech gives "OBrien" or "o brien"; the phone book says "O'Brien". `Text.norm` turns the apostrophe
into a space, so the two never matched — and **D'Souza is a common Indian surname**, so this was not
a theoretical case.

`nlu/NameMatch.kt` compares names with punctuation *and spacing* removed (`obrien` == `obrien`), and
`ContactResolver` now pre-filters with a fragment that has no apostrophe (`brien`), falling back to
a bounded 500-row scan when the fragment matches nothing. Deliberately strict elsewhere: "ram" does
**not** resolve to "Ramkumar", because calling the wrong person is worse than saying "I couldn't
find ram". Covered by `NameMatchTest`.

## 4. iPhone photo upload — does not apply, but its cousin is real

There is no upload anywhere in Vaani. The same shape of failure does exist though: **apps that
expose a useless accessibility tree** — Flutter, Compose, games, anything canvas-drawn. The
snapshot comes back empty or full of unlabelled boxes and every step fails.

Mitigations already in place: a gesture-tap fallback when a node refuses `ACTION_CLICK`, obstacle
clearing, and the guidance path that tells you where the control is. There is no fix that makes an
unreadable screen readable; the honest answer is that Vaani degrades to "I can't see that" instead
of tapping blindly.

## 5. Password reset email — does not apply

No accounts, no sign-in, no email is ever sent. The nearest analogue is a missing optional
dependency: no `model.gguf` or no native library means the LLM planner reports unavailable and the
rule grammar answers alone. That path is exercised on every install, since neither file ships.

## 6. Another time zone — audited, not a risk

Worth checking properly rather than assuming:

- Every clock read goes through `Calendar.getInstance()` — device local time.
- `TimeParser.clockFromNow` adds seconds to local time; no UTC is stored anywhere.
- Alarms and timers are `AlarmClock` intents with local hour/minute, which is what the user means
  when they say "7 baje" in a new city.

There is no persisted timestamp that could be misread across zones. The only stored times are
`Prefs.pendingCommit` (used as an age, so zone-independent) and the in-memory log.

## 7. Lost signal after paying — fixed

Vaani is offline, so there is no signal to lose. The equivalent is **the phone dying between the
tap and the confirmation**. Before, that left an order of unknown state and no record of it.

`Executor` now writes a journal entry (`Prefs.pendingCommit`) *before* the authorised tap and clears
it immediately after. If the marker is still there on the next start, the service says:

> "Last time I pressed 'Place order' and then stopped. I don't know whether it went through —
> please check your orders."

Erring toward a wasted check rather than a duplicate order.

## 8. No delete button — fixed

There was no way to remove anything without `adb`. The app now has three, each behind a
confirmation dialog: **Clear log**, **Delete taught recipes**, **Reset all settings**.

## 9. Hid my email — fixed, and this one was dangerous

The "hidden value" failure here is a text field that reports no text — password fields, and some
custom inputs. `appendText` rebuilt the field from an empty string, which would have **wiped the
existing contents** on the first dictated sentence.

Now: a password field is refused outright, and any other unreadable field falls back to putting the
sentence on the clipboard and issuing `ACTION_PASTE` at the cursor, which adds without needing to
read.

## 10. 10,000 at once — does not apply, but two cousins do

No server, no connection pool, no 500s. The load-related risks that do exist:

- **A burst of voice commands.** Handled by the `busy` flag ("I'm still working on the last one")
  and now by the commit lock.
- **A screen with thousands of nodes.** The accessibility walk is capped at 400 nodes and depth 30.
- **A very long dictation.** Each sentence rebuilds the field via `ACTION_SET_TEXT`, so a 5,000-word
  note does quadratic work. Not fixed; the practical limit is a few thousand words before appends
  get sluggish.

---

## What this audit does not cover

Nothing here has run on a phone. These are code-level fixes to code-level failure modes; the
behaviour against real apps, the wake-word threshold and the overlay card are still unverified.
See `docs/VERIFICATION.md`.

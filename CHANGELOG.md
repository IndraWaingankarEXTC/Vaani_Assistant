# Changelog

## v3.7.0 — Vaani in the notification shade

- **`qs/VaaniTileService.kt`**: a Quick Settings tile that switches listening on and off, like
  Bluetooth. On, the tile is highlighted and reads *Listening*; the wake word then works from any
  app and the rest is voice.
- **`qs/LensTileService.kt`**: a second tile that opens the camera lens in one tap, from the lock
  screen too (`unlockAndRun`). Uses the PendingIntent form of `startActivityAndCollapse` on Android
  14+, the Intent form below it.
- `docs/QS.md` explains adding them, and states plainly what a third-party tile cannot do: mobile
  data needs `MODIFY_PHONE_STATE`, which is system-only, so Vaani opens the internet panel instead.

No app behaviour changed otherwise.


## v3.6.0 — voice that works, emoji, Spotify, cinema, simpler screen

Reported from the first real install.

### Voice
- **The offline preference was the bug.** `EXTRA_PREFER_OFFLINE` was set unconditionally, so on a
  phone without the downloaded language pack every single attempt failed. It now prefers on-device
  and silently retries once through the network on a network/offline/client error.
- **Silence windows were too short.** 1.4 s of silence ended the command mid-sentence. Now 2.2 s.
- Hard stop 10 s → 15 s.

### New commands
- **Emoji** — "send a heart emoji", "send thumbs up emoji". 40 names mapped; the character is
  appended to the open message field, because the keyboard's emoji picker cannot be driven through
  the accessibility tree.
- **Spotify** — "play lofi on spotify", "spotify play arijit singh". Uses `spotify:search:` so no
  screen control is needed.
- **Cinema** — "book movie tickets", "new movies" → BookMyShow.

### Screen
- Permissions are **listed, not popped up at launch**. The button names exactly what is missing.
- Every button is 56 dp tall with 16 sp text.

### Tests
- 113 (was 109). New: `EmojiAndMediaTest`.
- Behaviour change: "play lofi beats on spotify" now returns `SpotifyPlay` rather than
  `OpenApp("spotify")` — the old path typed into Spotify's search box, which needs screen control
  and fails often. The test was updated to the new contract.


## v3.5.1 — tooling brought up to date

No app behaviour changed. The build warnings are gone.

- `actions/checkout@v7`, `actions/setup-java@v6`, `actions/upload-artifact@v7` — the previous
  majors ran on Node 20, which GitHub has deprecated.
- Gradle 8.7 → **8.11.1**, the newest 8.x. Gradle 9 is deliberately not used: AGP 8.5.2 does not
  support it, and moving Gradle alone is how a working build breaks.
- AGP 8.5.2 and Kotlin 1.9.24 stay. They are a matched set with Gradle, and a version bump that
  cannot be tested is a worse risk than a version that is one release behind.


## v3.5.0 — compress to a size, and convert on the spot

- **Target-size compression** (`compress/Compressor.kt`): "compress this to 15 KB" binary-searches
  the encoder quality, then shrinks the image if quality alone cannot reach the budget, returning the
  highest quality that fits. Tested against a synthetic encoder, so the search is verified without a
  phone.
- **Image compression** (`compress/ImageCompressor.kt`): drives that search with a real JPEG encoder.
- **PDF compression** (`compress/PdfCompressor.kt`): renders pages at decreasing scale, re-encodes as
  JPEG and assembles a new PDF. Text stops being selectable — the same trade every online tool makes,
  and it is stated in the UI.
- **Converter gains a compress mode**: 15 / 50 / 100 / 500 KB buttons, or arrive with a budget by
  voice.
- **Voice**: `CompressFile(targetBytes)` and `ConvertTo(format)`, so "compress this to 15 KB" and
  "convert this to pdf" open the right thing already configured.

### Fixed
- A regression caught by the tests: the new reading rules had swallowed "go back", turning a
  navigation command into a read-aloud one. Reading controls are now only explicit phrases
  ("next paragraph", "previous part"); bare "next" stays music, and the service overrides it while a
  reading is active.

### On the Fourier idea
Documented in `docs/COMPRESS.md` rather than built: a Fourier transform is exactly invertible, so it
cannot shrink anything by itself; compression comes from discarding coefficients, which is lossy and
only works on smooth signals. That is what JPEG already does with a DCT. For arbitrary files the
spectrum is flat and there is nothing to discard.

### Tests
- 109 (was 103). New: `CompressorTest`.


## v3.4.0 — memory, notes, notifications, reading, details, converter

The six queued features, all local. Nothing here needs the network.

- **Object memory** (`memory/ObjectMemory.kt`): "remember this is my keys" keeps a label plus the
  lens picture; "where are my keys" answers with the label, how long ago, and the note.
- **Voice notes** (`notes/VoiceNotes.kt`): "take a note" records and transcribes on the device;
  "find my note about the plumber" searches by what was said.
- **Notification digest** (`notify/NotificationDigest.kt`): "what's new" groups recent notifications
  by app and drops the noise — media players, downloads, ongoing events.
- **Read aloud** (`voice/ReadAloud.kt`): splits screen text at sentence ends so "next paragraph",
  "faster" and "stop reading" work mid-article. While reading, "next" means the reading, not music.
- **Local vault** (`vault/Vault.kt`): "save my address as …" and "fill my address" — name, email,
  phone, address, city, pin code, UPI. Never uploaded.
- **Converter** (`convert/ConverterActivity.kt`): images → PDF, text → PDF, PDF → images, using the
  platform's own `PdfDocument` and `PdfRenderer`. Office formats are refused with a reason.

Shared ranking (`core/TextRank.kt`) scores word overlap first and recency second, so both the object
memory and the notes answer "which of the things I kept do I mean?" the same way.

### Tests
- 103 (was 82). New: `RecallTest`, `NotificationDigestTest`, `ReadAloudTest`, `VaultAndConvertTest`.
- Tests now run with `android.jar` on the classpath, which lets the ranking companions of
  Android-bound classes be exercised without a device.


## v3.3.1 — a crash you can actually read on the phone

There is no `adb` when the only computer you have is the phone, so a crash on startup used to be
invisible: the app closed and said nothing.

- `core/CrashLog.kt` records the last uncaught exception to a file in the app's directory.
- `VaaniApp.kt` installs that handler before anything else runs.
- The app shows the stored crash at the top of its log on the next launch, so a screenshot is enough
  to report it.

No logic changed, so the 82 tests are unaffected.


## v3.3.0 — pointing, describing, keeping

Four things from the suggestions list, chosen because they are cheap to build and used constantly.

- **Point at things by position** (`nlu/Ordinals.kt`): "tap the third one", "the last one", "second
  from the bottom", "tap 4". Resolved against what is actually pressable, in reading order. Strict
  about false positives — "Add 2 to cart" is a button, not the second thing.
- **Describe the screen** (`ui/ScreenDescriber.kt`): "what's on my screen" now answers in a sentence
  — the app, the heading, what you can press, how many text fields. Deliberately not a screen
  reader: one question, one answer, no focus cursor.
- **Clipboard inbox** (`core/Inbox.kt`): "save this link", "what did I copy", "show my inbox". A
  JSON file in the app's own directory; nothing leaves the phone.
- **Voice profiles** (`voice/VoiceProfiles.kt`): four characters for the spoken replies, chosen with
  a button. `docs/VOICE.md` is honest about why a *cloned* voice is not here and what it would take.

### Tests
- 82 (was 74). New: `OrdinalsTest`, `ScreenDescriberTest`.

### Queued from the same list
Notifications triage, object memory, voice notes, offline translation, read-aloud controls, local
autofill. The file converter is scoped to what is possible offline (images/text → PDF, PDF → images,
merge/split); Office → PDF needs a rendering engine Android does not have.


## v3.2.1 — uploading so the history shows

- `docs/UPLOAD.md`: two ways to publish — push the zip for the real eleven-commit history, or upload
  by hand in six stages from a browser — plus eight concrete one-file changes to build your own
  commit history afterwards.
- CI: a `check` job now gates the build on the project being complete, so a repository that is still
  being uploaded in pieces shows a green "nothing to build yet" instead of a wall of red Xs.
- README badge and footer version updated.


## v3.2.0 — ready to be public

No behaviour change; this is the release that makes the repository presentable to strangers.

- **MIT licence.** Without one, nobody may legally reuse or redistribute it, however public the
  repository is.
- **README rewritten for an audience** rather than the author: what it is, why it is not just
  another voice app, install steps for someone who has never seen it, engineering notes, and the
  limits stated plainly.
- **`docs/VERSIONS.md`** — all ten versions with what each added and why, plus how to publish one
  release per version so the evolution is visible as installable APKs.
- `docs/PHONE.md` now covers creating the repository as **public**, changing visibility, and
  pushing every tag at once.


## v3.1.1 — a phone-only path to installing it

- `docs/PHONE.md`: step-by-step from creating the repo on a phone, through Termux, to the tag that
  builds the APK, to installing and first-run permissions.
- CI: the release step now attaches APKs to a release that already exists, because creating a tag
  from the GitHub web UI (the easy way on a phone) makes the release first. Previously the step
  failed with "release already exists".
- CI: APKs are renamed to `vaani-<tag>-debug.apk` so the download is obvious in a phone's file
  list, instead of `app-debug.apk`.


## v3.1.0 — objects pick themselves, and their labels follow

No more selecting. Live mode now tracks every object in frame and gives each one its own callout.

### Added
- `lens/ObjectTracker.kt` — associates detections across frames by intersection-over-union, so an
  object keeps its identity (and its callout) as it moves. Objects are forgotten after 1.5 s, so a
  missed frame does not flicker and a departed object does not linger. New objects get new ids.
- `lens/CalloutLayout.kt` — places each label above its object, flips below when there is no room,
  and slides in at the screen edge. Recomputed every frame, so it travels with the object.
- `LiveLabelsView` now draws a box plus a two-line callout per object, and reports which object a
  tap landed on.
- Each object is identified **once** and the result sticks; at most one new object is read per
  analysis pass, so eight boxes do not mean eight inferences per frame.
- Tap any object in live mode to hear about that one.

### Tests
- 74 JUnit tests (was 60). New: `ObjectTrackerTest` (identity across movement, new ids, wobble
  tolerance, ageing, one-shot identification, innermost-object hit test, IoU) and
  `CalloutLayoutTest` (above/below, edge clamping, travelling with the box).


## v3.0.0 — the lens

A full-screen camera that names what it is looking at. Say "open lens" (or "camera mode", "what is
this", "scan this").

### Added
- `lens/LensActivity.kt` — full-screen CameraX viewfinder with immersive mode, a live detection
  overlay, a frozen-frame corner selection, a result card and spoken output.
- `lens/CornerSelectorView.kt` — four draggable corners over the frozen frame, with the selection
  cut out of a dimmed photo.
- `lens/Perspective.kt` — straightens the quad with a four-point homography, because a label
  photographed at an angle is unreadable to OCR.
- `lens/VisionEngine.kt` — on-device ML Kit image labelling plus text recognition.
- `lens/DeviceReading.kt` — combines the two: labels give the category, the printed model number
  gives the identity. Scores candidate tokens so `WH-CH720N` wins and `1000mAh` and `2024` lose.
- `lens/Quad.kt` — corner ordering, output size, and rejection of collapsed selections.
- `Command.OpenLens` with parser forms, plus the camera permission and a fullscreen theme.

### Notes
- ~30 MB of APK for the bundled ML Kit models. That is the price of working offline.
- Launching from voice needs the "display over other apps" permission (Android 10 blocks
  background Activity starts), which the floating bubble already requires.

### Tests
- 60 JUnit tests (was 50). New: `DeviceReadingTest` (model-number scoring, spec tokens rejected,
  admitting ignorance) and `QuadTest` (corner ordering, output size, degenerate selections).


## v2.5.0 — the ten ways a normal user breaks an app

Audited the list from the video against this app. Seven apply, three are server problems that do
not exist here. Full reasoning and probabilities: `docs/ROBUSTNESS.md`.

### Fixed
- **Double tapped pay.** `Executor.authorise` is now single-entry behind an `AtomicBoolean`; a
  retried or re-entrant step can no longer press the commit button twice.
- **Capital letter email.** Dictation produced `Indra@Gmail.Com` and `gmail. com`. A dot now ends
  a sentence only when whitespace follows, and tokens containing `@`, `://` or `www.` are never
  capitalised. The rule that inserted a space after punctuation no longer applies to full stops.
- **Apostrophe in a name.** "OBrien" never matched "O'Brien" — and D'Souza is a common Indian
  surname. New `nlu/NameMatch.kt` compares with punctuation and spacing removed; `ContactResolver`
  pre-filters on an apostrophe-free fragment and falls back to a bounded scan.
- **Lost signal after paying.** A commit is journaled before the tap and cleared after; if the
  phone dies in between, the next start tells the user to check their orders.
- **Hid my email.** A text field that reports no text (password fields, some custom inputs) was
  rebuilt from an empty string, wiping it. Password fields are now refused and unreadable fields
  fall back to paste-at-cursor.
- **No delete button.** Clear log, delete taught recipes and reset settings, each confirmed.

### Tests
- 50 JUnit tests (was 43). New: `NameMatchTest`, plus email/URL and decimal cases in
  `PunctuationTest`.


## v2.4.0 — first real compile, first real tests

The project had never been built. It has now: a JDK, the Kotlin compiler and `android.jar` were
installed in a sandbox and the whole app was compiled — **166 classes, zero errors** — which
turned up eight errors that reading had not.

### Fixed (compile)
- `Planner` imported `android.app.AlarmClock`; the class is `android.provider.AlarmClock`.
  Alarms and timers could never have worked.
- `AccessibilityAgent.submit()` called `AccessibilityNodeInfo.ACTION_IME_ENTER` and
  `ACTION_ARGUMENT_PRESS_ENTER_KEY`, neither of which exists. It now uses
  `AccessibilityAction.ACTION_IME_ENTER` (API 30+) with a version guard.
- `Executor.execute()` used `return` inside an expression-bodied function — illegal Kotlin.
- `UiNode` hid `Box`'s `label`, `clickable` and `editable` without `override`.
- Duplicate `import android.content.Intent` in `Executor`.
- `CodeMixLexicon` used Devanagari nukta letters as `Char` literals; they are two code points.
  Nukta sequences are now replaced before the character pass, so क़ → q, ज़ → z, फ़ → f.

### Fixed (logic, found by the new tests)
- **"start dictating" opened an app called "dictating"** — the open-app rule claimed the verb
  "start" before the dictation rules ran. Dictation rules now run first.

### Added
- 43 JUnit tests (`app/src/test/java`) over the pure logic: text, time, the code-mixing lexicon,
  punctuation, the intent parser, guidance, screen summary and the safety tiers.
- `docs/VERIFICATION.md` — what is compiled, what is tested, what is still assumed.
- CI runs `testDebugUnitTest` before assembling.
- `ui/Box.kt` — the screen heuristics take a labelled rectangle instead of an Android node, so
  they are testable on a plain JVM.


## v2.3.0

### When it can't, it points
- `exec/Guidance.kt` — locates a control in the accessibility tree and describes it in words a
  person can act on: which third of the screen, and the nearest label above or below it
  ("at the bottom of the screen, just under 'Order total ₹8,469'").
- Not-found steps now offer the closest label that means the same thing, and tap it only on a
  spoken yes.
- Blocked steps append a direction after the refusal, so you know what to press yourself.
- Missing capability opens the right screen: accessibility settings when screen control is off,
  this app's settings page when Contacts is denied.

### Unplanned but allowed: clearing the way
- `exec/Obstacle.kt` — may dismiss one overlay that sits on top of the target (cookie banner,
  upsell, permission prompt), twice at most per step, then retries.
- Allow-list only, "reject all"/"decline" preferred over "accept", and never on a screen the
  safety guard has flagged.


## v2.2.0

### Voice typing
- `voice/Punctuation.kt` — spoken punctuation (`comma`, `full stop`, `new line`, `question mark`,
  and Hinglish forms), sentence capitalisation, standalone "i" → "I", and a closing full stop per
  utterance.
- `ui/AccessibilityAgent.kt` — `appendText()` adds to the focused field instead of replacing it,
  then parks the cursor at the end; `focusFirstEditable()` grabs the field when dictation starts.
- `voice/VoiceService.kt` — dictation session: every utterance is rendered and appended, the mic
  reopens without the wake word, and the session closes on "stop typing" or 60 s of silence.
- `Command.Dictate` / `Command.StopDictation` plus parser forms ("start dictating", "type mode",
  "bol ke likho", "likhna band karo").
- Settings: "Add punctuation while dictating", on by default.


## v2.1.0

### The order gate
- `exec/ScreenSummary.kt` — reads the checkout screen and extracts product, variant, price,
  total, delivery date and payment method as short bullets.
- `ui/SummaryPopup.kt` — overlay card with **Looks good** / **Cancel**, 90-second timeout that
  counts as a refusal.
- `voice/VoiceService.kt` — speaks the same facts in the user's language and takes whichever
  answer arrives first, tap or voice.
- `exec/SafetyGuard.kt` — new `AUTHORISE` tier: `checkout`, `place order`, `buy now`,
  `proceed to pay` are gated instead of denied. Credentials (PIN, OTP, CVV, card number) and
  payment apps stay `DENY`.
- `exec/Executor.kt` — after an approved commit it performs exactly one tap, then halts the plan,
  so it cannot walk into a payment sheet.
- Settings: "Ask me before an order or payment", on by default. Off restores the old
  refuse-outright behaviour.

### Fixes
- Duplicate detail line when the product title itself contained a size ("500 ml").


## v2.0.0

### Hands-free
- `voice/AudioGate.kt` — 16 kHz energy VAD with an adaptive noise floor; the recogniser sleeps
  until someone speaks.
- `voice/WakeWordEngine.kt` — fuzzy wake phrase (any word, any script), strict enough that "rani"
  does not trigger "vaani"; follow-up window; mic hand-off so the phone never transcribes itself.
- Settings: hands-free switch, editable wake phrase, follow-up switch.

### Multilingual, code-mixed
- `nlu/CodeMixLexicon.kt` — token-level rewriter across nine languages, Roman and native script,
  including Devanagari transliteration for unknown words.
- `nlu/LanguageDetector.kt` — per-utterance language picture; nudges the recogniser hint and picks
  the reply voice.
- `voice/Reply.kt` — replies in the language you used, with Hindi and English fallbacks.
- Parser now splits on commas between commands and handles Indian word order
  ("amma ko message bhejo saying …").

### Memory
- Budget raised to 3 GB: 2 GB physical + 1 GB reclaimable.
- `exec/MemoryGovernor.kt` — headroom checks, trim handling, 60 s eviction tick.
- `llm/LlmPlanner.kt` — resident model support with `warmUp()` / `release()`; falls back to
  per-utterance load, then to rules only.

### Fixes
- Alarm vs duration precedence: "an alarm in 20 minutes" is now a duration, not 20:00.
- Message commands no longer require the word "to".
- "open amazon and search for headphones" merges into one command instead of two.
- App names spoken with an extra phrase ("obsidian daily note") split into app + query.

## v1.0.0
- Rule grammar, accessibility hands, media control, deep links, taught recipes.
- Payment and piracy guard at the point of action.
- Optional per-utterance local model.

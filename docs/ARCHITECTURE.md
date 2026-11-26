# Architecture

## The constraint that shaped everything

v1 had 2 GB and could not hold a model. v2 has a 3 GB budget — 2 GB physical plus 1 GB that is
reclaimable (mmap-ed weights and page cache) — so a model can stay warm, but only while the kernel
has room to take it back. See `docs/MEMORY.md`.

What has not changed is the ordering:

> **Deterministic first, model last.** The grammar answers in microseconds and cannot hallucinate
> a payment. The model is a fallback for phrasing, not the main path.

## Layers

```
  mic ──► AudioGate ──► WakeWordEngine ──► AsrEngine ──► IntentParser ──► Planner ──► Executor
          (energy VAD,   (fuzzy phrase,     (STT, own     (rules on       (Command    (steps,
           16 kHz)         follow-up)        process)      canonical       → Step[])   guard on
                                                           tokens)                     every tap)
                                                            │
                                    CodeMixLexicon ─────────┘
                                    (9 languages → canonical tokens)
                                    RecipeStore  (taught flows, JSON)
                                    LlmPlanner   (optional, resident or per-utterance)
```

**Gate — `voice/AudioGate.kt`.** A 16 kHz mono `AudioRecord` read in 20 ms frames, an adaptive
noise floor and a two-frame trigger. It exists so the recogniser — which costs a process and
100–200 MB — stays asleep until someone actually speaks. On trigger it releases the microphone
immediately, because Android gives the mic to one client at a time and holding it would break
recognition.

**Wake — `voice/WakeWordEngine.kt`.** Matches the wake phrase on the recogniser's transcript
rather than with a keyword-spotting model: no extra library, no extra assets, and it works in any
script ("vaani", "वाणी", "suno vaani"). Matching is deliberately strict — same first letter and
same length with edit distance ≤ 1 — so "rani ko call karo" does not wake the assistant.

It also owns the listening state machine: gate → ASR → dispatch, then hand control back to the
service, which restarts the gate only when it is idle. That single rule is what stops the phone
from transcribing its own reply into a command.

**Ear — `voice/AsrEngine.kt`.** Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` when the
device has the offline model (API 31+), otherwise `createSpeechRecognizer` with
`EXTRA_PREFER_OFFLINE`. The recogniser runs in Google's process, so its memory is not Vaani's
problem; Vaani only holds a binder. A hard 10 s cap stops a stuck listen.

**Language — `nlu/CodeMixLexicon.kt`.** A token-level rewriter, not a language selector: native
phrases first (on the raw string, so matras survive), then Devanagari transliteration, then a
~300-entry table mapping surface forms from nine languages onto canonical English tokens.
Everything downstream is language-blind. Details: `docs/LANGUAGE.md`.

**Brain — `nlu/IntentParser.kt`.** Ordered regex rules over canonical text, producing a closed
`Command` set. Roughly 40 rules cover apps, media, calls, SMS, WhatsApp, alarms, timers,
navigation, search, system toggles and screen actions. Cost: a few hundred microseconds, zero
allocations beyond the strings. It also normalises Hinglish verbs (`kholo`, `bajao`, `bhejo`,
`lagao`, `band karo`, `chalao`) before matching, and splits compound utterances on
"and then" / "then" / ";" / "and <verb>".

Why not a model here? Three reasons. A 2 GB phone cannot keep one warm. A rules engine cannot
hallucinate a `pay` action. And the interesting work — actually driving an app — is done by the
accessibility layer, not by language understanding.

**Planner — `exec/Planner.kt`.** Pure function: `Command` + installed apps + contacts → `Plan`
(an ordered `List<Step>` plus one spoken line). Deep links are preferred over screen automation
wherever the app offers one, because a deep link is one intent versus five fragile taps:

| Ask | Fast path |
|---|---|
| "play X on Spotify" | `spotify:search:X` |
| "play my gym playlist" | `spotify:playlist:<id>` from a recipe |
| "headphones on Amazon" | `amazon://search?query=headphones` |
| "navigate to Y" | `geo:0,0?q=Y` |
| "pause" | `MediaSession.transportControls` |
| "set an alarm" | `AlarmClock.ACTION_SET_ALARM` with `EXTRA_SKIP_UI` |
| "WhatsApp Neha saying …" | `https://wa.me/<number>?text=…` |

Only when no deep link exists does the plan fall back to `FocusSearch → Type → Tap`.

**Hands — `ui/AccessibilityAgent.kt`.** The generic executor. It walks `rootInActiveWindow` into a
flat `List<UiNode>` (text, class, bounds, flags), then matches a spoken label against those nodes:
exact normalised match first, then substring, then Levenshtein-scored fuzzy match with a 0.72
floor. A match is clicked through the nearest clickable ancestor; if the node refuses
`ACTION_CLICK`, a gesture tap is dispatched at its centre. Typing uses `ACTION_SET_TEXT` on the
focused editable node, and submitting uses `ACTION_IME_ENTER` — the action a search box actually
listens for, unlike a synthetic key event.

This is why Vaani works in apps it has never seen: it reads whatever is on screen.

**Memory — `exec/RecipeStore.kt`.** For flows that are too specific or too fast to discover
("open my daily note", "reorder my usual groceries" up to but not through checkout), a JSON list of
steps keyed by phrases. Recipes are data, not code, so they are safe to edit on the phone.

**Optional model — `llm/LlmPlanner.kt`.** Only reached when the grammar returns `Unknown` and the
user has switched it on. It builds a constrained prompt (allowed JSON actions + the current screen
labels), loads the model, generates ≤96 tokens, parses the JSON back into the same `Command` set,
and destroys the model. A hallucinated action cannot escape the `Command` set, and the guard still
runs at execution time.

## Executing a plan

`Executor.run(plan)` walks the steps. Each step returns `true`/`false`; the first `false` stops the
plan and speaks the reason. Before every tap and launch:

1. `SafetyGuard.forScreen(snapshot)` — is the screen already a payment flow? Stop.
2. `SafetyGuard.forText(label)` / `forApp(pkg)` — is this action itself money or piracy? Stop.
3. `CONFIRM` verdicts (uninstall, clear data, log out) are asked out loud: Vaani pauses, listens
   once, and only proceeds on "yes"/"confirm"/"haan".

Failure messages are specific and spoken, because a voice assistant that silently does nothing is
worse than one that says "I couldn't find Send on the screen."

## Failure modes and what happens

| Situation | Behaviour |
|---|---|
| Accessibility service off | Steps needing the screen speak "screen control is switched off"; intents still work |
| No MediaSession | "I couldn't reach the music player" |
| Label not found in 6 s | "I couldn't find X on the screen" |
| App not installed | "I couldn't find an app called X" |
| Contact ambiguous | Only proceeds on a single match or a clear best match |
| Deep link rejected | Falls back to a plain launch |
| Payment screen reached | Stops, says why, does not retry |
| Two commands at once | Second says "I'm still working on the last one" |

## Listening states

```
IDLE ──gate trigger──► LISTENING ──final──► (wake?) ──► EXECUTING ──► IDLE
                          │                    │
                          │                    └─no wake, no follow-up window──► ignore
                          └─error──► IDLE (gate restarts)
EXECUTING ──done──► follow-up window (6 s) ──► LISTENING (no wake word needed)
```

The microphone is closed for the whole of `EXECUTING`, including while the reply plays. Barge-in
stops the voice but does not open the mic mid-plan; the gate comes back afterwards.

## Threading

Everything stateful (recogniser, service) is on the main thread. The pipeline runs in a
`CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`, and steps that block (waiting for a
label, pausing) suspend instead of sleeping. LLM inference is the only thing on `Dispatchers.IO`.

## Extending it

- New command type: add to `Command`, one branch in `IntentParser`, one in `Planner`. The
  `when` blocks are exhaustive, so the compiler lists every place that needs updating.
- New app with a deep link: add one `Known(...)` line in `AppRegistry`.
- New flow: a recipe, not code.

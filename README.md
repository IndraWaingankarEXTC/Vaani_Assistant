# Vaani — a hands-free, fully offline assistant for Android

![version](https://img.shields.io/badge/version-3.3.0-7CE3C1?style=flat-square)
![licence](https://img.shields.io/badge/licence-MIT-blue?style=flat-square)
![platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square)
![tests](https://img.shields.io/badge/tests-74%20passing-brightgreen?style=flat-square)
![network](https://img.shields.io/badge/network-not%20used-lightgrey?style=flat-square)

Speak — in English, Hindi, Tamil, or all three inside one sentence — and it drives the phone. It
opens apps, plays music, calls and texts, sets alarms and timers, navigates, and taps, types and
scrolls inside other apps. Point the camera at a device and it tells you what it is.

**Everything runs on the phone.** No account, no server, no API key, no telemetry. It works on a
plane, in a basement, with the SIM out.

**Built for a 2 GB phone.** The default brain is a rule grammar that answers in microseconds; the
optional language model is loaded only when the rules miss.

---

## Why this is not just another voice app

Four decisions carry it, and each one is a problem worth naming.

**1. Rules first, model last.** On a 2 GB phone the OS already holds ~1 GB. A chat model in the loop
for every command is not viable, so the grammar handles the everyday surface and a 360M–1.5B model
is a fallback for phrasing it missed. The model is never *required*, which is why the app works on
the worst phone you can hand it.

**2. Code-mixing, not language switching.** Nobody picks a language per sentence. `"spotify kholo
yaar, lofi bajao"` is rewritten token by token into canonical English — Hindi, Marathi, Bengali,
Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi, in Roman *and* native script — so the grammar
downstream is language-blind. Details in [`docs/LANGUAGE.md`](docs/LANGUAGE.md).

**3. The accessibility tree is the hands; the guard is at the point of action.** Taps, typing and
scrolling are generic, so it works in apps it has never seen. And money is checked *where the tap
happens*, not where the sentence is parsed — a wrong recipe or a hallucinated step still stops at
"Proceed to pay". See [`docs/SAFETY.md`](docs/SAFETY.md).

**4. The camera reads the label, not just the shape.** A generic classifier can say "headphones"; it
cannot say *which* headphones. Every device prints its own answer, so the lens combines image labels
(category) with OCR of the model number (identity) — `WH-CH720N` wins, `1000mAh` and `2024` lose.
See [`docs/LENS.md`](docs/LENS.md).

---

## What it does

| | |
|---|---|
| **Voice** | "Vaani, …" hands-free, or tap the floating mic. Follow-up window after each command. |
| **Languages** | Nine Indian languages, mixed inside one sentence, Roman or native script. Replies in the language you used. |
| **Apps** | Opens anything installed; deep links straight into Spotify search, Amazon, WhatsApp, Maps. |
| **Music** | Play/pause/next through MediaSession — no screen tapping. |
| **Calls & messages** | Calls, SMS, WhatsApp, from a spoken name. |
| **Clock** | "alarm lagao 7 baje", "timer for 25 minutes". |
| **Screen** | Taps, types, scrolls and reads any app by what is on screen. |
| **Typing** | Sentence dictation with spoken punctuation, appended to whatever field is focused. |
| **Shopping** | Walks to the payment step, stops, and shows a five-line summary before you decide. |
| **Camera** | Full-screen lens: every object gets a callout that follows it; four corners to identify one properly. |
| **Position** | "tap the third one", "the last one", "second from the bottom" — no need to read labels aloud. |
| **Describe** | "what's on my screen" answered in a sentence, not a focus cursor. |
| **Clipboard** | "save this link", "what did I copy", "show my inbox" — a local inbox that never leaves the phone. |
| **Voice** | Four voice profiles, chosen in the app. |

Full grammar: [`docs/COMMANDS.md`](docs/COMMANDS.md). Every version and what it changed:
[`docs/VERSIONS.md`](docs/VERSIONS.md).

---

## Install

You need an Android phone (8.0+). No computer required — [`docs/PHONE.md`](docs/PHONE.md) has the
whole journey from creating the repository to a running app, on the phone alone.

**If a release already exists** (someone built it):

1. Open **Releases** → download `vaani-<version>-debug.apk`.
2. Chrome will warn; tap **Download anyway**.
3. Tap the file → allow **install unknown apps** for your browser → **Install**.
4. Play Protect may say "unsafe app blocked" → **More details** → **Install anyway**.

**Then, in the app, in this order:**

1. Grant microphone, contacts, phone, SMS.
2. **Screen control** → Accessibility → *Vaani screen control* → on. *(This is what lets it act
   inside other apps.)*
3. **Media control access** → Notification access → *Vaani media access* → on.
4. **Floating mic** → allow Display over other apps.
5. Turn on **Keep Vaani running** and **Hands-free**.

Try: `open spotify`, `Vaani, amma ko call karo`, `Vaani, open lens`.

**Build it yourself:** open in Android Studio, or `gradle assembleDebug`. Building from source gives
you your own signing key, so your updates install over each other.

---

## How it works

```
mic ─► AudioGate ─► WakeWord ─► AsrEngine ─► IntentParser ─► Planner ─► Executor ─► phone
       energy VAD   fuzzy       on-device    rules on         Command     steps, guard
      16 kHz        phrase      recogniser   canonical        → Step[]    on every tap
                                              tokens
                          CodeMixLexicon ───────┘
                          (9 languages → canonical)
```

Two surfaces: the **voice path** above, and the **lens** (`lens/`), which is CameraX plus ML Kit
object detection, image labelling and text recognition, all bundled and offline.

Architecture, memory budget and failure modes: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
[`docs/MEMORY.md`](docs/MEMORY.md).

---

## Engineering notes

If you are reading this as a work sample, these are the parts worth your time:

- **A 3 GB budget on a 2 GB phone.** 1 GB is spent on memory the kernel can always reclaim
  (mmap-ed weights, page cache), with a governor that drops the model on `onTrimMemory` and on a
  60-second headroom check. [`docs/MEMORY.md`](docs/MEMORY.md)
- **Token-level code-mixing** across nine languages and two scripts, including Devanagari
  transliteration — and the trap that normalising before transliterating eats the matras.
  [`docs/LANGUAGE.md`](docs/LANGUAGE.md)
- **A four-tier safety model** enforced at the point of action, with a single-entry commit lock, a
  journal written before the tap so an interrupted payment is reported on next launch, and a
  summary card that treats silence as "no". [`docs/SAFETY.md`](docs/SAFETY.md)
- **74 unit tests** over the pure logic, run in CI before the APK is built, plus an audit of ten
  classic "how a normal user breaks your app" failures with the fixes.
  [`docs/ROBUSTNESS.md`](docs/ROBUSTNESS.md) · [`docs/VERIFICATION.md`](docs/VERIFICATION.md)
- **Honest verification.** Every claim about what is tested and what is still assumed is written
  down, including the bugs that only appeared once the code was actually compiled.

---

## Limits

Said plainly, because the alternative is a README that oversells:

- **It has never run on a device I own.** The code compiles (216 classes) and the logic is tested;
  the camera, the wake word and the accessibility automation are unverified against real apps.
- **No wake word without a speech service.** It uses the phone's on-device recogniser rather than a
  keyword spotter — no extra model, but it depends on the device's speech stack.
- **Apps that expose no accessibility tree** (Flutter, games, canvas UIs) cannot be driven; it says
  so instead of tapping blind.
- **Payment apps and PIN/OTP fields are refused outright**, by design.
- The lens adds ~30 MB of APK for the bundled models.

---

## Docs

[PHONE](docs/PHONE.md) · [UPLOAD](docs/UPLOAD.md) · [DEPLOY](docs/DEPLOY.md) · [VERSIONS](docs/VERSIONS.md) ·
[ARCHITECTURE](docs/ARCHITECTURE.md) · [COMMANDS](docs/COMMANDS.md) · [LANGUAGE](docs/LANGUAGE.md) ·
[MEMORY](docs/MEMORY.md) · [LENS](docs/LENS.md) · [VOICE](docs/VOICE.md) · [SAFETY](docs/SAFETY.md) ·
[ROBUSTNESS](docs/ROBUSTNESS.md) · [RECIPES](docs/RECIPES.md) · [LLM](docs/LLM.md) ·
[VERIFICATION](docs/VERIFICATION.md)

---

## Licence

MIT — see [LICENSE](LICENSE). Use it, change it, ship it, sell it; keep the copyright notice.

Built by [Indra Waingankar](https://github.com/IndraWaingankarEXTC). Issues and pull requests are
welcome; if it is useful to you, a star helps other people find it.

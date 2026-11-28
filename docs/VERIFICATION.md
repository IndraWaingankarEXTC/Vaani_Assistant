# What has actually been checked

Two kinds of claim live in this repo, and they are worth keeping apart.

## 1. Compiles

The whole app was compiled with the real Kotlin 1.9.24 compiler against
`android.jar` (API 34) and the AndroidX artifacts it depends on. **166 classes, zero errors.**

That first compile found eight errors that no amount of reading had caught:

| Error | Where | Why it mattered |
|---|---|---|
| `import android.app.AlarmClock` | `Planner` | Wrong package — the class is `android.provider.AlarmClock`. Alarms would never have built. |
| `AccessibilityNodeInfo.ACTION_IME_ENTER` | `AccessibilityAgent.submit()` | No such constant. The IME enter action is `AccessibilityAction.ACTION_IME_ENTER` (API 30+). Searching by typing was broken. |
| `ACTION_ARGUMENT_PRESS_ENTER_KEY` | same | Also does not exist. |
| `return` in an expression-bodied function | `Executor.execute()` | Illegal Kotlin; the whole step runner failed to compile. |
| `UiNode` hiding `Box` members | `UiNode` | `label`, `clickable`, `editable` needed `override`. |
| Duplicate `import Intent` | `Executor` | Ambiguous reference. |
| `'क़'` as a `Char` literal | `CodeMixLexicon` | Devanagari nukta letters are two code points; a `Char` cannot hold them. |

Two of these — the `AlarmClock` package and the IME constant — would have failed the very
first CI run. The nukta one silently disabled part of the transliterator.

## 2. Behave correctly

74 JUnit tests, all passing, covering the logic that has no Android in it:

| Suite | Covers |
|---|---|
| `TextTest` | normalisation, fillers, edit distance, fuzzy matching |
| `TimeParserTest` | clock forms (`6:30 am`, `half past six`, `quarter to 8`, `7 baje`) and durations |
| `CodeMixLexiconTest` | Roman and native-script rewriting across Hindi, Tamil, Bengali, Telugu |
| `PunctuationTest` | spoken punctuation, capitalisation, new lines, standalone "i" |
| `IntentParserTest` | 13 utterance families including Indian word order and compound commands |
| `GuidanceTest` | "it's at the bottom of the screen, just under …" |
| `ScreenSummaryTest` | reading a checkout into five bullets |
| `SafetyGuardTest` | the four tiers: allow, confirm, authorise, deny |

Writing them found one real logic bug: **"start dictating" opened an app called "dictating"**,
because the "open an app" rule claimed the verb "start" before the dictation rules ran.

Run them with:

```bash
gradle testDebugUnitTest        # or ./gradlew test
```

They run in CI on every push, before the APK is assembled.

### The lens (v3.0)

The whole app, including the lens, compiles: **216 classes**. One caveat, stated plainly: the ML
Kit *text recognition* API classes could not be resolved to a jar by hand-assembling a classpath
here — the artifacts are split across a transitive tree that only Gradle resolves. Those three
symbols were stubbed locally for the type-check and the stub is **not** in the repository. So the
lens code is verified against the documented ML Kit API shape, not against the actual jar.

## What is still not checked

- **The lens has never seen a camera.** CameraX binding, focus, the warp and ML Kit inference are
  all unexercised. This is the largest unverified surface in the project.
- **No device run.** Nothing here has touched a real phone. Accessibility behaviour against real
  apps, the wake-word gate's sensitivity, the overlay card, and whether WhatsApp honours
  `ACTION_SET_TEXT` are all still assumptions.
- **Not a Gradle build.** The compile above used `kotlinc` directly, with an `R` class stubbed from
  the real resource names. Gradle's own wiring (AGP, manifest merging, resource linking, R8) is
  exercised only in CI.
- **The LLM path is unexercised.** `TinyLlmBridge` needs a native library that is not shipped.

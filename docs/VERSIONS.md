# How it evolved

Ten tagged versions, each one a working thing rather than a checkpoint. The history is in the
repository: `git log --oneline --decorate` shows every step, and `git checkout v2.2.0` gets you any
of them.

| Tag | What it added | Why it mattered |
|---|---|---|
| **v1.0.0** | Rule grammar, accessibility hands, media sessions, deep links, taught recipes, payment/piracy guard | Proved the core idea: a phone can be driven by rules plus the accessibility tree, with no model in the loop |
| **v2.0.0** | Hands-free wake word, nine-language code-mixing, 3 GB memory budget | The two things that made it usable daily: you stop tapping, and you stop translating yourself |
| **v2.1.0** | Order gate — summary card before anything commits money | Turned a wall into a gate: it will shop for you up to the payment step, then stop and ask |
| **v2.2.0** | Voice typing — sentence dictation with spoken punctuation | Typing became a first-class thing to automate, not a side effect of "type X" |
| **v2.3.0** | Guidance — "it's at the bottom, under 'Order total'" — and obstacle clearing | Every dead end became a direction instead of an apology |
| **v2.4.0** | First real compile, first real tests | Found eight bugs reading had missed, including a wrong `AlarmClock` package that meant alarms never worked |
| **v2.5.0** | Robustness audit: double-tap, apostrophes, masked fields, interrupted commits | Fixed the failure modes real people actually hit |
| **v3.0.0** | The lens — full-screen camera, four corners, on-device identification | It stopped being only a voice assistant |
| **v3.1.0** | Tracking lens — objects keep their identity, callouts follow them | Removed the need to select anything at all |
| **v3.1.1** | Phone-only install path, CI fixes | Anyone can go from zero to a running app with just a phone |
| **v3.2.0** | Licence, README for strangers, this page | Open for other people to use and change |

## Every version is installable

From **v2.0.0** onward each tag carries a GitHub Actions workflow, so pushing the tags builds an
APK per version and attaches it to that version's release:

```bash
git push --tags
```

Then `releases` shows one entry per version, each with a downloadable APK — a visible record of the
evolution rather than a changelog nobody reads.

v1.0.0 predates the workflow, so it has source but no prebuilt APK.

## The shape of the evolution

Three phases, roughly:

1. **v1 — can it work at all?** Rules, accessibility, guard. The answer was yes, on 2 GB.
2. **v2 — can it be used without thinking about it?** Wake word, mixed languages, and the safety
   work that makes handing it a shopping task acceptable.
3. **v3 — can it do things a voice alone cannot?** The camera, and then making the camera not need
   instructing.

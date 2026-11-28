# Which voice answers you

Four profiles, chosen with the **Voice** button in the app. Each is the phone's own speech engine
tuned by pitch and speed — the engine already ships several voices per language.

| Profile | Character |
|---|---|
| Default | Neutral, as the phone sounds |
| Voice 1 · warm | Slightly lower and slower |
| Voice 2 · bright | Higher and quicker, British English |
| Voice 3 · calm | Soft and measured, American English |

Your chosen profile applies to every reply, including the mixed-language ones — the *language* comes
from what you just said, the *character* comes from the profile.

## On using your own voice

A clone of your voice needs a neural text-to-speech model — a few tens of megabytes, trained on
your recordings, running on the device. That is a real thing, and it is not something that can be
produced here: the model has to come from a service that trains it (ElevenLabs, PlayHT, Coqui and
similar), and the weights would then be shipped inside the APK.

What would be needed to add it later:

1. A small neural TTS runtime on the device — ONNX Runtime Mobile or TFLite, roughly 10–15 MB.
2. A voice model file per voice, typically 30–80 MB.
3. Twenty-odd recorded sentences to train a personal voice.

On a 2 GB phone, a resident TTS model of that size competes directly with the memory budget in
`docs/MEMORY.md`, so it would have to load on demand and unload after speaking — which adds a delay
to every reply. That is the honest trade, and it is why the profiles here are the phone's voices
rather than a copy of yours.

## What is already in place for it

`voice/VoiceProfiles.kt` is the seam. Adding a cloned voice means adding a profile whose
implementation calls a neural engine instead of `TextToSpeech`; nothing else in the app changes,
because every reply already goes through `Tts.speak`.

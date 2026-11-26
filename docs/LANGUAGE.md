# Code-mixing, not language switching

## The problem with a language picker

Nobody says "now I will speak Hindi". Real speech inside one sentence:

```
"Vaani, spotify kholo yaar, lofi bajao"
"Vaani, Obsidian में daily note खोलो"
"Vaani, swiggy la biryani order pannu"
"Vaani, amma ko message bhejo saying aa rahi hoon"
```

A per-language model or a language selector fails on every one of those. So Vaani never asks
which language you are speaking. It rewrites **each token** into a canonical English token that
the grammar already understands, and the grammar never knows a language was involved.

## The pipeline

```
raw utterance
   │  native-script phrases matched on the raw string (matras intact)
   ▼
transliterate Devanagari → Latin          (for words not in the phrase table)
   │
   ▼
normalise (lowercase, strip punctuation, fold diacritics)
   │
   ▼
token table: kholo→open, bajao→play, bhejo→send, lagao→set, band→off …
   │
   ▼
canonical: "spotify open lofi play"  →  word-order fix  →  "open spotify, play lofi"
```

Two details matter more than they look:

**Transliterate before normalising.** `Text.norm()` strips Unicode combining marks, and in
Devanagari those marks *are* the vowels. Normalise first and "खोलो" becomes "खल".

**Phrases before tokens.** "बंद करो" is two words that mean one verb. Multi-word native-script
phrases are matched against the untouched string, which is the only place the nukta and matra
sequences survive.

## Coverage

| | Roman script | Native script |
|---|---|---|
| Hindi / Urdu | ✅ | ✅ Devanagari (+ transliteration for unknown words) |
| Marathi | ✅ | ✅ via Devanagari |
| Bengali | ✅ | ✅ |
| Tamil | ✅ | ✅ |
| Telugu | ✅ | ✅ |
| Kannada | ✅ | ✅ |
| Malayalam | ✅ | ✅ |
| Gujarati | ✅ | ✅ |
| Punjabi | ✅ | ✅ |

Native-script coverage is the verb and particle vocabulary (open, play, send, call, search, pause,
on/off, volume, time words, politeness) — enough to run a command end to end. An app name in a
non-Devanagari script will not be transliterated; say it in Roman, which is also what the
recogniser returns for en-IN.

## Why the recogniser hint stays en-IN

Google's en-IN model transcribes Indian-language words in **Latin script** — "spotify kholo",
not "स्पॉटिफाई खोलो". That is exactly what a token table wants. So en-IN is the default.

If you speak mostly Tamil or Bengali, `LanguageDetector` notices and nudges the hint to `ta-IN` /
`bn-IN`, and the native-script tables take over. Turn it off with the **Auto language hint**
switch.

## Replies

`voice/Reply.kt` answers in the language you used: "Ho gaya" after Hindi, "Aachu" after Tamil,
"Done" after English, and Hinglish when the utterance was mixed. Templates fall back
language → Hindi → English, so nothing ever comes out empty.

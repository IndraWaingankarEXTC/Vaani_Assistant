# Recipes — teaching Vaani a flow

A recipe is a phrase and a list of steps. Recipes are checked **before** the grammar, so a phrase
you claim always runs your steps.

Where the file lives: `recipes.json` in the app's files directory, i.e.
`/data/data/com.vaani.assistant/files/recipes.json`. Push one with:

```bash
adb push recipes.json /data/local/tmp/recipes.json
adb shell run-as com.vaani.assistant cp /data/local/tmp/recipes.json files/recipes.json
```

A starter file ships in `app/src/main/assets/recipes.sample.json`. Copy it to `files/recipes.json`
and edit. Vaani reloads it automatically when the file's timestamp changes — no restart.

## Format

```json
{
  "recipes": [
    {
      "name": "Spotify gym playlist",
      "phrases": ["play my gym playlist", "gym music"],
      "steps": [
        { "do": "launch", "pkg": "com.spotify.music", "uri": "spotify:playlist:37i9dQZF1DX76Wlfdnj7AP" },
        { "do": "pause", "ms": 1200 },
        { "do": "tap", "label": "Shuffle play", "timeout": 8000 }
      ]
    }
  ]
}
```

## Steps

| `do` | Fields | Meaning |
|---|---|---|
| `launch` | `pkg`, optional `uri` | Open an app, or its deep link |
| `uri` | `uri`, optional `pkg` | Open a URL |
| `tap` | `label`, `timeout` (ms, default 6000) | Wait for a label, then tap it |
| `wait` | `label`, `timeout` | Wait for a label to appear, do not tap |
| `pause` | `ms` | Sleep, for slow apps |
| `type` | `text`, `submit` | Type into the focused field |
| `search` | — | Focus the search box / first text field |
| `scroll` | `forward` (default true) | Scroll |
| `back` / `home` | — | Global actions |
| `say` | `text` | Speak a line |
| `media` | `action`: play/pause/next/prev/stop/toggle | MediaSession transport |

## How to write one without guessing

1. Do the flow by hand on the phone while Vaani's log is open (the Log panel in the app, or
   `adb logcat -s Vaani`).
2. Note the exact on-screen labels you tapped — "Shuffle play", "Add to cart", "Daily notes".
   Labels are matched fuzzily (case, punctuation and small typos are fine), so copy them roughly.
3. Write the steps. Start with `launch`, add a `pause` of 1000–1500 ms, then `tap` each label in
   order.
4. If a step needs to wait for a network list, raise that step's `timeout`.

## Safety in recipes

A recipe cannot smuggle in a payment. Every `tap` still passes through `SafetyGuard.forText`, and
the screen is re-checked before each step. A recipe that ends on "Place order" will stop there and
say so. Do not try to work around it — that guard is the point.

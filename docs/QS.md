# Vaani in the notification shade

Two tiles, added the same way you add any tile.

## Adding them

1. Pull the shade down fully (the second pull, showing all tiles)
2. Tap the **pencil / edit** icon
3. Find **Vaani** and **Vaani Lens** in the list below
4. Drag both up into the grid
5. **Save**

On a Redmi/HyperOS that is: pull down twice → **Edit** → drag.

## What they do

**Vaani** — tap to switch listening on or off. When it is on, the tile is highlighted and says
*Listening*; the wake word works from any app, and everything after that is voice. Tap again to stop
and free the microphone.

**Vaani Lens** — one tap straight into the camera lens. Works from the lock screen too, because the
tile unlocks the phone first.

## About long-press

Long-pressing a tile opens the app's **App info** page. That is Android's behaviour for every tile
that is not a system app — Bluetooth gets its own pairing sheet only because Bluetooth *is* the
system. There is no callback for a long press on a third-party tile, so it cannot be customised.

The equivalent here is: tap **Vaani** to toggle, and open the app itself for the full settings.

## What a tile cannot do

**Mobile data cannot be switched by an app.** Turning data on or off needs
`android.permission.MODIFY_PHONE_STATE`, which is signature-level — granted to system apps only.
This is why no assistant can do it. What Vaani does instead is open the internet panel, where the
switch is one tap away:

```
Vaani, turn on data        → opens the internet panel
Vaani, turn off wifi       → opens the same panel
```

The same limit applies to airplane mode and to Bluetooth *pairing*. Flashlight and Do Not Disturb
do work directly, because both have public APIs.

# The lens

A full-screen camera that tells you what it is looking at.

```
Vaani, open lens
Vaani, camera mode
Vaani, what is this
Vaani, scan this
```

It opens full screen and nothing else — no title bar, no system bars.

## Two ways to use it

**Live.** Point it at a desk and every object it finds gets its own box and its own callout,
continuously. Nothing needs selecting: objects are picked up as they appear, each one is identified
individually, and the callout sits next to the object and **travels with it** as you or it moves.
New objects coming into frame get new callouts while the others keep theirs. Tap any object to hear
about that one specifically.

Each object is read **once**, not every frame — a result sticks to the object for as long as it is
tracked. That is what makes it usable: a recognition pass costs a few hundred milliseconds, so
reading all eight boxes on every frame would melt the phone. One new object per pass, at most.

**With four corners.** Press **Select area**, drag the four handles onto the corners of the device
— or, much better, onto its **label** — and press **Read this**. The selection is straightened
(the photo is taken at an angle; a warped label is unreadable to OCR) and then read properly.

## Why the corner box is the accurate path

Two models run on the device:

- **Image labelling** says what *kind* of thing it is: headphones, laptop, camera, watch.
- **Text recognition** reads what is *printed on it*: "SONY", "WH-CH720N", "1000mAh".

Neither is enough alone. Labelling cannot tell a WH-CH720N from a WH-1000XM4; text recognition
cannot tell a headphone box from a router box. Combined, they usually can:

> **Sony WH-CH720N** — Headphones · Confidence 88% (from the label)

`DeviceReading` scores every token in the text for "does this look like a model number", because
device labels are messy: `WH-CH720N` and `SM-A546E` are obvious, `Airdopes 141` is plausible, and
`1000mAh`, `5V` and `2024` are not. Units, years and version numbers are rejected outright.

## How objects keep their identity

ML Kit's detector hands back a fresh, unordered list every frame with no notion of "the same thing
as last time". `ObjectTracker` does that itself: each new detection is matched to the existing
object it overlaps most (intersection over union), and anything unmatched becomes a new object with
a new id. Objects that disappear are forgotten after 1.5 seconds, which is long enough that one
missed frame does not make a callout flicker and short enough that a callout does not linger on an
empty table.

**The honest weakness:** with two similar objects crossing paths, or one moving very fast between
frames, the association can swap two callouts for a moment. There is no motion model here, only
overlap — which is why the read-once rule matters, since a wrong swap is corrected the next time
the object is re-identified.

## What it will not do

- **It will not pretend.** If nothing legible is inside the box it says *"I can't identify it from
  this angle"* rather than inventing a name. Confidence below 45% is spoken as *"I'm guessing,
  honestly"*.
- **It will not guess a brand it did not read.** The brand comes from a known-name list matched
  against the OCR text, not from the shape.
- **It sends nothing anywhere.** Both models ship inside the APK and run on-device. No frame is
  written to disk, and the lens works with no signal.

## Requirements and cost

- **Camera permission** (asked on first open).
- **"Display over other apps"** for voice launch: since Android 10 an app cannot start an Activity
  from the background without it. The same permission the floating bubble already needs.
- **About 30 MB of APK**: the bundled ML Kit models for labelling, text and object detection. They
  are what make it work offline; the alternative is a server call per frame.

## Limits worth knowing

- Labels are a generic 400-class model: excellent at "laptop", useless at "the 2021 model with the
  better hinge".
- Small or curved labels — a model number printed round a barrel jack — defeat OCR at phone
  distance. Get closer, or box just the label.
- Text on a screen (a phone showing its own settings page) reads well; text embossed in dark
  plastic reads poorly.
- Live labelling runs at roughly 2–3 frames per second by design, to keep the phone cool.

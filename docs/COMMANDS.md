# Command grammar

Vaani normalises your words first: lowercase, punctuation stripped, filler removed ("hey vaani",
"please", "can you"), Hinglish verbs translated. So "Hey Vaani, could you please open Spotify"
and "spotify kholo" both land on `OpenApp("spotify")`.

## Speaking to it

Two ways in, both hands-free:

```
"Vaani, spotify kholo"          ← wake word, then the command in one breath
"Vaani"                          ← alone: it answers "Haan, bolo?" and listens
"…and next song"                 ← follow-up within 6 s, no wake word needed
```

The wake phrase is configurable in the app (any word, any script). Everything below can be said
with or without the wake word, in any mix of languages.

## Apps

| Say | Does |
|---|---|
| open spotify / launch obsidian / start amazon | launches the app |
| spotify kholo / obsidian khol | same, Hinglish |
| open amazon and search for headphones | launch, then search inside |
| play lofi beats on spotify | deep link to Spotify search |
| play despacito | opens your music app and searches |
| search for best phones on amazon | search inside an app |
| search for today's weather | web search |
| find biryani in swiggy | search inside an app |

A bare app name works too: "obsidian".

## Music and volume

pause · stop · resume · continue · next · skip · previous · prev · play music · pause the song
volume up · louder · increase volume · volume down · quieter · lower the volume
awaaz badhao · awaaz kam karo

These go through MediaSession, so they work with whatever is playing — Spotify, YouTube Music, a
local player — without tapping anything.

## Calls and messages

call amma · dial ravi · phone the office · amma ko call karo
send a message to ravi saying I'll be late
text priya that says running 10 minutes behind
send an sms to 9876543210 saying hello
whatsapp neha saying reached home
send a whatsapp message to arjun: call me back
message kiran on whatsapp saying thanks

If `SEND_SMS` is granted, texts are sent directly. Otherwise the composer opens pre-filled and
Vaani taps Send. WhatsApp uses `wa.me` deep links with the text pre-filled.

## Clock

set an alarm for 6:30 am · alarm at 7 · wake me up at half past five
set a timer for 25 minutes · timer 90 seconds · countdown for 10 minutes
alarm lagao 7 baje

Time forms understood: `7`, `7:30`, `07.30`, `7 am`, `19:45`, `seven thirty`, `half past six`,
`quarter to eight`, and durations as `N hours/minutes/seconds`, `ten minutes`, `half an hour`.

## Navigation and search

navigate to Phoenix Marketcity · directions to Bandra station · take me to the airport
search for the nearest chemist

## System toggles

turn on the flashlight · turn off the torch · flashlight on
turn on dnd · turn off do not disturb
turn on wifi · turn off bluetooth · turn on airplane mode

Flashlight and Do Not Disturb really toggle. Wi-Fi, Bluetooth and airplane mode open the matching
settings panel — Android has not allowed third-party apps to flip those silently since Android 10.

## Screen actions (need screen control)

tap Continue · click Sign in · press the search icon
type biryani · enter my email address
scroll down · scroll up · swipe left
go back · go home · recents
read the screen

These act on whatever is in front of you, in any app.

## Compound

Join with "and then", "then", ";" or "and <verb>":

```
open spotify and play my gym playlist
set an alarm for 6:30 and set a timer for 20 minutes
```

## Meta

repeat · do it again · say again — replays the previous command.

## Recipes (your own phrases)

Anything in `recipes.json` wins over the grammar, so you can claim a phrase for your own flow:

```
play my gym playlist
open my daily note
find my saved headphones
```
See `docs/RECIPES.md`.


## Mixed language

One sentence, several languages, no switching — this is the normal case, not an edge case.

| Say | Canonical form | Runs |
|---|---|---|
| spotify kholo yaar, lofi bajao | open spotify, play lofi | opens Spotify, plays lofi |
| Obsidian में daily note खोलो | open obsidian daily note | opens Obsidian on the note |
| आवाज़ बढ़ाओ | volume up | louder |
| फोन करो अम्मा को | call amma | calls Amma |
| amma ko message bhejo saying aa rahi hoon | send amma … aa rahi hoon | SMS to Amma |
| torch चालू करो | torch turn on | flashlight on |
| youtube पे lofi चलाओ | play youtube lofi | YouTube, searching lofi |
| क्या है स्क्रीन पर | what screen | reads the screen |
| spotify திற | open spotify | opens Spotify |
| খোলো whatsapp | open whatsapp | opens WhatsApp |
| తెరువు spotify | open spotify | opens Spotify |
| swiggy la biryani order pannu | open swiggy … | Swiggy search |

Languages: Hindi, Urdu, Marathi, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi —
Roman script everywhere, native script for the command vocabulary. See `docs/LANGUAGE.md`.

Replies follow the same language: "Ho gaya", "Aachu", "Ayyipoyindi", "Done".


## Ordering (v2.1)

Vaani will shop up to the payment step, then stop and show you what it found.

```
Vaani, amazon pe Sony WH-CH720N dhoondo
Vaani, add it to the cart
Vaani, place the order
```

At "place the order" it does not tap. It shows a card:

- Product and variant
- Price and total
- Delivery date
- Payment method it can see

Then it asks, out loud, in your language. Say **"haan theek hai"**, **"looks good"**, or tap the
button — or say nothing and it cancels after 90 seconds.

After your yes it taps **once**, then stops and tells you to finish the payment yourself. It never
enters a PIN, an OTP or a card number, and never presses twice.

Turn the whole gate off with **"Ask me before an order or payment"** in the app; that reverts to
refusing order steps outright. Details: `docs/SAFETY.md`.


## Voice typing (v2.2)

Dictation, sentence by sentence. Open any text field — WhatsApp, Obsidian, Gmail, a note — then:

```
Vaani, start dictating
Vaani, bol ke likho
Vaani, type mode
```

From there every sentence you speak is **appended** to the field and the microphone reopens on
its own. No wake word between sentences, no word-by-word stepping. Say **"stop typing"** (or
*"bas karo"*, *"likhna band karo"*) to finish; it also closes after 60 seconds of silence.

What you say goes in as written text:

| You say | You get |
|---|---|
| meeting moved to friday can you send the deck | Meeting moved to friday can you send the deck. |
| hello comma how are you question mark | Hello, how are you? |
| first point full stop second point full stop done | First point. Second point. Done. |
| line one full stop new line line two | Line one.<br>Line two. |
| dear sir comma i am applying full stop new paragraph regards comma ravi | Dear sir, I am applying.<br><br>Regards, ravi. |

Spoken punctuation: `comma`, `full stop` / `period`, `question mark`, `exclamation mark`,
`semicolon`, `colon`, `new line`, `new paragraph`, `open bracket` / `close bracket`,
`open quote` / `close quote`, `hyphen`, `dash`, `underscore`, `percent`, `at the rate`.
Hinglish works too: `alpa viram` (comma), `purna viram` (full stop), `prashn chinha` (question
mark), `nayi line` (new line).

Each sentence is capitalised and given a closing full stop, so a dictated paragraph reads like a
paragraph rather than a transcript. Turn that off with **"Add punctuation while dictating"** in
the app if you want the raw words.

For a single phrase there is still the one-shot form: *"Vaani, type biryani"*.


## When it can't, it points

You do not have to guess why something did not happen.

```
You:  Vaani, place the order
Vaani: That's a payment screen, and I don't move money.
       It's at the bottom of the screen, just under 'Order total ₹8,469'.

You:  Vaani, tap Buy now
Vaani: I can't see Buy now. I can see Place your order — shall I tap that?
```

It also clears one thing that is in the way — a cookie banner or a "not now" popup — and retries
before giving up. See `docs/SAFETY.md` for exactly what it is allowed to dismiss.


## The lens (v3.0)

```
Vaani, open lens
Vaani, camera mode
Vaani, what is this
Vaani, scan this
```

Opens a full-screen camera. Two modes:

- **Live** — boxes and labels objects as you move the phone.
- **Select area** — drag four corners around the device (or its label), then **Read this**. The
  selection is straightened and the device is identified from its printed model number plus image
  labels, and spoken.

Say "Close" or press the button to leave. Details and accuracy limits: `docs/LENS.md`.


## Pointing at things by position (v3.3)

When a screen has a list or a row of buttons, you do not have to read every label aloud:

```
Vaani, tap the third one
Vaani, open the second one
Vaani, the last one
Vaani, second from the bottom
Vaani, tap 4
```

Counting is in reading order — top to bottom — across everything pressable on screen. It is strict
about what counts: a button labelled "Add 2 to cart" is a button, not "the second thing", so only a
whole-phrase number ("tap 2") is treated as a position.

## Describing a screen (v3.3)

```
Vaani, what's on my screen
Vaani, describe this
```

Answers in a sentence — the app, the heading, what you can press, whether there are text fields —
rather than walking every element the way a screen reader does. One question, one answer.

## Clipboard and inbox (v3.3)

```
Vaani, save this link
Vaani, what did I copy
Vaani, show my inbox
```

"Save this" puts whatever is on the clipboard into a local inbox (a JSON file in the app's own
directory — it never leaves the phone). "Show my inbox" tells you how many things are in it and what
the newest one is.

> Android 10 and later only let the *foreground* app read the clipboard. The service is a foreground
> service so it normally works, but if the phone refuses, Vaani says so instead of guessing.


## Memory, notes and what arrived (v3.4)

```
Vaani, remember this is my keys
Vaani, where are my keys
Vaani, take a note
Vaani, find my note about the plumber
Vaani, what's new
```

- **remember** keeps a label and, if you were just using the lens, the picture it took.
- **where is** answers with the label, how long ago, and any note you added.
- **take a note** records what you say next and keeps the words, searchable later.
- **find my note** searches by what was said, not when it was said.
- **what's new** summarises recent notifications by app, ignoring media players and downloads.

## Reading aloud (v3.4)

```
Vaani, read this
Vaani, next paragraph
Vaani, faster
Vaani, stop reading
```

The screen text is split at sentence ends into chunks, so you can pause, skip and change speed
mid-article. While a reading is active, "next" and "stop" control the reading rather than the music.

## Your details (v3.4)

```
Vaani, save my address as 12 MG Road, Bengaluru
Vaani, fill my address
Vaani, fill my email
```

Kept in a file in the app's private directory — never uploaded, never synced. Fields: name, first
name, last name, email, phone, address, city, pin code, UPI ID.

## Converting a file (v3.4)

```
Vaani, convert a file
```

Opens a converter that does what the phone can do by itself:

- **images → PDF** (JPG, PNG, WebP — centred on an A4 page, aspect ratio kept)
- **text → PDF** (TXT, MD — paginated at 11pt)
- **PDF → images** (one PNG per page)

Office formats are refused with a reason, because Android has no renderer for them.


## Compressing to a size (v3.5)

```
Vaani, compress this to 15 KB
Vaani, shrink this to 2 MB
Vaani, compress to 100 KB
```

Opens the converter already set to that budget. Pick the file, and it returns the highest quality
that fits under it — never over. Sizes are understood as KB, MB or plain bytes, in digits.

## Converting on the spot (v3.5)

```
Vaani, convert this to pdf
Vaani, convert to png
Vaani, convert a file
```

Opens the converter with the right target. JPEG/PNG/WebP → PDF, text → PDF, PDF → images.
Office formats are refused with a reason rather than silently mangled.

How the size search works, and why the Fourier idea cannot work: `docs/COMPRESS.md`.


## Emoji, Spotify and cinema (v3.6)

```
Vaani, send a heart emoji
Vaani, send thumbs up emoji
Vaani, play lofi on spotify
Vaani, spotify play arijit singh
Vaani, book movie tickets
Vaani, new movies
```

Emoji go into the message field that is already open — open the chat first. 40 names are mapped
(heart, laugh, thumbs up, fire, pray, party, and so on).

Spotify uses its own search link, so it works without screen control. Cinema opens BookMyShow,
which takes over the link if it is installed.

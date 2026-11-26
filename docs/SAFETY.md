# Safety: what Vaani does at the edge of your money

Two hard lines, from the start: **no payments behind your back, no piracy.**

v2.1 refines the first one. It used to be a wall — anything that looked like money stopped dead.
That was safe but useless: you could not ask it to shop. Now there is a gate instead of a wall.

## Three tiers, and which one applies

| Tier | Examples | What happens |
|---|---|---|
| **ALLOW** | search, add to cart, open an app, scroll, change quantity | just does it |
| **AUTHORISE** | `checkout`, `place order`, `buy now`, `proceed to pay`, `pay now` | stops, shows the summary card, waits for your yes |
| **CONFIRM** | uninstall, clear data, log out, delete account | asks out loud, proceeds on yes |
| **DENY** | UPI PIN, OTP, CVV, card number, payment apps, torrents | refuses, always |

The line between AUTHORISE and DENY is the point of the whole design: **Vaani will take you to the
payment step and never take the money.** It presses one button at most, then hands over.

## The order gate, step by step

1. You ask for something: *"Vaani, order the Sony headphones on Amazon."*
2. Vaani navigates, searches, opens the product, adds to cart, opens checkout. All ordinary steps.
3. The next tap would commit — `Place your order`. Instead of tapping, it stops.
4. `ScreenSummary` reads the checkout screen and pulls out the four or five facts a person
   actually checks:

```
        Confirm before I go ahead
        Amazon
        •  Product · Sony WH-CH720N Noise Cancelling Headphones, Black
        •  Details · Colour: Black, Quantity: 1
        •  Price · ₹7,990
        •  Total · Order total: ₹8,469
        •  Delivery · FREE delivery Tue, 25 Nov
        •  Payment · Pay with UPI

        This is what I read on the screen. Check it — then pay yourself.
                                    [ Cancel ]  [ Looks good ]
```

5. It says the same facts out loud, in the language you were speaking, and opens the mic.
6. You answer either way — tap **Looks good**, or just say *"haan theek hai"* / *"looks good"*.
   First answer wins.
7. On yes: **one** tap, then the plan halts and it says *"Done — finish the payment yourself."*
8. On no, on silence, or on anything ambiguous: nothing happens, and the card closes.

## Clearing what is in the way

Some steps fail for a boring reason: a cookie banner, an upsell or a permission prompt sits on top
of the button. Vaani may dismiss **one** such overlay — twice at most per step — and retry the
original step. This is the only category of action it takes that was never in the plan.

It is deliberately narrow:

- Only labels on an explicit allow-list: *reject all, decline, not now, no thanks, maybe later,
  skip, dismiss, got it, ok, allow while using the app*.
- **Refusing consent is preferred over accepting it** — "reject all" and "decline" are tried
  before "accept", because the safer default is the one that shares less.
- Never on a screen the guard has flagged. A checkout page or a payment sheet is not something to
  go poking at, so `Obstacle` checks `SafetyGuard.forScreen` first and does nothing if it objects.
- Never more than twice, then it stops and points at the control instead.

## When it can't, it points

Refusing with "I couldn't do that" is useless. Every dead end now ends with a direction:

| Situation | What you hear |
|---|---|
| Button blocked by the guard | *"That's a payment screen, and I don't move money."* then *"It's at the bottom of the screen, just under 'Order total ₹8,469'."* |
| Button not on the screen | *"I can't see Buy now. I can see Place your order — shall I tap that?"* |
| Same, and you say no | *"I can't see Buy now. It's at the middle of the screen, just above 'Continue'."* |
| Screen control switched off | it opens **Settings → Accessibility** for you |
| Contacts permission missing | it opens this app's settings page |

The direction comes from the accessibility bounds: which third of the screen, and the nearest label
directly above or below it. It is a pointer, not a promise — a badly labelled screen gives a vague
pointer, which is still better than a dead end.

## Why silence is a no

The card times out after 90 seconds and treats that as a refusal. An assistant that waits forever
holds your phone hostage; one that defaults to yes spends your money. Neither is acceptable, so
the default is no.

## What it will never do, gate or no gate

- Type a UPI PIN, OTP, CVV or card number.
- Open PhonePe, Paytm, GPay, BHIM or a crypto exchange.
- Press a second time after you approve — the plan is halted after the single tap, so it cannot
  walk from "Place order" into a payment sheet and start filling it.
- Complete a purchase without a human answer.

If a screen contains a credential field (`UPI PIN`, `enter OTP`, `CVV`, `card number`), the
screen-level check stops the plan outright before any tap is considered.

## Turning it off

The **"Ask me before an order or payment"** switch in the app is on by default. Switching it off
does not unlock payments — it restores the old behaviour, where Vaani refuses order steps entirely
rather than gating them.

## What the summary can get wrong

It is a heuristic over whatever the app exposes to the accessibility tree. It finds prices by
pattern, delivery by date-shaped text, and the product by "the longest sensible line near the
top". On a well-built checkout it is accurate; on a badly labelled screen it will be thin, and on
a screen it cannot read at all it shows the first few lines instead of pretending.

That is precisely why the card exists: **you are the check, not the text.** Read the bullets, and
if they do not match what you expected, cancel and do it yourself.

## Privacy

- Speech recognition prefers the on-device model; when a phone has no offline model, Android falls
  back to its network recogniser and that audio is handled by Google's service, not by Vaani.
- Nothing Vaani does is uploaded. No analytics, no account.
- The log is in memory only, capped at 250 lines, and dies with the process.

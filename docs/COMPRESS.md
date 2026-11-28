# Compressing to a size, not to a "quality"

## Why a target size needs a search

"How small does this JPEG get?" has no formula. It depends on the picture: a photo of a wall
compresses beautifully, a photo of grass does not. So asking for **under 15 KB** is a search problem,
not a setting.

`compress/Compressor.kt` binary-searches the encoder's quality, and when even the lowest usable
quality is still too large it shrinks the image and searches again:

```
try quality 95 at full size      → 340 KB   too big
try quality 53 at full size      →  22 KB   too big
try quality 32 at full size      →  14 KB   fits, keep it, try higher
try quality 42 at full size      →  18 KB   too big
→ 14 KB at quality 32
```

It returns the **highest quality that fits**, not the smallest file it can make — a 3 KB image is
easy and useless.

One deliberate policy: **resolution is spent before quality.** If a bigger budget lets it keep full
resolution at quality 15 rather than 40% size at quality 40, it keeps the pixels. For photographs
that looks better, which is why the tests assert scale and bytes rather than the quality number.

The search is tested against a synthetic encoder, so the logic is verified without a phone or a
single real JPEG.

## The Fourier idea, and why it does not compress anything

Worth writing down, because it is a natural idea and it is wrong in a specific, provable way.

The proposal: take a file's bits as a signal, run a Fourier transform, store only the series, and
reconstruct on decompression. Three problems:

1. **The transform is a rotation, not a reduction.** A file of N bits has N/2 complex coefficients.
   Stored as floats that is roughly 4× *larger* than the original. The Fourier transform is exactly
   invertible — which means it cannot, by itself, remove anything.
2. **Compression only comes from throwing coefficients away** — and that is lossy. It works for
   photographs because real scenes are smooth: almost all the energy sits in low frequencies, so
   dropping the high ones costs little visible detail. **This is precisely what JPEG does already**,
   with a DCT (Fourier's cosine cousin) on 8×8 blocks, then quantising the coefficients.
3. **For arbitrary files there is nothing to throw away.** Text, code and archives are statistically
   near-random; their spectrum is flat. Discarding "high frequencies" discards real bits and the file
   comes back corrupted.

So the frequency trick is already inside the JPEG you are compressing, and it does not generalise to
files. What does work for arbitrary data is what this app uses everywhere else: find the redundancy
that actually exists (word overlap, repeated structures) or re-encode into a format that has already
done the analysis — which is why compressing a JPEG to 15 KB means re-encoding it, not transforming
its bytes.

## What each type gets

| Input | What happens | Honest cost |
|---|---|---|
| **JPEG / PNG / WebP** | Re-encoded at the highest quality that fits; downscaled only if quality alone cannot reach the target | Recompression loses a little sharpness; a target below ~5 KB will look soft |
| **PDF** | Pages rendered, re-encoded as JPEG, assembled into a new PDF | **Text stops being selectable.** Same trade every online "compress PDF" makes |
| **Text** | Converted to PDF; text files are already tiny | — |
| **Office files** | Refused with a reason | Android has no renderer for them |

A PDF that is mostly images barely shrinks; one that is mostly text shrinks a lot and still looks
right on screen.

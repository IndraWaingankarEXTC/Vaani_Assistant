# Memory: 2 GB + 1 GB = a 3 GB budget

## What the extra gigabyte actually is

The phone has 2 GB. Nothing can add physical RAM. What you *can* add is memory the kernel is
always free to take back the moment something else needs it:

1. **mmap-ed model weights.** The `.gguf` is mapped, not read into anonymous memory. Pages live
   in page cache; under pressure the kernel evicts them and they are re-read from flash on next
   use. Resident cost falls to whatever is actually hot.
2. **Page cache** for the app's own code and resources.
3. Optionally **zram swap**, if your ROM has it enabled — compressed RAM, slower than RAM, much
   faster than flash. Vaani does not require it.

So the app plans against 3 GB, but treats the last gigabyte as a loan. `MemoryGovernor` is the
accountant.

## The policy

| Free RAM | What happens |
|---|---|
| > 1 000 MB | model may stay resident (`warmUp()` is allowed) |
| 600–1 000 MB | model is loaded per utterance, then destroyed |
| < 600 MB, or `lowMemory` | model released; rules-only |
| `onTrimMemory` ≥ `RUNNING_LOW` | model released immediately |

`MemoryGovernor.canHoldModel()` gates the warm-up; `shouldRelease()` is checked every 60 s and on
every trim callback. Release is one call: `llama_free` + `llama_model_free`.

## Why rules still come first

Even with 3 GB, the grammar answers in microseconds and cannot hallucinate a payment. The model is
a fallback for phrasing the grammar missed, not the main path. With it resident, an ambiguous
utterance costs one generation; with it evicted, it costs a load plus a generation; with it
disabled, nothing.

## What to watch on a real device

```bash
adb shell dumpsys meminfo com.vaani.assistant | head -20
adb shell cat /proc/meminfo | grep -E "MemAvailable|SwapFree"
adb logcat -s Vaani | grep -E "Mem|LLM"
```

If `MemAvailable` sits below ~500 MB while the model is resident, the phone is thrashing: either
turn the model off, or switch to the 360M checkpoint.

## The VAD is the other memory cost

`AudioGate` holds a 16 kHz mono `AudioRecord` with a small ring buffer — a few hundred kilobytes,
not megabytes. The expensive part of hands-free is CPU and battery, not RAM.

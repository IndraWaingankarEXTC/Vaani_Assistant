# Optional: a local model for odd phrasing

**You do not need this.** The rule grammar handles the everyday command surface, and it is the
default because it is free.

Add the model only if you find yourself rephrasing commands the grammar misses. With it on, an
`Unknown` utterance is handed to a 360M–500M parameter model that must answer in the same closed
JSON command set. Everything else stays identical.

## Which model, on the 3 GB budget

v2 can keep a model resident (see `docs/MEMORY.md`), so the ceiling moved up — but only as far as
the reclaimable gigabyte allows.

| Model | Q4_K_M | Peak resident | Verdict |
|---|---|---|---|
| SmolLM2-360M-Instruct | ~230 MB | ~250 MB | Safe on any phone, best battery |
| Qwen2.5-0.5B-Instruct | ~400 MB | ~430 MB | Good balance, decent Hinglish |
| **Qwen2.5-1.5B-Instruct** | ~1.0 GB | ~1.1 GB | **Best code-mixed quality; needs the full 3 GB budget** |
| Gemma-2-2B | ~1.6 GB | ~1.8 GB | No — pushes a 2 GB phone into swap |

For mixed Hindi/English/Tamil phrasing, Qwen2.5-1.5B is the one that actually holds the sentence
together. Below that, expect the grammar to do the work and the model to handle only simple
rephrasings.

Put the `.gguf` at `/data/data/com.vaani.assistant/files/model.gguf` (or set
`Prefs.llmModelPath`), then switch on "Use local model for odd phrasing".

```bash
adb push smollm2-360m-instruct-q4_k_m.gguf /data/local/tmp/model.gguf
adb shell run-as com.vaani.assistant cp /data/local/tmp/model.gguf files/model.gguf
```

## Resident vs per-utterance

`LlmPlanner.warmUp()` maps the model and keeps it, if `MemoryGovernor.canHoldModel()` says there
is headroom (> 1 GB free). Then an ambiguous utterance costs one generation, nothing else.

When the phone gets tight — free RAM under 600 MB, `lowMemory`, or `onTrimMemory(RUNNING_LOW)` —
`release()` drops it and the next ambiguous utterance pays a load again. Below that, the rules
answer alone. Weights are `mmap`-ed, so "resident" never means "pinned": the kernel can evict
pages whenever it likes, and they come back from flash on demand.

The **Memory** button in the app shows what is actually happening on your device.

## Wiring llama.cpp

`llm/TinyLlmBridge.kt` declares three natives. Build llama.cpp for arm64-v8a and add a small
`vaani_llm.cpp`:

```cpp
#include <jni.h>
#include "llama.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_vaani_assistant_llm_TinyLlmBridge_nativeCreate(
        JNIEnv* env, jobject, jstring path, jint ctx, jint threads) {
    // llama_backend_init(); llama_model_load_from_file(...); llama_new_context_with_model(...)
    // return (jlong) handle;  0 on failure
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vaani_assistant_llm_TinyLlmBridge_nativeGenerate(
        JNIEnv* env, jobject, jlong handle, jstring prompt, jint maxTokens) {
    // llama_decode loop, stop on "</s>" or maxTokens, return NewStringUTF
}

extern "C" JNIEXPORT void JNICALL
Java_com_vaani_assistant_llm_TinyLlmBridge_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    // llama_free / llama_model_free / llama_backend_free
}
```

Build with the NDK, then drop the `.so` into `app/src/main/jniLibs/arm64-v8a/libvaani_llm.so`.
`TinyLlmBridge.load()` returns false when the library is absent, and the app silently stays
rule-only — that is why the codebase builds and runs without any of this.

## Guardrails that survive the model

- The prompt constrains output to `CommandJson.schema()`; anything outside it fails to parse and
  is discarded.
- The parsed `Command` goes through the same `Planner` and the same `SafetyGuard`.
- Screen text is passed as context but is treated as data, never as instructions.

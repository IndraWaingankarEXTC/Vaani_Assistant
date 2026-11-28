package com.vaani.assistant.llm

import com.vaani.assistant.core.Vlog

/**
 * JNI surface for a llama.cpp build (see docs/LLM.md). The library is optional: if
 * `libvaani_llm.so` is not packaged, [load] returns false and the assistant simply runs on the
 * rule grammar, which is the default on a 2 GB phone anyway.
 *
 * Expected C signatures (llama.cpp >= b3xxx):
 *   long  create(const char* model_path, int ctx, int threads)
 *   char* generate(long handle, const char* prompt, int max_tokens)
 *   void  destroy(long handle)
 */
object TinyLlmBridge {

    private var attempted = false
    private var loaded = false

    fun load(): Boolean {
        if (attempted) return loaded
        attempted = true
        loaded = runCatching { System.loadLibrary("vaani_llm") }
            .onFailure { Vlog.i("LLM", "no native library, staying rule-only") }
            .isSuccess
        return loaded
    }

    fun create(modelPath: String, contextSize: Int, threads: Int): Long =
        nativeCreate(modelPath, contextSize, threads)

    fun generate(handle: Long, prompt: String, maxTokens: Int): String =
        nativeGenerate(handle, prompt, maxTokens) ?: ""

    fun destroy(handle: Long) = nativeDestroy(handle)

    private external fun nativeCreate(modelPath: String, contextSize: Int, threads: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String?
    private external fun nativeDestroy(handle: Long)
}

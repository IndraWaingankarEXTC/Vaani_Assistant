package com.vaani.assistant.llm

import android.content.Context
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.exec.MemoryGovernor
import com.vaani.assistant.nlu.Command
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Optional fallback planner backed by a local model.
 *
 * With a 3 GB budget the model can stay mapped between commands, which removes the per-utterance
 * load cost entirely: weights come straight from page cache and the kernel can still evict them
 * under pressure. If the phone gets tight, [MemoryGovernor] makes us drop it and we fall back to
 * loading per utterance, and then to the rule grammar alone.
 */
class LlmPlanner(private val ctx: Context) {

    private val governor = MemoryGovernor(ctx)
    private var handle = 0L
    private var loadedPath: String? = null

    private val modelFile: File?
        get() = (Prefs.llmModelPath?.let { File(it) } ?: File(ctx.filesDir, "model.gguf"))
            .takeIf { it.exists() && it.length() > 0 }

    val isResident: Boolean get() = handle != 0L

    fun available(): Boolean = Prefs.llmEnabled && modelFile != null && TinyLlmBridge.load()

    /** Load and keep the model mapped, if the phone has the headroom. */
    fun warmUp(): Boolean {
        if (!available()) return false
        if (handle != 0L) return true
        if (Prefs.modelResident && !governor.canHoldModel()) {
            Vlog.i("LLM", "no headroom to keep the model resident")
            return false
        }
        val file = modelFile ?: return false
        return runCatching {
            val h = TinyLlmBridge.create(file.absolutePath, CONTEXT, THREADS)
            if (h == 0L) {
                Vlog.e("LLM", "model failed to load")
                false
            } else {
                handle = h
                loadedPath = file.absolutePath
                Vlog.i("LLM", "model resident (${file.length() / (1024 * 1024)}MB)")
                true
            }
        }.getOrElse {
            Vlog.e("LLM", "warm-up failed: ${it.message}")
            false
        }
    }

    fun release() {
        if (handle == 0L) return
        runCatching { TinyLlmBridge.destroy(handle) }
        Vlog.i("LLM", "model released")
        handle = 0L
        loadedPath = null
    }

    /** Called by the service on memory pressure and on a periodic tick. */
    fun trimIfNeeded() {
        if (handle != 0L && governor.shouldRelease()) release()
    }

    suspend fun parse(utterance: String, screen: String): Command? = withContext(Dispatchers.IO) {
        if (!available()) return@withContext null

        val resident = warmUp()
        if (resident) {
            return@withContext generate(handle, utterance, screen)
        }

        // Not resident: borrow the model for one utterance and give it straight back.
        val file = modelFile ?: return@withContext null
        var temp = 0L
        try {
            temp = TinyLlmBridge.create(file.absolutePath, CONTEXT, THREADS)
            if (temp == 0L) {
                Vlog.e("LLM", "model failed to load")
                return@withContext null
            }
            generate(temp, utterance, screen)
        } catch (t: Throwable) {
            Vlog.e("LLM", "inference failed: ${t.message}")
            null
        } finally {
            if (temp != 0L) runCatching { TinyLlmBridge.destroy(temp) }
        }
    }

    private fun generate(h: Long, utterance: String, screen: String): Command? = runCatching {
        val raw = TinyLlmBridge.generate(h, prompt(utterance, screen), MAX_TOKENS)
        Vlog.i("LLM", "raw: ${raw.take(120)}")
        CommandJson.parse(raw)
    }.getOrElse {
        Vlog.e("LLM", "generation failed: ${it.message}")
        null
    }

    private fun prompt(utterance: String, screen: String): String = buildString {
        appendLine("You turn a spoken phone command into one JSON action.")
        appendLine("The speaker may mix English with Hindi, Tamil, Telugu, Bengali or Marathi.")
        appendLine(CommandJson.schema())
        appendLine()
        if (screen.isNotBlank()) {
            appendLine("Visible screen elements:")
            appendLine(screen.take(1200))
            appendLine()
        }
        appendLine("Command: $utterance")
        append("JSON:")
    }

    private companion object {
        const val CONTEXT = 2048
        const val THREADS = 3
        const val MAX_TOKENS = 96
    }
}

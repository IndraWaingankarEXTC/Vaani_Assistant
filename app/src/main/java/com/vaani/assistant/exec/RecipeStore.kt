package com.vaani.assistant.exec

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.nlu.MediaAction
import org.json.JSONObject
import java.io.File

/**
 * Taught flows: "when I say X, do these steps". This is how the assistant covers the long tail of
 * detail inside a specific app (an Amazon search, an Obsidian daily note) without any model.
 *
 * Stored as `recipes.json` in the app's files directory. See docs/RECIPES.md for the format.
 */
class RecipeStore(private val ctx: Context) {

    data class Recipe(val name: String, val phrases: List<String>, val steps: List<Step>)

    private var cache: List<Recipe> = emptyList()
    private var loadedAt = 0L

    private val file: File get() = File(ctx.filesDir, "recipes.json")

    fun all(): List<Recipe> {
        val stamp = file.lastModified()
        if (stamp != loadedAt) {
            cache = load()
            loadedAt = stamp
        }
        return cache
    }

    fun reload(): List<Recipe> {
        loadedAt = 0L
        return all()
    }

    fun match(utterance: String): Recipe? {
        val n = Text.norm(utterance)
        if (n.isEmpty()) return null
        val recipes = all()
        if (recipes.isEmpty()) return null
        recipes.firstOrNull { r -> r.phrases.any { Text.norm(it) == n } }?.let { return it }
        val pairs = recipes.flatMap { r -> r.phrases.map { Text.norm(it) to r } }
        val hit = Text.bestMatch(n, pairs.map { it.first }, 0.82) ?: return null
        return pairs.firstOrNull { it.first == hit }?.second
    }

    private fun load(): List<Recipe> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("recipes") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name", "recipe")
                val phrases = o.optJSONArray("phrases")?.let { p ->
                    (0 until p.length()).map { p.optString(it) }
                } ?: emptyList()
                val stepsArr = o.optJSONArray("steps") ?: return@mapNotNull null
                val steps = (0 until stepsArr.length()).mapNotNull { s -> step(stepsArr.optJSONObject(s)) }
                if (steps.isEmpty()) null else Recipe(name, phrases, steps)
            }
        }.getOrElse {
            Vlog.e("Recipes", "could not parse recipes.json: ${it.message}")
            emptyList()
        }
    }

    private fun step(o: JSONObject?): Step? {
        if (o == null) return null
        return when (o.optString("do")) {
            "launch" -> Step.Launch(o.optString("pkg"), o.optString("uri").ifBlank { null })
            "uri" -> Step.OpenUri(o.optString("uri"), o.optString("pkg").ifBlank { null })
            "tap" -> Step.Tap(o.optString("label"), o.optLong("timeout", 6000))
            "wait" -> Step.WaitFor(o.optString("label"), o.optLong("timeout", 6000))
            "pause" -> Step.Pause(o.optLong("ms", 800))
            "type" -> Step.Type(o.optString("text"), o.optBoolean("submit", false))
            "search" -> Step.FocusSearch()
            "scroll" -> Step.Scroll(o.optBoolean("forward", true))
            "back" -> Step.Back
            "home" -> Step.Home
            "say" -> Step.Say(o.optString("text"))
            "media" -> Step.Media(
                when (o.optString("action")) {
                    "play" -> MediaAction.PLAY
                    "pause" -> MediaAction.PAUSE
                    "next" -> MediaAction.NEXT
                    "prev" -> MediaAction.PREV
                    "stop" -> MediaAction.STOP
                    else -> MediaAction.TOGGLE
                }
            )
            "open" -> Step.Raw(
                Intent(Intent.ACTION_VIEW, Uri.parse(o.optString("uri")))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            else -> null
        }
    }

    fun plan(recipe: Recipe): Plan = Plan(recipe.steps, "Running ${recipe.name}")
}

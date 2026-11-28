package com.vaani.assistant.nlu

import android.content.Context
import android.content.pm.PackageManager
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog

/**
 * Resolves a spoken app name to an installed package, plus an optional deep link that lands the
 * user directly on a search or a place. Deep links are far more reliable (and cheaper in RAM)
 * than tapping through an app's UI.
 */
class AppRegistry(private val ctx: Context) {

    data class Target(
        val label: String,
        val pkg: String,
        /** printf-style template; %s is replaced with the URL-encoded query. */
        val searchUri: String? = null
    )

    private data class Known(val aliases: List<String>, val target: Target)

    private val known: List<Known> = listOf(
        Known(listOf("spotify", "songs", "music"), Target("Spotify", "com.spotify.music", "spotify:search:%s")),
        Known(listOf("youtube music", "yt music", "music app"), Target("YouTube Music", "com.google.android.apps.youtube.music", "https://music.youtube.com/search?q=%s")),
        Known(listOf("amazon", "amazon shopping", "amazon app"), Target("Amazon", "in.amazon.mShop.android.shopping", "amazon://search?query=%s")),
        Known(listOf("amazon global"), Target("Amazon", "com.amazon.mShop.android.shopping", null)),
        Known(listOf("obsidian", "notes", "my notes", "vault"), Target("Obsidian", "md.obsidian", null)),
        Known(listOf("whatsapp", "wa"), Target("WhatsApp", "com.whatsapp", null)),
        Known(listOf("telegram"), Target("Telegram", "org.telegram.messenger", null)),
        Known(listOf("youtube"), Target("YouTube", "com.google.android.youtube", "https://www.youtube.com/results?search_query=%s")),
        Known(listOf("maps", "google maps", "navigation"), Target("Maps", "com.google.android.apps.maps", "geo:0,0?q=%s")),
        Known(listOf("chrome", "browser", "google"), Target("Chrome", "com.android.chrome", "https://www.google.com/search?q=%s")),
        Known(listOf("gmail", "mail", "email"), Target("Gmail", "com.google.android.gm", null)),
        Known(listOf("clock", "alarm clock", "clock app"), Target("Clock", "com.google.android.deskclock", null)),
        Known(listOf("settings", "setting"), Target("Settings", "com.android.settings", null)),
        Known(listOf("camera"), Target("Camera", "com.google.android.GoogleCamera", null)),
        Known(listOf("photos", "gallery"), Target("Photos", "com.google.android.apps.photos", null)),
        Known(listOf("instagram", "insta"), Target("Instagram", "com.instagram.android", null)),
        Known(listOf("netflix"), Target("Netflix", "com.netflix.mediaclient", null)),
        Known(listOf("phone", "dialer", "call app"), Target("Phone", "com.google.android.dialer", null)),
        Known(listOf("calculator", "calc"), Target("Calculator", "com.google.android.calculator", null)),
        Known(listOf("calendar"), Target("Calendar", "com.google.android.calendar", null)),
        Known(listOf("drive"), Target("Drive", "com.google.android.apps.docs", null)),
        Known(listOf("swiggy"), Target("Swiggy", "in.swiggy.android", null)),
        Known(listOf("zomato"), Target("Zomato", "com.application.zomato", null))
    )

    /** Installed app labels, built once and cached. A few hundred short strings, a few KB. */
    private val installedLabels: List<Pair<String, String>> by lazy {
        val pm = ctx.packageManager
        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .mapNotNull { info ->
                    if (pm.getLaunchIntentForPackage(info.packageName) == null) return@mapNotNull null
                    val label = pm.getApplicationLabel(info).toString()
                    label to info.packageName
                }
        }.getOrElse {
            Vlog.e("Apps", "could not list installed apps: ${it.message}")
            emptyList()
        }
    }

    fun installed(pkg: String): Boolean =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    fun resolve(spoken: String): Target? {
        val q = Text.norm(spoken)
        if (q.isEmpty()) return null

        // 1. exact alias
        for (k in known) {
            if (k.aliases.any { Text.norm(it) == q }) {
                if (installed(k.target.pkg)) return k.target
            }
        }
        // 2. fuzzy alias
        val aliasPairs = known.flatMap { k -> k.aliases.map { Text.norm(it) to k.target } }
        Text.bestMatch(q, aliasPairs.map { it.first }, 0.7)?.let { hit ->
            aliasPairs.firstOrNull { it.first == hit }?.let { if (installed(it.second.pkg)) return it.second }
        }
        // 3. anything actually installed on the phone
        val labels = installedLabels
        // A transliterated name lands close but not close enough ("spotiphaee"): the first five
        // letters are the reliable part.
        if (q.length >= 5) {
            val head = q.take(5)
            labels.firstOrNull { Text.norm(it.first).startsWith(head) }
                ?.let { return Target(it.first, it.second) }
        }
        Text.bestMatch(q, labels.map { it.first }, 0.6)?.let { hit ->
            labels.firstOrNull { it.first == hit }?.let { return Target(it.first, it.second) }
        }
        return null
    }

    fun labelFor(pkg: String): String =
        installedLabels.firstOrNull { it.second == pkg }?.first ?: pkg
}

package com.vaani.assistant.exec

import android.Manifest
import android.provider.AlarmClock
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vaani.assistant.core.Text
import com.vaani.assistant.convert.ConverterActivity
import com.vaani.assistant.lens.LensActivity
import com.vaani.assistant.nlu.AppRegistry
import com.vaani.assistant.nlu.Command
import com.vaani.assistant.nlu.ContactResolver
import com.vaani.assistant.voice.Reply

/**
 * Turns a parsed [Command] into an ordered list of [Step]s. Pure function of the command plus the
 * phone's installed apps and contacts — no I/O beyond a cached app list, no model in memory.
 */
class Planner(
    private val ctx: Context,
    private val apps: AppRegistry,
    private val contacts: ContactResolver
) {

    private val newTask = Intent.FLAG_ACTIVITY_NEW_TASK

    fun plan(cmd: Command): Plan = when (cmd) {
        is Command.OpenApp -> planOpen(cmd)
        is Command.SearchInApp -> planOpen(Command.OpenApp(cmd.app, cmd.query))
        is Command.WebSearch -> Plan(
            listOf(
                Step.Raw(
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, cmd.query)
                        addFlags(newTask)
                    }
                )
            ),
            "Searching for ${cmd.query}"
        )
        is Command.Call -> planCall(cmd)
        is Command.SendSms -> planSms(cmd)
        is Command.SendWhatsApp -> planWhatsApp(cmd)
        is Command.SetAlarm -> planAlarm(cmd)
        is Command.SetTimer -> planTimer(cmd)
        is Command.Media -> Plan(listOf(Step.Media(cmd.action)))
        is Command.Volume -> Plan(listOf(Step.Volume(cmd.up)))
        is Command.Navigate -> Plan(
            listOf(Step.Raw(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(cmd.place))).apply { addFlags(newTask) })),
            "Finding directions to ${cmd.place}"
        )
        is Command.Toggle -> planToggle(cmd)
        is Command.Tap -> Plan(listOf(Step.Tap(cmd.text)))
        is Command.TypeText -> Plan(listOf(Step.Type(cmd.text, cmd.submit)))
        is Command.Scroll -> Plan(listOf(Step.Scroll(cmd.forward)))
        Command.Back -> Plan(listOf(Step.Back))
        Command.Home -> Plan(listOf(Step.Home))
        Command.Recents -> Plan(listOf(Step.Recents))
        Command.ReadScreen -> Plan(listOf(Step.ReadScreen))
        Command.Repeat -> Plan(emptyList(), "There is nothing to repeat yet.")
        // Handled by the voice service, which owns the microphone; nothing to execute here.
        Command.Dictate -> Plan(emptyList(), null)
        Command.StopDictation -> Plan(emptyList(), null)
        Command.SaveClip, Command.ReadClipboard, Command.ListInbox -> Plan(emptyList(), null)
        // Handled inside the service, which owns the stores, the speech and the screen.
        is Command.Remember, is Command.WhereIs, Command.TakeNote, is Command.FindNote,
        Command.Notifications, Command.ReadAloud, is Command.ReadAloudControl,
        is Command.FillField, is Command.SaveDetail -> Plan(emptyList(), null)
        Command.ConvertFile -> Plan(
            listOf(Step.Raw(Intent(ctx, ConverterActivity::class.java).addFlags(newTask))),
            "Opening the converter"
        )
        is Command.CompressFile -> Plan(
            listOf(
                Step.Raw(
                    Intent(ctx, ConverterActivity::class.java)
                        .putExtra(ConverterActivity.EXTRA_COMPRESS, true)
                        .putExtra(ConverterActivity.EXTRA_TARGET, cmd.targetBytes)
                        .addFlags(newTask)
                )
            ),
            "Compressing to under ${com.vaani.assistant.compress.Sizes.human(cmd.targetBytes)}"
        )
        is Command.ConvertTo -> Plan(
            listOf(Step.Raw(Intent(ctx, ConverterActivity::class.java).addFlags(newTask))),
            "Opening the converter for ${cmd.format.uppercase()}"
        )
        is Command.CompressFile -> Plan(
            listOf(
                Step.Raw(
                    Intent(ctx, ConverterActivity::class.java)
                        .putExtra(ConverterActivity.EXTRA_COMPRESS, true)
                        .putExtra(ConverterActivity.EXTRA_TARGET, cmd.targetBytes)
                        .addFlags(newTask)
                )
            ),
            "Compressing to under ${com.vaani.assistant.compress.Sizes.human(cmd.targetBytes)}"
        )
        is Command.ConvertTo -> Plan(
            listOf(Step.Raw(Intent(ctx, ConverterActivity::class.java).addFlags(newTask))),
            "Opening the converter for ${cmd.format.uppercase()}"
        )
        Command.OpenLens -> Plan(
            listOf(Step.Raw(Intent(ctx, LensActivity::class.java).addFlags(newTask))),
            "Opening the lens"
        )
        is Command.Compound -> {
            val parts = cmd.parts.map { plan(it) }
            val say = parts.mapNotNull { it.say }.firstOrNull()
            Plan(parts.flatMap { it.steps }, say)
        }
        is Command.Unknown -> Plan(emptyList(), "Sorry, I didn't catch a command in that.")
    }

    // ------------------------------------------------------------------ apps

    private val genericAlias = setOf("music", "songs", "music app", "notes", "my notes")

    private fun planOpen(c: Command.OpenApp): Plan {
        // "play youtube lofi" resolves to the generic music app; the real app is inside the query.
        if (c.query != null && Text.norm(c.app) in genericAlias) {
            splitAppAndQuery(c.query)?.let { inner ->
                if (inner.first.label != c.app) {
                    return planOpen(Command.OpenApp(inner.first.label, inner.second))
                }
            }
        }
        val direct = apps.resolve(c.app)
        val target: AppRegistry.Target
        val query: String?
        if (direct != null) {
            target = direct
            query = c.query?.takeIf { it.isNotBlank() }
        } else {
            // "open obsidian daily note" -> app "obsidian", query "daily note"
            val split = splitAppAndQuery(c.app)
                ?: return Plan(emptyList(), "I couldn't find an app called ${c.app} on this phone.")
            target = split.first
            query = split.second ?: c.query?.takeIf { it.isNotBlank() }
        }
        val deepLink = if (query != null && target.searchUri != null) {
            target.searchUri.replace("%s", Uri.encode(query))
        } else null

        val steps = mutableListOf<Step>()
        steps += if (deepLink != null) Step.OpenUri(deepLink, target.pkg) else Step.Launch(target.pkg)
        if (query != null && deepLink == null) {
            steps += Step.Pause(1000)
            steps += Step.FocusSearch()
            steps += Step.Type(query, submit = true)
        }
        val say = if (query != null) "Playing $query on ${target.label}" else "Opening ${target.label}"
        return Plan(steps, say)
    }

    /** Longest installed-app prefix of a spoken phrase, with the rest treated as a search. */
    private fun splitAppAndQuery(spoken: String): Pair<AppRegistry.Target, String?>? {
        val words = spoken.split(' ').filter { it.isNotBlank() }
        if (words.size < 2) return null
        for (i in 1 until words.size) {
            val head = words.take(i).joinToString(" ")
            val hit = apps.resolve(head) ?: continue
            val rest = words.drop(i).joinToString(" ").trim().ifBlank { null }
            return hit to rest
        }
        return null
    }

    // ------------------------------------------------------------------ calls & messages

    /** No contacts permission: open the exact settings page the user needs, rather than failing. */
    private fun contactsDenied(): Plan = Plan(
        listOf(
            Step.Raw(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + ctx.packageName)
                ).addFlags(newTask)
            )
        ),
        Reply.say("contacts_needed", null)
    )

    private fun planCall(c: Command.Call): Plan {
        if (!contacts.hasPermission()) return contactsDenied()
        val contact = contacts.find(c.who)
            ?: return Plan(emptyList(), "I couldn't find ${c.who} in your contacts.")
        val canCall = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(
            if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse("tel:" + Uri.encode(contact.number))
        ).apply { addFlags(newTask) }
        return Plan(listOf(Step.Raw(intent)), "Calling ${contact.name}")
    }

    private fun planSms(c: Command.SendSms): Plan {
        if (!contacts.hasPermission()) return contactsDenied()
        val contact = contacts.find(c.who)
            ?: return Plan(emptyList(), "I couldn't find ${c.who} in your contacts.")
        if (c.text.isBlank()) {
            return Plan(
                listOf(Step.Raw(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.number}")).apply { addFlags(newTask) })),
                "Opening a message to ${contact.name}"
            )
        }
        val canSend = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        return if (canSend) {
            Plan(listOf(Step.SendSms(contact.number, c.text)), "Texting ${contact.name}")
        } else {
            Plan(
                listOf(
                    Step.Raw(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.number}")).apply {
                            putExtra("sms_body", c.text)
                            addFlags(newTask)
                        }
                    ),
                    Step.WaitFor("send", 8000),
                    Step.Tap("send", 5000)
                ),
                "Texting ${contact.name}"
            )
        }
    }

    private fun planWhatsApp(c: Command.SendWhatsApp): Plan {
        if (!contacts.hasPermission()) return contactsDenied()
        val contact = contacts.find(c.who)
        val text = c.text.ifBlank { "" }
        if (contact != null) {
            val digits = contact.number.filter { it.isDigit() }
            if (digits.length >= 7) {
                val uri = "https://wa.me/$digits" + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else ""
                val steps = mutableListOf<Step>(Step.OpenUri(uri, "com.whatsapp"))
                if (text.isNotEmpty()) {
                    steps += Step.WaitFor("send", 8000)
                    steps += Step.Tap("send", 6000)
                }
                return Plan(steps, "Messaging ${contact.name} on WhatsApp")
            }
        }
        // No number on file: drive WhatsApp's own UI instead.
        val steps = mutableListOf<Step>(
            Step.Launch("com.whatsapp"),
            Step.Pause(1500),
            Step.FocusSearch(),
            Step.Type(c.who, submit = false),
            Step.Pause(1200),
            Step.Tap(c.who, 6000),
            Step.Pause(1000)
        )
        if (text.isNotEmpty()) {
            steps += Step.FocusSearch()
            steps += Step.Type(text, submit = false)
            steps += Step.Tap("send", 6000)
        }
        return Plan(steps, "Messaging ${c.who} on WhatsApp")
    }

    // ------------------------------------------------------------------ clock

    private fun planAlarm(c: Command.SetAlarm): Plan {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, c.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, c.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            c.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(newTask)
        }
        return Plan(listOf(Step.Raw(intent)), "Alarm set for ${"%02d".format(c.hour)}:${"%02d".format(c.minute)}")
    }

    private fun planTimer(c: Command.SetTimer): Plan {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, c.seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            c.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(newTask)
        }
        val mins = c.seconds / 60
        val say = if (mins >= 1) "Timer set for $mins minutes" else "Timer set for ${c.seconds} seconds"
        return Plan(listOf(Step.Raw(intent)), say)
    }

    // ------------------------------------------------------------------ system toggles

    private fun planToggle(c: Command.Toggle): Plan = when (c.setting) {
        "flashlight", "torch" ->
            Plan(listOf(Step.Flashlight(c.on)), if (c.on) "Flashlight on" else "Flashlight off")
        "dnd" ->
            Plan(listOf(Step.Dnd(c.on)), if (c.on) "Do not disturb on" else "Do not disturb off")
        "wifi" -> panel(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
        "bluetooth" -> panel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
        "airplane" -> panel(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode")
        "data" -> panel(Settings.ACTION_WIRELESS_SETTINGS, "Mobile data")
        "hotspot" -> panel(Settings.ACTION_WIFI_SETTINGS, "Hotspot")
        else -> Plan(emptyList(), "I don't know how to switch ${c.setting} on this phone.")
    }

    private fun panel(action: String, label: String): Plan = Plan(
        listOf(Step.Raw(Intent(action).apply { addFlags(newTask) })),
        "Opening $label settings"
    )
}

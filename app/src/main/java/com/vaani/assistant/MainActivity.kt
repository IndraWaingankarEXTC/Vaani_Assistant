package com.vaani.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.CrashLog
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.voice.VoiceProfiles
import java.io.File
import com.vaani.assistant.exec.MemoryGovernor
import com.vaani.assistant.ui.AccessibilityAgent
import com.vaani.assistant.voice.VoiceService

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var etCommand: EditText
    private lateinit var swService: SwitchCompat
    private lateinit var swBubble: SwitchCompat
    private lateinit var swSpeak: SwitchCompat
    private lateinit var swLlm: SwitchCompat
    private lateinit var swHandsFree: SwitchCompat
    private lateinit var swFollowUp: SwitchCompat
    private lateinit var swAutoLang: SwitchCompat
    private lateinit var swConfirmOrders: SwitchCompat
    private lateinit var swAutoPunctuation: SwitchCompat
    private lateinit var etWakePhrase: EditText
    private var binding = false

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread { tvLog.append("\n$line") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        etCommand = findViewById(R.id.etCommand)
        swService = findViewById(R.id.swService)
        swBubble = findViewById(R.id.swBubble)
        swSpeak = findViewById(R.id.swSpeak)
        swLlm = findViewById(R.id.swLlm)
        swHandsFree = findViewById(R.id.swHandsFree)
        swFollowUp = findViewById(R.id.swFollowUp)
        swAutoLang = findViewById(R.id.swAutoLang)
        swConfirmOrders = findViewById(R.id.swConfirmOrders)
        swAutoPunctuation = findViewById(R.id.swAutoPunctuation)
        etWakePhrase = findViewById(R.id.etWakePhrase)

        tvLog.text = Vlog.dump()

        findViewById<Button>(R.id.btnTalk).setOnClickListener {
            ensurePermissions()
            VoiceService.listen(this)
        }

        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            requestPermissions(PERMISSIONS, REQ_PERMISSIONS)
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnMediaAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<Button>(R.id.btnRun).setOnClickListener {
            val text = etCommand.text.toString().trim()
            if (text.isNotEmpty()) {
                VoiceService.runText(this, text)
                etCommand.setText("")
            }
        }

        findViewById<Button>(R.id.btnVoice).setOnClickListener {
            Prefs.voiceProfile = VoiceProfiles.next(Prefs.voiceProfile)
            refresh()
        }

        // Deleting things is a first-class action, not a trip to adb.
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            Vlog.clear()
            tvLog.text = ""
        }

        findViewById<Button>(R.id.btnDeleteRecipes).setOnClickListener {
            confirm("Delete taught recipes?") {
                val file = File(filesDir, "recipes.json")
                val removed = file.exists() && file.delete()
                tvLog.append("\nRecipes " + if (removed) "deleted" else "already empty")
            }
        }

        findViewById<Button>(R.id.btnResetSettings).setOnClickListener {
            confirm("Reset every setting to its default?") {
                getSharedPreferences("vaani", MODE_PRIVATE).edit().clear().apply()
                Prefs.init(this)
                refresh()
                tvLog.append("\nSettings reset")
            }
        }

        swService.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.serviceEnabled = checked
            if (checked) VoiceService.start(this) else VoiceService.stop(this)
        }
        swBubble.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.bubbleEnabled = checked
            VoiceService.setBubble(this, checked)
        }
        swSpeak.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.speakReplies = checked
        }
        swLlm.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.llmEnabled = checked
        }
        swHandsFree.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.wakeWordEnabled = checked
            VoiceService.setHandsFree(this, checked)
        }
        swFollowUp.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.followUpEnabled = checked
        }
        swAutoLang.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.autoLanguage = checked
        }
        swConfirmOrders.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.confirmOrders = checked
        }
        swAutoPunctuation.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            Prefs.autoPunctuation = checked
        }

        etWakePhrase.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val phrase = s?.toString()?.trim().orEmpty()
                if (phrase.isNotEmpty()) Prefs.wakePhrase = phrase
            }
        })

        findViewById<Button>(R.id.btnMemory).setOnClickListener {
            val stats = MemoryGovernor(this).stats()
            tvLog.append("\n" + stats.summary())
            findViewById<TextView>(R.id.btnMemory).text = "Memory · ${stats.summary()}"
        }
    }

    override fun onStart() {
        super.onStart()
        Vlog.addListener(logListener)
        // If the last run died, say so before anything else — there is no adb on a phone.
        CrashLog.take(this)?.let { crash ->
            tvLog.text = "LAST RUN CRASHED\n\n$crash\n\n---- log ----"
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        Vlog.removeListener(logListener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        binding = true
        swService.isChecked = Prefs.serviceEnabled
        swBubble.isChecked = Prefs.bubbleEnabled
        swSpeak.isChecked = Prefs.speakReplies
        swLlm.isChecked = Prefs.llmEnabled
        swHandsFree.isChecked = Prefs.wakeWordEnabled
        swFollowUp.isChecked = Prefs.followUpEnabled
        swAutoLang.isChecked = Prefs.autoLanguage
        swConfirmOrders.isChecked = Prefs.confirmOrders
        swAutoPunctuation.isChecked = Prefs.autoPunctuation
        if (etWakePhrase.text.isNullOrBlank()) etWakePhrase.setText(Prefs.wakePhrase)
        binding = false

        findViewById<Button>(R.id.btnPermissions).text = buildString {
            append("Permissions: ")
            append(if (granted(Manifest.permission.RECORD_AUDIO)) "mic ok" else "mic needed")
            append(if (granted(Manifest.permission.READ_CONTACTS)) " · contacts ok" else " · contacts needed")
            append(if (granted(Manifest.permission.CALL_PHONE)) " · phone ok" else " · phone needed")
        }

        findViewById<Button>(R.id.btnAccessibility).text = buildString {
            append("Screen control: ")
            append(if (AccessibilityAgent.ready()) "on" else "off — tap to enable")
        }

        val mediaOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        findViewById<Button>(R.id.btnMediaAccess).text =
            "Media control: " + if (mediaOk) "on" else "off — tap to enable"

        findViewById<Button>(R.id.btnOverlay).text =
            "Floating mic: " + if (Settings.canDrawOverlays(this)) "allowed" else "not allowed"

        findViewById<Button>(R.id.btnMemory).text =
            "Memory · " + MemoryGovernor(this).stats().summary()
    }

    private fun confirm(question: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(question)
            .setPositiveButton("Yes") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissions() {
        val missing = PERMISSIONS.filter { !granted(it) }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private companion object {
        const val REQ_PERMISSIONS = 200
        val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}

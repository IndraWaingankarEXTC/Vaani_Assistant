package com.vaani.assistant.vault

import android.content.Context
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog
import org.json.JSONObject
import java.io.File

/**
 * Your own details, kept on the phone, typed into forms on request.
 *
 * Deliberately small and deliberately local: name, address, email, phone, UPI ID. Nothing is
 * uploaded, nothing is synced, and the file lives in the app's private directory.
 */
class Vault(context: Context) {

    private val file = File(context.applicationContext.filesDir, "vault.json")
    private val lock = Any()

    fun put(key: String, value: String): Boolean {
        val cleanKey = VaultKeys.normalise(key) ?: return false
        val cleanValue = value.trim()
        if (cleanValue.isEmpty()) return false
        synchronized(lock) {
            val all = read().toMutableMap()
            all[cleanKey] = cleanValue
            write(all)
        }
        return true
    }

    fun get(key: String): String? {
        val cleanKey = VaultKeys.normalise(key) ?: return null
        return read()[cleanKey]
    }

    fun keys(): List<String> = read().keys.sorted()

    fun remove(key: String): Boolean = synchronized(lock) {
        val all = read().toMutableMap()
        val cleanKey = VaultKeys.normalise(key) ?: return false
        val removed = all.remove(cleanKey) != null
        write(all)
        removed
    }

    fun clear(): Boolean = synchronized(lock) { file.delete() }

    private fun read(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val json = JSONObject(file.readText())
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrElse { emptyMap() }
    }

    private fun write(values: Map<String, String>) {
        runCatching {
            val json = JSONObject()
            values.forEach { (k, v) -> json.put(k, v) }
            file.writeText(json.toString())
        }.onFailure { Vlog.e("Vault", "save failed: ${it.message}") }
    }
}

/** Which field a spoken phrase means. Pure, so the mapping can be tested. */
object VaultKeys {

    private val aliases = mapOf(
        "name" to listOf("name", "full name", "my name"),
        "firstname" to listOf("first name", "given name"),
        "lastname" to listOf("last name", "surname", "family name"),
        "email" to listOf("email", "e mail", "mail id", "email address"),
        "phone" to listOf("phone", "phone number", "mobile", "number"),
        "address" to listOf("address", "home address", "full address", "my address"),
        "city" to listOf("city", "town"),
        "pincode" to listOf("pin code", "pincode", "postal code", "zip"),
        "upi" to listOf("upi", "upi id", "vpa")
    )

    fun normalise(spoken: String): String? {
        val q = Text.norm(spoken)
        if (q.isEmpty()) return null
        for ((key, forms) in aliases) {
            if (forms.any { q == it || q.endsWith(" $it") || q.startsWith("$it ") || q.contains(it) }) {
                return key
            }
        }
        return null
    }

    fun allKeys(): List<String> = aliases.keys.sorted()
}

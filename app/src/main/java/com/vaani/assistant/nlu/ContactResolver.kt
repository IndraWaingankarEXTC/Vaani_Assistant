package com.vaani.assistant.nlu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog

/** Spoken name (or a spoken number) to a dialable number. */
class ContactResolver(private val ctx: Context) {

    data class Contact(val name: String, val number: String)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun queryByName(
        uri: Uri,
        projection: Array<String>,
        fragment: String,
        limit: Int
    ): MutableList<Contact> {
        val rows = mutableListOf<Contact>()
        val selection = if (fragment.isBlank()) null
        else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = if (fragment.isBlank()) null else arrayOf("%$fragment%")
        runCatching {
            ctx.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(projection[0])
                val numIdx = c.getColumnIndexOrThrow(projection[1])
                while (c.moveToNext() && rows.size < limit) {
                    val name = c.getString(nameIdx) ?: continue
                    val num = c.getString(numIdx) ?: continue
                    rows.add(Contact(name, num.replace(" ", "")))
                }
            }
        }.onFailure { Vlog.e("Contacts", "query failed: ${it.message}") }
        return rows
    }

    fun find(spoken: String): Contact? {
        val raw = spoken.trim()
        val digits = raw.filter { it.isDigit() }
        // A spoken phone number: "nine eight seven..." or "+91 98765 43210".
        if (digits.length >= 7 && raw.none { it.isLetter() }) {
            return Contact(raw, if (raw.startsWith("+")) raw else digits)
        }
        if (!hasPermission()) {
            Vlog.e("Contacts", "READ_CONTACTS not granted")
            return null
        }
        val q = Text.norm(spoken)
        if (q.isEmpty()) return null

        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        // The contact store keeps the apostrophe that speech drops, so the LIKE prefilter uses a
        // fragment without it, and the real decision is made by NameMatch in code.
        val token = NameMatch.queryToken(spoken)
        var rows = queryByName(uri, projection, token, limit = 60)

        // Nothing matched the fragment: fall back to a bounded scan, because "OBrien" is not a
        // substring of "O'Brien" and never will be.
        if (rows.isEmpty() && token.length >= 3) rows = queryByName(uri, projection, "", limit = 500)

        if (rows.isEmpty()) return null
        val best = NameMatch.best(spoken, rows.map { it.name }) ?: return null
        return rows.firstOrNull { it.name == best }
            ?: rows.firstOrNull { NameMatch.key(it.name) == NameMatch.key(best) }
    }
}

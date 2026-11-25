package com.dxcar.launcher.comms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Resolves incoming phone numbers to Contact Display Names via ContactsContract.PhoneLookup
 * on both Phone Companion and AI Box (Bluetooth PBAP synced contacts), and detects generic
 * placeholder caller labels so real contact names are never overwritten by raw HFP/PhoneState events.
 */
object ContactNameResolver {

    private val GENERIC_CALLER_LABELS = setOf(
        "來電",
        "未知來電",
        "藍牙來電",
        "藍牙免持來電",
        "行動電話來電",
        "手機來電",
        "行動電話",
        "藍牙電話",
        "藍牙 HFP",
        "未知號碼",
        "Unknown",
        "Incoming call",
        "Phone call"
    )

    fun isGenericOrNumberOnly(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return true
        if (GENERIC_CALLER_LABELS.any { it.equals(t, ignoreCase = true) }) return true
        // Pure phone number characters (+, -, (, ), spaces, #, *, digits)
        val stripped = t.replace(Regex("[\\s+\\-().#*0-9]"), "")
        return stripped.isEmpty()
    }

    fun extractPlausiblePhoneNumber(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val match = Regex("(\\+?\\d[\\d\\s\\-()]{4,}\\d)").find(t)
        return match?.value?.trim().orEmpty()
    }

    fun resolveDisplayName(context: Context, rawNumberOrText: String?): String? {
        val candidate = rawNumberOrText?.trim().orEmpty()
        if (candidate.isEmpty()) return null

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null

        val phoneToLookup = if (isGenericOrNumberOnly(candidate)) {
            candidate.replace(Regex("[^+0-9*#]"), "")
        } else {
            extractPlausiblePhoneNumber(candidate).replace(Regex("[^+0-9*#]"), "")
        }
        if (phoneToLookup.length < 3) return null

        return runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneToLookup)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.trim()?.takeIf { it.isNotBlank() } else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}

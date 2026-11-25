package com.dxcar.launcher.comms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

data class ResolvedCallerInfo(
    val displayName: String,
    val phoneNumber: String
)

/**
 * Resolves incoming phone calls on the Smartphone (and AI Box PBAP contacts) so that:
 * - Saved contacts display their Contact Name + Phone Number.
 * - Non-contacts (strangers) display their raw Phone Number cleanly.
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
        "電話",
        "語音通話",
        "藍牙電話",
        "藍牙 HFP",
        "未知號碼",
        "私人號碼",
        "Unknown",
        "Incoming call",
        "Phone call",
        "Phone"
    )

    fun isPureGenericPlaceholder(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return true
        return GENERIC_CALLER_LABELS.any { it.equals(t, ignoreCase = true) }
    }

    fun isGenericOrNumberOnly(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return true
        if (isPureGenericPlaceholder(t)) return true
        val stripped = t.replace(Regex("[\\s+\\-().#*0-9]"), "")
        return stripped.isEmpty()
    }

    fun extractPlausiblePhoneNumber(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        if (t.startsWith("tel:", ignoreCase = true)) {
            val rawSub = t.substring(4)
            val telNum = runCatching { java.net.URLDecoder.decode(rawSub, "UTF-8") }.getOrDefault(rawSub).trim()
            if (telNum.count { it.isDigit() } >= 3) return telNum
        }
        val match = Regex("(\\+?\\d[\\d\\s\\-()]{4,}\\d)").find(t)
        return match?.value?.trim().orEmpty()
    }

    private fun buildNumberLookupVariants(rawNumber: String): List<String> {
        val cleaned = rawNumber.replace(Regex("[^+0-9*#]"), "")
        if (cleaned.length < 3) return emptyList()
        val variants = LinkedHashSet<String>()
        variants.add(cleaned)
        // Taiwan +886 <-> 09xx normalization so both +886912345678 and 0912345678 match Contacts!
        if (cleaned.startsWith("+886") && cleaned.length >= 12) {
            variants.add("0" + cleaned.removePrefix("+886"))
        } else if (cleaned.startsWith("886") && cleaned.length >= 11) {
            variants.add("0" + cleaned.removePrefix("886"))
        } else if (cleaned.startsWith("09") && cleaned.length == 10) {
            variants.add("+886" + cleaned.drop(1))
        }
        return variants.toList()
    }

    fun resolveDisplayName(context: Context, rawNumberOrText: String?): String? {
        val candidate = rawNumberOrText?.trim().orEmpty()
        if (candidate.isEmpty()) return null

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null

        val baseNum = if (isGenericOrNumberOnly(candidate)) {
            candidate
        } else {
            extractPlausiblePhoneNumber(candidate)
        }
        val variants = buildNumberLookupVariants(baseNum)
        if (variants.isEmpty()) return null

        for (phoneToLookup in variants) {
            val found = runCatching {
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
            if (!found.isNullOrBlank()) return found
        }

        // Secondary fallback: CommonDataKinds.Phone normalized match for stubborn OEM contact providers
        val tailDigits = variants.first().filter { it.isDigit() }.takeLast(8)
        if (tailDigits.length >= 7) {
            val fallbackName = runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    arrayOf("%${tailDigits.takeLast(6)}%"),
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        val dbNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().filter { it.isDigit() } else ""
                        if (dbNum.endsWith(tailDigits)) {
                            val nm = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim() else null
                            if (!nm.isNullOrBlank()) return@use nm
                        }
                    }
                    null
                }
            }.getOrNull()
            if (!fallbackName.isNullOrBlank()) return fallbackName
        }

        return null
    }

    /**
     * Fallback for Direct APK when READ_CALL_LOG is granted and OEM dialer delays EXTRA_INCOMING_NUMBER.
     */
    fun queryRecentCallLogCaller(context: Context, maxAgeMs: Long = 20_000L): ResolvedCallerInfo? {
        val hasCallLog = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCallLog) return null

        return runCatching {
            val minDate = System.currentTimeMillis() - maxAgeMs
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(minDate.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val contactName = cachedName.ifBlank { resolveDisplayName(context, num).orEmpty() }
                    val display = contactName.ifBlank { num }
                    if (display.isNotBlank()) ResolvedCallerInfo(display, num) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * Extracts text labels from custom OEM InCallUI RemoteViews (vivo OriginOS / Xiaomi / Samsung)
     * when Notification.EXTRA_TITLE / EXTRA_TEXT are left blank!
     */
    fun extractRemoteViewsStrings(vararg views: RemoteViews?): List<String> {
        val results = mutableListOf<String>()
        for (rv in views) {
            if (rv == null) continue
            runCatching {
                val actionsField = RemoteViews::class.java.getDeclaredField("mActions").apply {
                    isAccessible = true
                }
                val actions = actionsField.get(rv) as? Iterable<*> ?: return@runCatching
                for (act in actions) {
                    if (act == null) continue
                    runCatching {
                        val methodField = act.javaClass.getDeclaredField("methodName").apply { isAccessible = true }
                        val mName = methodField.get(act) as? String
                        if (mName == "setText" || mName == "setContentDescription") {
                            val valField = act.javaClass.getDeclaredField("value").apply { isAccessible = true }
                            val cs = valField.get(act)?.toString()?.trim().orEmpty()
                            if (cs.isNotBlank() && cs.length <= 80) {
                                results.add(cs)
                            }
                        }
                    }
                }
            }
        }
        return results.distinct()
    }
}

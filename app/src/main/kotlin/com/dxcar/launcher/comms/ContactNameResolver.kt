package com.dxcar.launcher.comms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
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
     * Formats Taiwan/international phone numbers cleanly for Car HUD readability
     * (e.g. "+886912345678" / "0912345678" -> "0912-345-678").
     */
    fun formatPhoneNumberDisplay(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val digits = t.filter { it.isDigit() || it == '+' }
        val localTw = when {
            digits.startsWith("+8869") && digits.length == 13 -> "0" + digits.removePrefix("+886")
            digits.startsWith("8869") && digits.length == 12 -> "0" + digits.removePrefix("886")
            else -> digits
        }
        if (localTw.startsWith("09") && localTw.length == 10 && localTw.all { it.isDigit() }) {
            return "${localTw.substring(0, 4)}-${localTw.substring(4, 7)}-${localTw.substring(7, 10)}"
        }
        return t
    }

    /**
     * Collects all readable CharSequence / String values inside a Notification's extras Bundle,
     * catching OEM-specific caller name/number extras on vivo OriginOS, Xiaomi HyperOS, Samsung OneUI.
     */
    fun extractAllStringsFromExtras(extras: Bundle?): List<String> {
        if (extras == null || extras.isEmpty) return emptyList()
        val out = LinkedHashSet<String>()
        runCatching {
            for (key in extras.keySet()) {
                val keyLow = key.lowercase()
                if (keyLow.contains("icon") || keyLow.contains("bitmap") || keyLow.contains("template") ||
                    keyLow.contains("channel") || keyLow.contains("sort") || keyLow.contains("group")
                ) continue
                @Suppress("DEPRECATION")
                val v = extras.get(key)
                when (v) {
                    is CharSequence -> {
                        val s = v.toString().trim()
                        if (s.isNotBlank() && s.length <= 96) out.add(s)
                    }
                    is Array<*> -> {
                        v.forEach { el ->
                            if (el is CharSequence) {
                                val s = el.toString().trim()
                                if (s.isNotBlank() && s.length <= 96) out.add(s)
                            }
                        }
                    }
                }
            }
        }
        return out.toList()
    }

    /**
     * Extracts text labels from custom OEM InCallUI RemoteViews (vivo OriginOS / Xiaomi / Samsung)
     * when Notification.EXTRA_TITLE / EXTRA_TEXT are left blank or generic!
     * Uses public SDK RemoteViews inflation first (works reliably on Android 14/15/16 where mActions reflection is blocked),
     * then falls back to reflection on older devices.
     */
    fun extractRemoteViewsStrings(
        context: Context? = null,
        sourcePackage: String? = null,
        vararg views: RemoteViews?
    ): List<String> {
        val results = LinkedHashSet<String>()
        for (rv in views) {
            if (rv == null) continue

            // Strategy 1: Public SDK inflation into an offscreen FrameLayout (Android 14/15/16 safe!)
            if (context != null) {
                runCatching {
                    val pkgCtx = if (!sourcePackage.isNullOrBlank()) {
                        runCatching {
                            context.createPackageContext(sourcePackage, Context.CONTEXT_IGNORE_SECURITY)
                        }.getOrDefault(context)
                    } else {
                        context
                    }
                    val container = FrameLayout(pkgCtx)
                    val inflated = rv.apply(pkgCtx, container)
                    collectTextFromViewTree(inflated, results)
                }
            }

            // Strategy 2: Legacy reflection fallback for Android 9-13 custom RemoteViews
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
        return results.toList()
    }

    private fun collectTextFromViewTree(view: View?, out: MutableSet<String>) {
        if (view == null) return
        if (view is TextView) {
            val txt = view.text?.toString()?.trim().orEmpty()
            if (txt.isNotBlank() && txt.length <= 80) {
                out.add(txt)
            }
        }
        val desc = view.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotBlank() && desc.length <= 80) {
            out.add(desc)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectTextFromViewTree(view.getChildAt(i), out)
            }
        }
    }

    /**
     * Searches phone contacts by display name for Car Voice -> Phone Dialing ("打電話給王小明").
     * Returns (matchedDisplayName to phoneNumber) or null if not found.
     */
    fun findPhoneNumberByContactName(context: Context, queryName: String): Pair<String, String>? {
        val raw = queryName.trim()
        if (raw.isEmpty()) return null

        // If user spoke a direct phone number (e.g. "0912345678"), return immediately without needing contacts
        val digitsOnly = raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val nonPhoneChars = raw.replace(Regex("[\\s+\\-().#*0-9]"), "")
        if (nonPhoneChars.isEmpty() && digitsOnly.count { it.isDigit() } >= 3) {
            return raw to digitsOnly
        }

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null

        val normalizedQuery = raw.lowercase().replace(Regex("\\s+"), "")
        return runCatching {
            var bestExact: Pair<String, String>? = null
            var bestPartial: Pair<String, String>? = null
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    if (name.isBlank() || num.isBlank()) continue
                    val normName = name.lowercase().replace(Regex("\\s+"), "")
                    if (normName == normalizedQuery) {
                        bestExact = name to num
                        break
                    } else if (bestPartial == null && (normName.contains(normalizedQuery) || normalizedQuery.contains(normName))) {
                        bestPartial = name to num
                    }
                }
            }
            bestExact ?: bestPartial
        }.getOrNull()
    }
}

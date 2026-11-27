package com.dxcar.roothelper

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RootHelperConfig(
    val planBEnabled: Boolean = true,
    val dockPinnedRightShift: Boolean = true,
    val ui1NavPackage: String = "com.google.android.apps.maps",
    val ui1RightPackage: String = "com.google.android.youtube",
    val ui1SplitRatio: Float = 0.70f,
    val ui2LeftPackage: String = "com.google.android.apps.maps",
    val ui2RightPackage: String = "com.google.android.youtube",
    val ui2SplitRatio: Float = 0.50f,
    val force60HzEnabled: Boolean = false,
    val otaInterceptedPackage: String = "",
    val otaInterceptActive: Boolean = false
)

object RootHelperConfigStore {
    private const val PREF_NAME = "dxcar_root_helper_prefs"
    private const val KEY_JSON = "config_json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context): RootHelperConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null) ?: return RootHelperConfig()
        return runCatching { json.decodeFromString<RootHelperConfig>(raw) }
            .getOrDefault(RootHelperConfig())
    }

    fun save(context: Context, config: RootHelperConfig) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_JSON, json.encodeToString(config)).apply()
    }

    fun toJson(config: RootHelperConfig): String = json.encodeToString(config)
}

class RootHelperConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val cfg = RootHelperConfigStore.load(ctx)
        val cursor = MatrixCursor(arrayOf("config_json"))
        cursor.addRow(arrayOf(RootHelperConfigStore.toJson(cfg)))
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.dxcar.roothelper.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
        val ctx = context ?: return null
        return PlanBWindowEngine.dispatchFromProvider(ctx, method, extras)
    }
}

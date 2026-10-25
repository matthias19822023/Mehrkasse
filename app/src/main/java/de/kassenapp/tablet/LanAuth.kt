package de.kassenapp.tablet

import android.content.Context
import java.security.SecureRandom

/** Shared-secret pairing for trusted event Wi-Fi. This is authentication, not encryption. */
object LanAuth {
    private const val PREFS = "kassen_app_settings"
    private const val KEY = "lan_pairing_key"
    fun getOrCreateMasterKey(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val generated = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        prefs.edit().putString(KEY, generated).apply()
        return generated
    }
    fun getKey(context: Context): String = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    fun setKey(context: Context, key: String) { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, key.trim()).apply() }
}

package kr.re.kitech.nascam

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("nascam", Context.MODE_PRIVATE)

    var url: String get() = p.getString("url", "") ?: ""; set(v) = p.edit().putString("url", v.trimEnd('/')).apply()
    var folder: String get() = p.getString("folder", "photo/NasCam") ?: ""; set(v) = p.edit().putString("folder", v.trim('/')).apply()
    var user: String get() = p.getString("user", "") ?: ""; set(v) = p.edit().putString("user", v).apply()
    var pass: String get() = p.getString("pass", "") ?: ""; set(v) = p.edit().putString("pass", v).apply()
    var dateFolder: Boolean get() = p.getBoolean("dateFolder", true); set(v) = p.edit().putBoolean("dateFolder", v).apply()
    var wifiOnly: Boolean get() = p.getBoolean("wifiOnly", true); set(v) = p.edit().putBoolean("wifiOnly", v).apply()
    var deleteAfter: Boolean get() = p.getBoolean("deleteAfter", false); set(v) = p.edit().putBoolean("deleteAfter", v).apply()

    var lastError: String get() = p.getString("lastError", "") ?: ""; set(v) = p.edit().putString("lastError", v).apply()

    val isConfigured get() = url.isNotBlank() && user.isNotBlank()
}

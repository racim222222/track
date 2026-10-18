package com.example.tracker

import android.content.Context
import android.os.Build
import java.util.UUID

/** Config figée dans l'APK : l'utilisateur n'a rien à saisir. */
object Config {
    const val SERVER_URL = "https://track-a4jp.onrender.com"
    // Clé d'envoi uniquement (ne donne PAS accès à la carte admin). Extractible de l'APK par nature.
    const val DEVICE_KEY = "mvp-device-key"

    fun deviceId(c: Context): String {
        val p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        return p.getString("id", null) ?: (Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "") + "-" + UUID.randomUUID().toString().take(4))
            .also { p.edit().putString("id", it).apply() }
    }

    fun enabled(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putBoolean("enabled", v).apply()
}

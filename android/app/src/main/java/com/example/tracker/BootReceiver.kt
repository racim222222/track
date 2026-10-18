package com.example.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Relance le suivi au redémarrage du téléphone / après mise à jour de l'app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ok = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Config.enabled(c) && ok) {
            try { c.startForegroundService(Intent(c, LocationService::class.java)) } catch (_: Exception) {}
        }
    }
}

package com.example.tracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView

/** Aucun champ : à l'ouverture, enchaîne les autorisations puis démarre le suivi tout seul. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { status.text = LocationService.status; ui.postDelayed(this, 1000) }
    }
    private var askedFine = false
    private var askedBg = false
    private var askedBattery = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        status = TextView(this).apply { textSize = 18f; setPadding(48, 96, 48, 48) }
        setContentView(status)
    }

    override fun onResume() { super.onResume(); ui.post(refresh); proceed() }
    override fun onPause() { super.onPause(); ui.removeCallbacks(refresh) }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun proceed() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (askedFine) { LocationService.status = "Localisation refusée : Réglages > Applis > Tracker > Autorisations"; return }
            askedFine = true
            val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(need.toTypedArray(), 1)
            return
        }
        // Dès que la position est autorisée, le suivi démarre (l'appareil apparaît sur la carte).
        Config.setEnabled(this, true)
        try { startForegroundService(Intent(this, LocationService::class.java)) } catch (e: Exception) { LocationService.status = "Démarrage impossible : ${e.message}" }

        // « Toujours autoriser » : Android impose une demande séparée (ouvre l'écran de réglages dès Android 11).
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) && !askedBg) {
            askedBg = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
            return
        }
        // Évite que le système tue le service en arrière-plan.
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !askedBattery) {
            askedBattery = true
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
        }
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, res: IntArray) = proceed()
}

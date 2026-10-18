package com.example.tracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var token: EditText
    private lateinit var devId: EditText
    private lateinit var status: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { status.text = LocationService.status; ui.postDelayed(this, 1000) }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        fun field(hint: String, v: String) = EditText(this).apply { this.hint = hint; setText(v); setSingleLine() }
        url = field("URL serveur (http://IP:3000)", p.getString("url", "http://10.0.2.2:3000")!!)
        token = field("Token", p.getString("token", "dev")!!)
        devId = field("Id appareil", p.getString("id", null) ?: UUID.randomUUID().toString().take(8))
        status = TextView(this).apply { textSize = 16f }
        val start = Button(this).apply { text = "Démarrer"; setOnClickListener { ensurePermsThenStart() } }
        val stop = Button(this).apply { text = "Arrêter"; setOnClickListener { stopService(Intent(this@MainActivity, LocationService::class.java)) } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32)
            listOf<android.view.View>(url, token, devId, start, stop, status).forEach { addView(it) }
        })
    }

    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(refresh) }

    private fun ensurePermsThenStart() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) start() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, res: IntArray) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) start()
        else LocationService.status = "Permission localisation refusée"
    }

    private fun start() {
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("url", url.text.toString().trim()).putString("token", token.text.toString().trim())
            .putString("id", devId.text.toString().trim()).apply()
        // Doit être lancé depuis l'app au premier plan (Android 12+ interdit le départ d'un FGS location en arrière-plan)
        startForegroundService(Intent(this, LocationService::class.java))
    }
}

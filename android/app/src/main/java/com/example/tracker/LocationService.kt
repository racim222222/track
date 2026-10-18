package com.example.tracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Service foreground : seul moyen fiable de recevoir le GPS écran éteint / app en arrière-plan. */
class LocationService : Service(), LocationListener {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var lm: LocationManager
    private lateinit var url: String
    private lateinit var token: String
    private lateinit var deviceId: String

    override fun onBind(i: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        url = p.getString("url", "")!!.trimEnd('/') + "/api/location"
        token = p.getString("token", "")!!
        deviceId = p.getString("id", "")!!

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("loc", "Partage de position", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "loc")
            .setContentTitle("Position partagée")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (lm.isProviderEnabled(prov)) lm.requestLocationUpdates(prov, 5000L, 0f, this)
        }
        status = "En attente d'un fix GPS…"
        return START_STICKY
    }

    override fun onLocationChanged(l: Location) {
        io.execute { send(l) }
    }

    private fun send(l: Location) {
        val body = JSONObject()
            .put("id", deviceId).put("lat", l.latitude).put("lng", l.longitude)
            .put("acc", l.accuracy).put("speed", l.speed).put("ts", l.time).toString()
        status = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.outputStream.use { it.write(body.toByteArray()) }
            "HTTP ${c.responseCode} · ${"%.5f".format(l.latitude)}, ${"%.5f".format(l.longitude)} (±${l.accuracy.toInt()}m, ${l.provider})"
        } catch (e: Exception) {
            "Erreur réseau : ${e.message}"
        }
    }

    override fun onDestroy() {
        lm.removeUpdates(this)
        io.shutdown()
        status = "Arrêté"
    }

    companion object {
        @Volatile var status = "Arrêté"
    }
}

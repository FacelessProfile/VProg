package com.UwU.students

import android.Manifest
import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.kotlinroomdatabase.data.ZmqSockets
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs


class LocationForeground : Service() {

    private val LOG_TAG = "LOCATION_SERVICE"
    private val UPDATE_INTERVAL = 5000L
    private val MIN_DISTANCE_CHANGE = 1.0

    private var lastLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val zmqSockets = ZmqSockets("tcp://37.194.49.70:5555")

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundService()
        startLocationUpdates()
        Log.d(LOG_TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "location_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking location every 5 seconds")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    @androidx.annotation.RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        scheduler.scheduleWithFixedDelay(
             { getCurrentLocation() },
            0,
            UPDATE_INTERVAL,
            TimeUnit.MILLISECONDS
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                val location = task.result
                if (location != null) {
                    saveLocation(location)
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Location error: ${e.message}")
        }
    }

    private fun hasLocationChanged(newLocation: Location): Boolean {
        val last = lastLocation ?: return true
        return last.distanceTo(newLocation) >= MIN_DISTANCE_CHANGE ||
                abs(newLocation.altitude - last.altitude) >= MIN_DISTANCE_CHANGE
    }

    @OptIn(InternalSerializationApi::class)
    private fun saveLocation(location: Location) {
        try {
            if (!hasLocationChanged(location)) {
                return
            }

            val timestamp = System.currentTimeMillis()

            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "locations.txt"
            )
            file.parentFile?.mkdirs()

            val data = "Lat: ${location.latitude}, Lon: ${location.longitude}, Alt: ${location.altitude}, Time: $timestamp\n"
            FileWriter(file, true).use { it.write(data) }

            val payload = LocationPayload(
                operation = "location",
                lat = location.latitude,
                lon = location.longitude,
                alt = location.altitude,
                timestamp = timestamp
            )

            val json = Json.encodeToString(
                LocationPayload::class.serializer(),
                payload
            )

            scope.launch {
                val response = zmqSockets.sendData(json)
                Log.d("ZMQ_RESPONSE", response)
            }

            lastLocation = location

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Save/send error: ${e.message}")
        }
    }

    override fun onDestroy() {
        scheduler.shutdown()
        zmqSockets.close()
        super.onDestroy()
    }
}

package com.UwU.students

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.kotlinroomdatabase.data.ZmqSockets
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import java.io.FileWriter

@Serializable
data class LocationPayload(
    val operation: String,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val timestamp: Long
)

class LocationActivity : AppCompatActivity() {

    val LOG_TAG: String = "LOCATION_ACTIVITY"
    private lateinit var bBackToMain: Button

    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
    }

    private lateinit var myFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var tvLat: TextView
    private lateinit var tvLon: TextView
    private lateinit var tvAlt: TextView
    private lateinit var tvUpdate: TextView

    private val zmqSockets = ZmqSockets("tcp://37.194.49.70:5555")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_location)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bBackToMain = findViewById(R.id.back_to_main)
        myFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        tvLat = findViewById(R.id.tv_lat)
        tvLon = findViewById(R.id.tv_lon)
        tvAlt = findViewById(R.id.tv_alt)
        tvUpdate = findViewById(R.id.tv_last_update)

        if (checkPermissions()) {
            startLocationService()
        }
    }

    private fun startLocationService() {
        try {
            val serviceIntent = Intent(this, LocationForeground::class.java)
            startForegroundService(serviceIntent)
            Log.d(LOG_TAG, "LocationForeground service started")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error starting LocationForeground: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        bBackToMain.setOnClickListener {
            startActivity(Intent(this, HubActivity::class.java))
        }
        getCurrentLocation()
    }

    @OptIn(InternalSerializationApi::class)
    private fun saveLocationToFile(location: Location) {
        try {
            val timestamp = System.currentTimeMillis()

            val data = "Lat: ${location.latitude}, Lon: ${location.longitude}, Alt: ${location.altitude}, Time: $timestamp\n"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "locations.txt"
            )
            file.parentFile?.mkdirs()
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

            lifecycleScope.launch {
                val response = zmqSockets.sendData(json)
                Log.d("ZMQ_RESPONSE", response)
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error saving or sending: ${e.message}")
        }
    }

    private fun getCurrentLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                if (
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions()
                    return
                }

                myFusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location = task.result
                    if (location == null) {
                        Toast.makeText(applicationContext, "problems with signal", Toast.LENGTH_SHORT).show()
                    } else {
                        saveLocationToFile(location)
                        tvLat.text = "LAT: ${location.latitude}"
                        tvLon.text = "LON: ${location.longitude}"
                        tvAlt.text = "ALT: ${location.altitude}"
                        tvUpdate.text = "UPDATED: ${System.currentTimeMillis()}"
                    }
                }
            } else {
                Toast.makeText(applicationContext, "Enable location in settings", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        } else {
            tvLat.text = "Permission is not granted"
            tvLon.text = "Permission is not granted"
            tvAlt.text = "Permission is not granted"
            tvUpdate.text = "Permission is not granted"
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_ACCESS_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationService()
            getCurrentLocation()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onDestroy() {
        super.onDestroy()
        zmqSockets.close()
        stopService(Intent(this, LocationForeground::class.java))
    }
}

package com.UwU.students

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class LocationActivity : AppCompatActivity() {

    private val LOG_TAG: String = "LOCATION_ACTIVITY"
    private lateinit var bBackToMain: Button

    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
    }

    private lateinit var myFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var tvLat: TextView
    private lateinit var tvLon: TextView
    private lateinit var tvAlt: TextView
    private lateinit var tvUpdate: TextView

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

        // При запуске проверяем разрешения и стартуем ФОНОВЫЙ сервис
        if (checkPermissions()) {
            startLocationService()
        } else {
            requestPermissions()
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
            finish() // Просто закрываем текущую активити, чтобы вернуться назад
        }
        updateUI()
    }

    private fun updateUI() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                myFusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location = task.result
                    if (location != null) {
                        tvLat.text = "LAT: ${location.latitude}"
                        tvLon.text = "LON: ${location.longitude}"
                        tvAlt.text = "ALT: ${location.altitude}"
                        tvUpdate.text = "UPDATED: ${System.currentTimeMillis()}"
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE // Нужно для уровня сигнала в сервисе
            ),
            PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_ACCESS_LOCATION &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            startLocationService()
            updateUI()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}
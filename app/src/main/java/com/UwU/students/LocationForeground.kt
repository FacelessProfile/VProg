package com.UwU.students

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.telephony.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocationForeground : Service() {
    private val LOG_TAG = "LOCATION_SERVICE"
    private val UPDATE_INTERVAL = 5000L
    private val ramBuffer = mutableListOf<String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scheduler = Executors.newScheduledThreadPool(1)
    private lateinit var zmqHandler: ZmqHandler

    companion object {
        val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()

        fun checkAndRequestPermissions(activity: Activity, requestCode: Int) {
            val missing = REQUIRED_PERMISSIONS.filter {
                ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        zmqHandler = ZmqHandler("tcp://192.168.0.19:20077")
        startForeground(1, createNotification())
        scheduler.scheduleWithFixedDelay({
            try { getCurrentLocation() } catch (e: Exception) { Log.e(LOG_TAG, "${e.message}") }
        }, 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS)
    }

    private fun createNotification(): Notification {
        val chanId = "loc_chan"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(chanId, "Трекер", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, chanId)
            .setContentTitle("MY PROJECT")
            .setContentText("Идёт сбор данных о локации")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val data = "${it.latitude};${it.longitude};${getSignalStrength()};${System.currentTimeMillis() / 1000}"
                processData(data)
            }
        }
    }

    private fun processData(newData: String) {
        synchronized(ramBuffer) { ramBuffer.add(newData) }
        scope.launch {
            val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!docDir.exists()) docDir.mkdirs()
            val file = File(docDir, "location.txt")

            val currentRam = synchronized(ramBuffer) {
                val copy = ramBuffer.toList()
                ramBuffer.clear()
                copy
            }

            val diskData = if (file.exists()) file.readLines() else emptyList()
            val totalPayload = diskData + currentRam
            if (totalPayload.isEmpty()) return@launch

            var success = true
            val sentCount = mutableListOf<String>()

            for (line in totalPayload) {
                if (zmqHandler.sendData(line) == "ACK") {
                    sentCount.add(line)
                } else {
                    success = false
                    break
                }
            }

            if (success) {
                if (file.exists()) file.delete()
            } else {
                val unsent = totalPayload.drop(sentCount.size)
                file.writeText(unsent.joinToString("\n") + "\n")
            }
        }
    }

    private fun getSignalStrength(): Int {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return -1
            val info = tm.allCellInfo?.firstOrNull()
            when (info) {
                is CellInfoLte -> info.cellSignalStrength.dbm
                is CellInfoGsm -> info.cellSignalStrength.dbm
                is CellInfoWcdma -> info.cellSignalStrength.dbm
                else -> -1
            }
        } catch (e: Exception) { -1 }
    }

    override fun onDestroy() {
        scheduler.shutdown()
        zmqHandler.close()
        scope.cancel()
        super.onDestroy()
    }

    private class ZmqHandler(private val address: String) {
        private var context: ZContext = ZContext()
        private var socket: ZMQ.Socket = context.createSocket(ZMQ.REQ)

        init {
            connect()
        }

        private fun connect() {
            socket.receiveTimeOut = 3000
            socket.sendTimeOut = 3000
            socket.linger = 0
            socket.connect(address)
        }

        private fun reconnect() {
            try {
                context.destroySocket(socket)
                socket = context.createSocket(ZMQ.REQ)
                connect()
            } catch (e: Exception) {
                Log.e("ZMQ", "Реконнект провален: ${e.message}")
            }
        }

        fun sendData(data: String): String {
            return try {
                if (!socket.send(data)) throw Exception("Не отправилось")
                val response = socket.recvStr()
                response ?: throw Exception("Не отвечает")
            } catch (e: Exception) {
                reconnect()
                "ERROR"
            }
        }

        fun close() {
            context.destroy()
        }
    }
}
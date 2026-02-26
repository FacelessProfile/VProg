package com.UwU.students

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }.toTypedArray()

        fun checkAndRequestPermissions(activity: Activity, requestCode: Int) {
            val missing = REQUIRED_PERMISSIONS.filter {
                ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        zmqHandler = ZmqHandler("tcp://192.168.0.55:20077")
        scheduler.scheduleWithFixedDelay({
            try { getCurrentLocation() } catch (e: Exception) { Log.e(LOG_TAG, "Ошибка: ${e.message}") }
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
            .setContentText("Идет сбор данных (30с буфер)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
            location?.let {
                val data = "${it.latitude};${it.longitude};${getSignalStrength()};${System.currentTimeMillis() / 1000}"
                processData(data)
            }
        }
    }

    private fun processData(newData: String) {
        synchronized(ramBuffer) { ramBuffer.add(newData) }

        // Ждем 6 записей (6 * 5с = 30 секунд)
        if (ramBuffer.size < 6) return

        scope.launch {
            val currentRam = synchronized(ramBuffer) {
                val copy = ramBuffer.toList()
                ramBuffer.clear()
                copy
            }

            val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!docDir.exists()) docDir.mkdirs()
            val file = File(docDir, "location.txt")

            // Читаем кеш БЕЗОПАСНО
            val diskData = try {
                if (file.exists()) file.readLines() else emptyList()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Ошибка доступа к файлу: ${e.message}")
                emptyList()
            }

            val totalPayload = diskData + currentRam
            if (file.exists()) file.delete()

            Log.d(LOG_TAG, "30с прошло. Пытаюсь отправить ${totalPayload.size} строк...")

            val unsent = mutableListOf<String>()
            var isError = false

            for (line in totalPayload) {
                if (!isError && zmqHandler.sendData(line) == "ACK") {
                    Log.d(LOG_TAG, "Успешно отправлено: $line")
                } else {
                    isError = true
                    unsent.add(line)
                }
            }

            if (unsent.isNotEmpty()) {
                try {
                    Log.w(LOG_TAG, "Кэширую ${unsent.size} строк в Documents/location.txt")
                    file.appendText(unsent.joinToString("\n") + "\n")
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "КРИТИЧЕСКАЯ ОШИБКА: Не удалось записать файл! Проверь разрешения в настройках.")
                }
            } else {
                Log.d(LOG_TAG, "Все данные успешно ушли")
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
        init { connect() }
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
            } catch (e: Exception) { Log.e("ZMQ", "Реконнект...") }
        }
        fun sendData(data: String): String {
            return try {
                if (!socket.send(data)) throw Exception()
                socket.recvStr() ?: "ERROR"
            } catch (e: Exception) {
                reconnect()
                "ERROR"
            }
        }
        fun close() { try { context.destroy() } catch (e: Exception) {} }
    }
}
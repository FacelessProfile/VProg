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
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
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
    private lateinit var telephonyManager: TelephonyManager
    private var activeFlags = "1111"

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
        zmqHandler = ZmqHandler("tcp://37.194.49.70:20077")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        scheduler.scheduleWithFixedDelay({
            try { getCurrentLocation() } catch (e: Exception) { Log.e(LOG_TAG, "Error: ${e.message}") }
        }, 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS)
    }

    private fun createNotification(): Notification {
        val chanId = "loc_chan"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(chanId, "Tracker", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, chanId)
            .setContentTitle("MY PROJECT")
            .setContentText("Collecting data...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
            location?.let {
                val locPart = if (activeFlags.getOrElse(0) { '1' } == '1') {
                    "${it.latitude};${it.longitude};${it.altitude};${System.currentTimeMillis() / 1000};${it.accuracy}"
                } else "SKIP;SKIP;SKIP;SKIP;SKIP"

                val cellPart = getCellData(telephonyManager)
                processData("$locPart;$cellPart")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun getCellData(telephonyManager: TelephonyManager): String {
        val info = telephonyManager.allCellInfo?.find { it.isRegistered } ?: return "NONE" + ";0".repeat(14)
        return when (info) {
            is CellInfoLte -> {
                if (activeFlags.getOrElse(1) { '1' } == '0') "LTE_OFF" + ";0".repeat(14)
                else {
                    val id = info.cellIdentity
                    val sig = info.cellSignalStrength
                    "LTE;${id.bands?.firstOrNull() ?: 0};${id.ci};${id.earfcn};${id.mccString};${id.mncString};${id.pci};${id.tac};" +
                            "${sig.asuLevel};${sig.cqi};${sig.rsrp};${sig.rsrq};${sig.rssi};${sig.rssnr};${sig.timingAdvance}"
                }
            }
            is CellInfoNr -> {
                if (activeFlags.getOrElse(3) { '1' } == '0') "NR_OFF" + ";0".repeat(10)
                else {
                    val id = info.cellIdentity as CellIdentityNr
                    val sig = info.cellSignalStrength as CellSignalStrengthNr
                    "NR;${id.bands.firstOrNull() ?: 0};${id.nci};${id.pci};${id.nrarfcn};${id.tac};${id.mccString};${id.mncString};" +
                            "${sig.ssRsrp};${sig.ssRsrq};${sig.ssSinr};0"
                }
            }
            is CellInfoGsm -> {
                if (activeFlags.getOrElse(2) { '1' } == '0') "GSM_OFF" + ";0".repeat(9)
                else {
                    val id = info.cellIdentity
                    val sig = info.cellSignalStrength
                    "GSM;${id.cid};${id.bsic};${id.arfcn};${id.lac};${id.mccString};${id.mncString};${id.psc};" +
                            "${sig.dbm};${sig.rssi};${sig.timingAdvance}"
                }
            }
            else -> "UNKNOWN" + ";0".repeat(14)
        }
    }

    private fun processData(newData: String) {
        synchronized(ramBuffer) { ramBuffer.add(newData) }
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

            val diskData = try {
                if (file.exists()) file.readLines() else emptyList()
            } catch (e: Exception) { emptyList() }

            val totalPayload = diskData + currentRam
            if (file.exists()) file.delete()

            val unsent = mutableListOf<String>()
            var isError = false

            for (line in totalPayload) {
                if (!isError) {
                    val response = zmqHandler.sendData(line)
                    if (response != "ERROR") {
                        if (response.length >= 4 && response.all { it == '0' || it == '1' }) {
                            activeFlags = response
                        }
                    } else {
                        isError = true
                        unsent.add(line)
                    }
                } else {
                    unsent.add(line)
                }
            }

            if (unsent.isNotEmpty()) {
                try {
                    file.appendText(unsent.joinToString("\n") + "\n")
                } catch (e: Exception) { }
            }
        }
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
            socket.receiveTimeOut = 2000
            socket.sendTimeOut = 2000
            socket.linger = 0
            socket.connect(address)
        }
        private fun reconnect() {
            try {
                context.destroySocket(socket)
                socket = context.createSocket(ZMQ.REQ)
                connect()
            } catch (e: Exception) { }
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
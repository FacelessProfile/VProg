package com.UwU.students

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.telephony.CellInfoLte
import android.telephony.CellInfoGsm
import android.telephony.CellInfoNr
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.annotation.RequiresPermission

class CellActivity : AppCompatActivity() {
    val TAG = "Telephony" // DEBUG TODO
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_cell)

        resultTextView = findViewById(R.id.result)
        resultTextView.movementMethod = ScrollingMovementMethod()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkPermissionsAndGetCellInfo()
    }

    private fun checkPermissionsAndGetCellInfo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешений", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        } else {
            getCellInfo()
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCellInfo()
        } else {
            resultTextView.text = "Разрешения не получены"
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun getCellInfo() {
        try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val cellInfoList = telephonyManager.allCellInfo

            if (cellInfoList == null || cellInfoList.isEmpty()) {
                resultTextView.text = "Нет информации о сотах"
                return
            }

            val resultText = buildString {
                for ((index, cellInfo) in cellInfoList.withIndex()) {
                    append("\nСота ${index + 1}\n")
                    when (cellInfo) {
                        is CellInfoLte -> append(getLteInfo(cellInfo))
                        is CellInfoGsm -> append(getGsmInfo(cellInfo))
                        is CellInfoNr -> append(getNrInfo(cellInfo))
                        else -> append("Неизвестный тип соты\n")
                    }
                    append("\n---\n")
                }
            }

            resultTextView.text = resultText

        } catch (e: Exception) {
            resultTextView.text = "Ошибка: ${e.message}"
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCellInfo()
        }
    }

    private fun getLteInfo(cellInfo: CellInfoLte): String {
        return """
            1. CellInfoLte
                1. CellIdentityLte:
                - Band: ${cellInfo.cellIdentity.bands?.joinToString() ?: "N/A"}
                - CellIdentity: ${cellInfo.cellIdentity.ci}
                - EARFCN: ${cellInfo.cellIdentity.earfcn}
                - MCC: ${cellInfo.cellIdentity.mccString ?: "N/A"}
                - MNC: ${cellInfo.cellIdentity.mncString ?: "N/A"}
                - PCI: ${cellInfo.cellIdentity.pci}
                - TAC: ${cellInfo.cellIdentity.tac}
            
                2. CellSignalStrengthLte:
                - ASU Level: ${cellInfo.cellSignalStrength.asuLevel}
                - CQI: ${cellInfo.cellSignalStrength.cqi}
                - RSRP: ${cellInfo.cellSignalStrength.rsrp} dBm
                - RSRQ: ${cellInfo.cellSignalStrength.rsrq} dB
                - RSSI: ${cellInfo.cellSignalStrength.rssi} dBm
                - RSSNR: ${cellInfo.cellSignalStrength.rssnr} dB
                - Timing Advance: ${cellInfo.cellSignalStrength.timingAdvance}
                
                Active: ${cellInfo.isRegistered}
        """
    }

    private fun getGsmInfo(cellInfo: CellInfoGsm): String {
        return """
            2. CellInfoGsm
                1. CellIdentityGSM:
                    - CellIdentity: ${cellInfo.cellIdentity.cid}
                    - BSIC: ${cellInfo.cellIdentity.bsic}
                    - ARFCN: ${cellInfo.cellIdentity.arfcn}
                    - LAC: ${cellInfo.cellIdentity.lac}
                    - MCC: ${cellInfo.cellIdentity.mccString ?: "N/A"}
                    - MNC: ${cellInfo.cellIdentity.mncString ?: "N/A"}
                    - PSC: ${cellInfo.cellIdentity.psc}
            
                2. CellSignalStrengthGsm:
                    - Dbm: ${cellInfo.cellSignalStrength.dbm} dBm
                    - RSSI: ${cellInfo.cellSignalStrength.rssi} dBm
                    - Timing Advance: ${cellInfo.cellSignalStrength.timingAdvance}
                    
                Active: ${cellInfo.isRegistered}
        """
    }

    private fun getNrInfo(cellInfo: CellInfoNr): String {
        val celi = cellInfo.cellIdentity as CellIdentityNr
        val cels = cellInfo.cellSignalStrength as CellSignalStrengthNr

        return """
            3. CellInfoNr:
                1. CellIdentityNr:
                    - Band: ${celi.bands.firstOrNull() ?: "N/A"}
                    - NCI: ${celi.nci}
                    - PCI: ${celi.pci}
                    - Nrargcn: ${celi.nrarfcn}
                    - TAC: ${celi.tac}
                    - MCC: ${celi.mccString ?: "N/A"}
                    - MNC: ${celi.mncString ?: "N/A"}
            
                2. CellSignalStrengthNr:
                    - SS-RSRP: ${cels.ssRsrp} dBm
                    - SS-RSRQ: ${cels.ssRsrq} dB
                    - SS-SINR: ${cels.ssSinr} dB
                    
                Active: ${cellInfo.isRegistered}
        """
    }
}
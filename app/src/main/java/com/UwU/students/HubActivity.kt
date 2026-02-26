package com.UwU.students

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_hub)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val calc = findViewById<Button>(R.id.CalcTransferBTN)
        calc.setOnClickListener({
            val randomIntent = Intent(this, CalcActivity::class.java)
            startActivity(randomIntent)
        });


        val media = findViewById<Button>(R.id.MediaTransferBTN)
        media.setOnClickListener({
            val randomIntent = Intent(this, MediaActivity::class.java)
            startActivity(randomIntent)
        });


        val location = findViewById<Button>(R.id.LocationTransferBTN)
        location.setOnClickListener({
            val randomIntent = Intent(this, LocationActivity::class.java)
            startActivity(randomIntent)
        });

        val telephony = findViewById<Button>(R.id.TelTransferBTN)
        telephony.setOnClickListener({
            val randomIntent = Intent(this, CellActivity::class.java)
            startActivity(randomIntent)
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
        }


    }
    override fun onResume(){
        super.onResume()
    }
}
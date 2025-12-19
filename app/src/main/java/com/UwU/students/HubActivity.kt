package com.UwU.students

import android.content.Intent
import android.os.Bundle
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


    }
    override fun onResume(){
        super.onResume()
    }
}
package com.example.myapplication12345

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val usage = intent.getDoubleExtra("USAGE", 0.0)
        val carbonEmission = intent.getDoubleExtra("CARBON_EMISSION", 0.0)

        findViewById<TextView>(R.id.textViewResult).text =
            "사용량: %.2f kWh\n탄소 배출량: %.2f kg CO2".format(usage, carbonEmission)
    }
}
package com.example.myapplication12345.ui.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.example.myapplication12345.R

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)


        val usage = intent.getDoubleExtra("USAGE", 0.0)
        val carbonEmission = intent.getDoubleExtra("CARBON_EMISSION", 0.0)

        val resultTextView = findViewById<TextView>(R.id.textViewResult)
        resultTextView.text = """
            전기 사용량: ${String.format("%.1f", usage)} kWh
            예상 탄소 배출량: ${String.format("%.2f", carbonEmission)} kg CO₂
        """.trimIndent()
    }
}

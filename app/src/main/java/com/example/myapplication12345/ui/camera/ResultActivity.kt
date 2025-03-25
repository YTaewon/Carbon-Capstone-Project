package com.example.myapplication12345.ui.camera

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication12345.R
import com.example.myapplication12345.ServerManager

class ResultActivity : AppCompatActivity() {

    private lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // ScoreManager 초기화
        serverManager = ServerManager(this)

        val usage = intent.getDoubleExtra("USAGE", 0.0)
        val carbonEmission = intent.getDoubleExtra("CARBON_EMISSION", 0.0)
        val backButton: ImageView = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish() // 현재 액티비티 종료 -> 이전 화면으로 이동ㅇ
        }
        val resultTextView = findViewById<TextView>(R.id.textViewResult)
        resultTextView.text = """
            전기 사용량: ${String.format("%.1f", usage)} kWh
            예상 탄소 배출량: ${String.format("%.2f", carbonEmission)} kg CO₂
        """.trimIndent()

        // 탄소 배출량을 포인트로 변환
        val points = convertCarbonToPoints(carbonEmission)

        // 변환된 포인트를 사용자 점수에 추가
        serverManager.addScoreToCurrentUser(points) {
            Toast.makeText(this, "탄소 배출량 절감으로 ${points}점이 추가되었습니다!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 탄소 배출량을 포인트로 변환하는 함수
     * 탄소 배출량이 적을수록 더 많은 포인트를 받도록 설계
     *
     * @param carbonEmission 탄소 배출량(kg CO₂)
     * @return 변환된 포인트
     */
    private fun convertCarbonToPoints(carbonEmission: Double): Int {
        val baseEmission = 10.0 // 기준 탄소 배출량 (kg CO₂)
        val basePoints = 50    // 기준 포인트

        return if (carbonEmission <= baseEmission) {
            // 기준보다 적게 배출하면 더 많은 포인트 (최대 100점)
            val bonusPoints = ((baseEmission - carbonEmission) / baseEmission * 50).toInt()
            basePoints + bonusPoints
        } else {
            // 기준보다 많이 배출하면 적은 포인트 (최소 10점)
            val reducedPoints = ((carbonEmission - baseEmission) / baseEmission * 40).toInt()
            maxOf(basePoints - reducedPoints, 10)
        }
    }
}
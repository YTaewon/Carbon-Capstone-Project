package com.example.myapplication12345

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication12345.auth.IntroActivity
import com.example.myapplication12345.fragments.DailyFragment
import com.example.myapplication12345.fragments.MonthlyFragment
import com.example.myapplication12345.fragments.NowFragment
import com.example.myapplication12345.fragments.WeeklyFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class RankingActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var scoreManager: ScoreManager // ScoreManager 추가

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        scoreManager = ScoreManager(this) // ScoreManager 초기화
        setContentView(R.layout.activity_ranking)

        enableEdgeToEdge()
        setFrag(0)

        val btnNow = findViewById<Button>(R.id.btn_now)
        val btnDaily = findViewById<Button>(R.id.btn_daily)
        val btnWeekly = findViewById<Button>(R.id.btn_weekly)
        val btnMonthly = findViewById<Button>(R.id.btn_monthly)

        val buttons = listOf(btnNow, btnDaily, btnWeekly, btnMonthly)

        for (button in buttons) {
            button.setOnClickListener { clickedButton ->
                for (b in buttons) {
                    b.textSize = 16f
                }
                val clickedButtonView = findViewById<Button>(clickedButton.id)
                clickedButtonView.textSize = 20f
                when (button) {
                    btnNow -> setFrag(0)
                    btnDaily -> setFrag(1)
                    btnWeekly -> setFrag(2)
                    btnMonthly -> setFrag(3)
                }
            }
        }

        // 로그아웃 버튼 설정
        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, IntroActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Plus 버튼 설정
        findViewById<Button>(R.id.plusBtn).setOnClickListener {
            scoreManager.addScoreToCurrentUser(5) {
                // 점수 추가 완료 후 수행할 작업 (옵셔널)
                Toast.makeText(this, "점수 추가 완료!2", Toast.LENGTH_SHORT).show()
            }
        }

        // 시스템 바 Insets 설정
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setFrag(fragNum: Int) {
        val ft = supportFragmentManager.beginTransaction()
        when (fragNum) {
            0 -> ft.replace(R.id.main_frame, NowFragment()).commit()
            1 -> ft.replace(R.id.main_frame, DailyFragment()).commit()
            2 -> ft.replace(R.id.main_frame, WeeklyFragment()).commit()
            3 -> ft.replace(R.id.main_frame, MonthlyFragment()).commit()
        }
    }
}
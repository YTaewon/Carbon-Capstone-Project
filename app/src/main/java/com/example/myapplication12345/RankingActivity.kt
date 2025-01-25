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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.ktx.Firebase

class RankingActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
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
                // 모든 버튼의 textSize를 16sp로 초기화
                for (b in buttons) {
                    b.textSize = 16f
                }
                // 클릭된 버튼의 textSize를 20sp로 변경
                val clickedButtonView = findViewById<Button>(clickedButton.id)
                clickedButtonView.textSize = 20f
                // Fragment 전환
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
            addScoreToCurrentUser(5)
        }

        // 시스템 바 Insets 설정
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets


        }
    }

    private fun addScoreToCurrentUser(scoreToAdd: Int) {
        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid

        if (currentUserId != null) {
            val userRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUserId)

            // 현재 점수 가져오기
            userRef.child("score").get().addOnSuccessListener { dataSnapshot ->
                val currentScore = dataSnapshot.getValue(Int::class.java) ?: 0

                // 점수 추가 및 업데이트
                val newScore = currentScore + scoreToAdd
                val scoreData = mapOf(
                    "value" to scoreToAdd, // 추가된 점수 값
                    "timestamp" to ServerValue.TIMESTAMP // 타임스탬프 추가
                )

                userRef.child("scores").push().setValue(scoreData) // scores 노드에 새로운 점수 데이터 추가
                    .addOnSuccessListener {
                        userRef.child("score").setValue(newScore) // score 노드 업데이트
                            .addOnSuccessListener {
                                Toast.makeText(this, "점수가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { exception ->
                                Toast.makeText(this, "점수 추가 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "점수 추가 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setFrag(fragNum: Int) {
        val ft = supportFragmentManager.beginTransaction()
        when (fragNum) {
            0 -> {
                ft.replace(R.id.main_frame, NowFragment()).commit()
            }

            1 -> {
                ft.replace(R.id.main_frame, DailyFragment()).commit()
            }

            2 -> {
                ft.replace(R.id.main_frame, WeeklyFragment()).commit()
            }

            3 -> {
                ft.replace(R.id.main_frame, MonthlyFragment()).commit()
            }
        }
    }
}

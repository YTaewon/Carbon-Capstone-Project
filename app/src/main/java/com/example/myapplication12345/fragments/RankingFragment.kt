package com.example.myapplication12345.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.myapplication12345.R
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

class RankingFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.activity_ranking, container, false)
        auth = Firebase.auth


        setFrag(view, 0)

        // 탭 버튼들 설정
        val btnNow = view.findViewById<Button>(R.id.btn_now)
        val btnDaily = view.findViewById<Button>(R.id.btn_daily)
        val btnWeekly = view.findViewById<Button>(R.id.btn_weekly)
        val btnMonthly = view.findViewById<Button>(R.id.btn_monthly)
        val buttons = listOf(btnNow, btnDaily, btnWeekly, btnMonthly)

        for (button in buttons) {
            button.setOnClickListener { clickedButton ->
                // 모든 버튼의 textSize를 16sp로 초기화
                buttons.forEach { it.textSize = 16f }
                // 클릭된 버튼의 textSize를 20sp로 변경
                view.findViewById<Button>(clickedButton.id).textSize = 20f

                // 탭에 따라 프래그먼트 전환
                when (button) {
                    btnNow -> setFrag(view, 0)
                    btnDaily -> setFrag(view, 1)
                    btnWeekly -> setFrag(view, 2)
                    btnMonthly -> setFrag(view, 3)
                }
            }
        }

        // 로그아웃 버튼 설정
        view.findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), IntroActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Plus 버튼 설정 (점수 추가)
        view.findViewById<Button>(R.id.plusBtn).setOnClickListener {
            addScoreToCurrentUser(5)
        }

        // 시스템 바 Insets 설정
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        return view
    }

    private fun addScoreToCurrentUser(scoreToAdd: Int) {
        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid

        if (currentUserId != null) {
            val userRef =
                FirebaseDatabase.getInstance().reference.child("users").child(currentUserId)

            // 현재 점수 가져오기
            userRef.child("score").get().addOnSuccessListener { dataSnapshot ->
                val currentScore = dataSnapshot.getValue(Int::class.java) ?: 0
                val newScore = currentScore + scoreToAdd
                val scoreData = mapOf(
                    "value" to scoreToAdd, // 추가된 점수 값
                    "timestamp" to ServerValue.TIMESTAMP // 타임스탬프 추가
                )

                userRef.child("scores").push().setValue(scoreData)
                    .addOnSuccessListener {
                        userRef.child("score").setValue(newScore)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    requireContext(),
                                    "점수가 추가되었습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { exception ->
                                Toast.makeText(
                                    requireContext(),
                                    "점수 추가 실패: ${exception.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(
                            requireContext(),
                            "점수 추가 실패: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        } else {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 내부 FrameLayout(R.id.main_frame)에 탭에 따른 프래그먼트를 교체
    private fun setFrag(rootView: View, fragNum: Int) {
        val ft = childFragmentManager.beginTransaction()
        when (fragNum) {
            0 -> ft.replace(R.id.main_frame, NowFragment()).commit()
            1 -> ft.replace(R.id.main_frame, DailyFragment()).commit()
            2 -> ft.replace(R.id.main_frame, WeeklyFragment()).commit()
            3 -> ft.replace(R.id.main_frame, MonthlyFragment()).commit()
        }
    }
}

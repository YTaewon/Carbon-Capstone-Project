package com.example.myapplication12345.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.myapplication12345.R
import com.example.myapplication12345.ScoreManager
import com.example.myapplication12345.auth.IntroActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class RankingFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var scoreManager: ScoreManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_ranking, container, false) // activity_ranking -> fragment_ranking
        auth = Firebase.auth
        scoreManager = ScoreManager(requireContext())

        setFrag(0)

        // 탭 버튼 설정
        val btnNow = view.findViewById<Button>(R.id.btn_now)
        val btnDaily = view.findViewById<Button>(R.id.btn_daily)
        val btnWeekly = view.findViewById<Button>(R.id.btn_weekly)
        val btnMonthly = view.findViewById<Button>(R.id.btn_monthly)
        val buttons = listOf(btnNow, btnDaily, btnWeekly, btnMonthly)

        buttons.forEach { button ->
            button.setOnClickListener { clickedButton ->
                buttons.forEach { it.textSize = 16f }
                view.findViewById<Button>(clickedButton.id).textSize = 20f
                when (button) {
                    btnNow -> setFrag(0)
                    btnDaily -> setFrag(1)
                    btnWeekly -> setFrag(2)
                    btnMonthly -> setFrag(3)
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

        // Plus 버튼 설정
        view.findViewById<Button>(R.id.plusBtn).setOnClickListener {
            scoreManager.addScoreToCurrentUser(5)
        }

        // 시스템 바 Insets 설정
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        return view
    }

    private fun setFrag(fragNum: Int) {
        val ft = childFragmentManager.beginTransaction()
        when (fragNum) {
            0 -> ft.replace(R.id.main_frame, NowFragment()).commit()
            1 -> ft.replace(R.id.main_frame, DailyFragment()).commit()
            2 -> ft.replace(R.id.main_frame, WeeklyFragment()).commit()
            3 -> ft.replace(R.id.main_frame, MonthlyFragment()).commit()
        }
    }
}
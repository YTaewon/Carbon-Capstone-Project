package com.example.myapplication12345.ui.ranking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication12345.R
import com.example.myapplication12345.ScoreManager
import com.example.myapplication12345.ui.login.IntroActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import timber.log.Timber

class RankingFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var scoreManager: ScoreManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter

    // 시간 범위 상수
    companion object {
        const val TYPE_NOW = "now"
        const val TYPE_DAILY = "daily"
        const val TYPE_WEEKLY = "weekly"
        const val TYPE_MONTHLY = "monthly"

        private const val TWENTY_FOUR_HOURS_MILLIS = 24 * 60 * 60 * 1000L // 24시간
        private const val SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L   // 7일
        private const val THIRTY_DAYS_MILLIS = 30 * 24 * 60 * 60 * 1000L // 30일
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_ranking, container, false)
        auth = Firebase.auth
        scoreManager = ScoreManager(requireContext())

        // RecyclerView 초기화
        recyclerView = view.findViewById(R.id.rv_profile)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.visibility = View.VISIBLE

        // 초기 빈 어댑터 설정
        rankingAdapter = RankingAdapter(ArrayList())
        recyclerView.adapter = rankingAdapter

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
                    btnNow -> fetchRankingData(TYPE_NOW)
                    btnDaily -> fetchRankingData(TYPE_DAILY)
                    btnWeekly -> fetchRankingData(TYPE_WEEKLY)
                    btnMonthly -> fetchRankingData(TYPE_MONTHLY)
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

        // 초기 데이터 로드 (Now 탭 기본 설정)
        fetchRankingData(TYPE_NOW)
        btnNow.textSize = 20f // 초기 선택 상태 표시

        return view
    }

    private fun fetchRankingData(rankingType: String) {
        FirebaseDatabase.getInstance().reference.child("users").get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) {
                    Timber.w("RankingFragment", "Fragment not attached, skipping data update")
                    return@addOnSuccessListener
                }

                val profileList = arrayListOf<Profiles>()
                val currentTimeMillis = System.currentTimeMillis()
                val timeThreshold = when (rankingType) {
                    TYPE_DAILY -> TWENTY_FOUR_HOURS_MILLIS
                    TYPE_WEEKLY -> SEVEN_DAYS_MILLIS
                    TYPE_MONTHLY -> THIRTY_DAYS_MILLIS
                    TYPE_NOW -> Long.MAX_VALUE // Now는 전체 점수 사용
                    else -> Long.MAX_VALUE
                }

                for (userSnapshot in snapshot.children) {
                    val nickname = userSnapshot.child("nickname").value?.toString() ?: "Unknown"
                    var score = 0

                    if (rankingType == TYPE_NOW) {
                        // Now: 전체 점수 사용
                        score = userSnapshot.child("score").getValue(Int::class.java) ?: 0
                    } else {
                        // Daily, Weekly, Monthly: 타임스탬프 기반 필터링
                        val scoresSnapshot = userSnapshot.child("scores")
                        for (scoreSnapshot in scoresSnapshot.children) {
                            val scoreValue = scoreSnapshot.child("value").getValue(Int::class.java) ?: 0
                            val timestamp = scoreSnapshot.child("timestamp").getValue(Long::class.java) ?: 0
                            if (currentTimeMillis - timestamp <= timeThreshold) {
                                score += scoreValue
                            }
                        }
                    }

                    if (score > 0) { // 점수 0 제외
                        profileList.add(Profiles(R.drawable.user, nickname, score))
                    }
                }

                profileList.sortByDescending { it.score }
                updateTopThree(profileList)

                val remainingProfiles = if (profileList.size > 3) {
                    ArrayList(profileList.subList(3, profileList.size))
                } else {
                    arrayListOf()
                }

                rankingAdapter.updateData(remainingProfiles)
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Timber.e(e, "Failed to load ranking data for $rankingType")
                }
            }
    }

    private fun updateTopThree(profileList: ArrayList<Profiles>) {
        if (!isAdded || activity == null) {
            Timber.w("RankingFragment", "Fragment not attached to an activity, skipping update")
            return
        }

        val activityView = requireActivity().findViewById<View>(android.R.id.content)

        val ivProfile1 = activityView.findViewById<ImageView>(R.id.iv_profile1)
        val tvName1 = activityView.findViewById<TextView>(R.id.tv_name1)
        val tvScore1 = activityView.findViewById<TextView>(R.id.tv_score1)

        val ivProfile2 = activityView.findViewById<ImageView>(R.id.iv_profile2)
        val tvName2 = activityView.findViewById<TextView>(R.id.tv_name2)
        val tvScore2 = activityView.findViewById<TextView>(R.id.tv_score2)

        val ivProfile3 = activityView.findViewById<ImageView>(R.id.iv_profile3)
        val tvName3 = activityView.findViewById<TextView>(R.id.tv_name3)
        val tvScore3 = activityView.findViewById<TextView>(R.id.tv_score3)

        if (profileList.size >= 1) {
            val top1 = profileList[0]
            ivProfile1?.setImageResource(top1.profile)
            tvName1?.text = top1.name
            tvScore1?.text = top1.score.toString()
        } else {
            tvName1?.text = ""
            tvScore1?.text = "000"
        }

        if (profileList.size >= 2) {
            val top2 = profileList[1]
            ivProfile2?.setImageResource(top2.profile)
            tvName2?.text = top2.name
            tvScore2?.text = top2.score.toString()
        } else {
            tvName2?.text = ""
            tvScore2?.text = "000"
        }

        if (profileList.size >= 3) {
            val top3 = profileList[2]
            ivProfile3?.setImageResource(top3.profile)
            tvName3?.text = top3.name
            tvScore3?.text = top3.score.toString()
        } else {
            tvName3?.text = ""
            tvScore3?.text = "000"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView.adapter = null // 어댑터 해제
    }
}
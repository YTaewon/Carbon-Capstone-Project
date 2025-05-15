package com.example.myapplication12345.ui.ranking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView // TextView import 추가
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication12345.R
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import timber.log.Timber

class RankingFragment : Fragment() {
    private var fragmentView: View? = null // onCreateView에서 할당, onDestroyView에서 null

    private lateinit var recyclerView: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter

    // Top 3 UI 요소들을 멤버 변수로 선언 (선택 사항이지만, 반복적인 findViewById 줄일 수 있음)
    // 또는 updateTopThree 내에서 매번 findViewById 사용 (현재 방식 유지)
    // 여기서는 현재 방식을 유지하며 수정합니다.

    companion object {
        const val TYPE_NOW = "now"
        const val TYPE_DAILY = "daily"
        const val TYPE_WEEKLY = "weekly"
        const val TYPE_MONTHLY = "monthly"

        private const val TWENTY_FOUR_HOURS_MILLIS = 24 * 60 * 60 * 1000L
        private const val SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val THIRTY_DAYS_MILLIS = 30 * 24 * 60 * 60 * 1000L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ranking, container, false)
        fragmentView = view

        recyclerView = view.findViewById(R.id.rv_profile)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // recyclerView.setHasFixedSize(true) // NestedScrollView와 함께 사용할 때 false가 더 적합할 수 있음
        recyclerView.isNestedScrollingEnabled = false // XML에서 설정했다면 중복이지만, 명시적으로 추가
        recyclerView.visibility = View.VISIBLE

        rankingAdapter = RankingAdapter(ArrayList())
        recyclerView.adapter = rankingAdapter

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    when (tab.position) {
                        0 -> fetchRankingData(TYPE_NOW)
                        1 -> fetchRankingData(TYPE_DAILY)
                        2 -> fetchRankingData(TYPE_WEEKLY)
                        3 -> fetchRankingData(TYPE_MONTHLY)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // R.id.main 대신 fragment_ranking.xml의 최상위 ID (예: fragment_ranking_root) 사용
        // 또는 view 자체에 적용
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fetchRankingData(TYPE_NOW)
        return view
    }

    private fun fetchRankingData(rankingType: String) {
        FirebaseDatabase.getInstance().reference.child("users").get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || fragmentView == null) { // fragmentView null 체크 추가
                    return@addOnSuccessListener
                }

                val profileList = arrayListOf<Profiles>()
                val currentTimeMillis = System.currentTimeMillis()
                val timeThreshold = when (rankingType) {
                    TYPE_DAILY -> TWENTY_FOUR_HOURS_MILLIS
                    TYPE_WEEKLY -> SEVEN_DAYS_MILLIS
                    TYPE_MONTHLY -> THIRTY_DAYS_MILLIS
                    TYPE_NOW -> Long.MAX_VALUE
                    else -> Long.MAX_VALUE
                }

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val nickname = userSnapshot.child("nickname").value?.toString() ?: "Unknown"
                    var score = 0

                    if (rankingType == TYPE_NOW) {
                        score = userSnapshot.child("score").getValue(Int::class.java) ?: 0
                    } else {
                        val scoresSnapshot = userSnapshot.child("scores")
                        for (scoreSnapshot in scoresSnapshot.children) {
                            val scoreValue = scoreSnapshot.child("value").getValue(Int::class.java) ?: 0
                            val timestamp = scoreSnapshot.child("timestamp").getValue(Long::class.java) ?: 0
                            if (currentTimeMillis - timestamp <= timeThreshold) {
                                score += scoreValue
                            }
                        }
                    }
                    if (score > 0) {
                        profileList.add(Profiles(userId, nickname, score))
                    }
                }

                profileList.sortByDescending { it.score }

                updateTopThree(profileList) // fragmentView가 null이 아님을 확인했으므로 호출

                val remainingProfiles = if (profileList.size > 3) {
                    ArrayList(profileList.subList(3, profileList.size))
                } else {
                    arrayListOf()
                }
                rankingAdapter.updateData(remainingProfiles)
            }
            .addOnFailureListener { e ->
                if (isAdded) Timber.e(e, "Failed to load ranking data for $rankingType")
            }
    }

    private fun updateTopThree(profileList: ArrayList<Profiles>) {
        // fragmentView를 사용하여 현재 프래그먼트의 뷰를 가져옴
        val currentFragmentView = fragmentView
        if (currentFragmentView == null || !isAdded) {
            return
        }

        // fragmentView에서 Top 3 UI 요소들을 찾음
        val ivProfile1 = currentFragmentView.findViewById<ImageView>(R.id.iv_profile1)
        val tvName1 = currentFragmentView.findViewById<TextView>(R.id.tv_name1)
        val tvScore1 = currentFragmentView.findViewById<TextView>(R.id.tv_score1)
        val ivProfile2 = currentFragmentView.findViewById<ImageView>(R.id.iv_profile2)
        val tvName2 = currentFragmentView.findViewById<TextView>(R.id.tv_name2)
        val tvScore2 = currentFragmentView.findViewById<TextView>(R.id.tv_score2)
        val ivProfile3 = currentFragmentView.findViewById<ImageView>(R.id.iv_profile3)
        val tvName3 = currentFragmentView.findViewById<TextView>(R.id.tv_name3)
        val tvScore3 = currentFragmentView.findViewById<TextView>(R.id.tv_score3)


        fun loadProfileImage(userId: String, imageView: ImageView?) { // imageView를 nullable로 변경
            if (!isAdded || imageView == null) return // imageView null 체크 추가

            FirebaseDatabase.getInstance().reference.child("users").child(userId)
                .child("profileImageUrl").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded || fragmentView == null) return // fragmentView null 체크 추가

                        val imageUrl = snapshot.getValue(String::class.java)
                        // this@RankingFragment 대신 requireContext() 사용
                        Glide.with(requireContext())
                            .load(imageUrl)
                            .placeholder(R.drawable.user)
                            .error(R.drawable.user)
                            .into(imageView) // imageView가 non-null임이 보장됨
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (isAdded && fragmentView != null) { // fragmentView null 체크 추가
                            Timber.w(error.toException(), "loadProfileImage:onCancelled")
                            imageView.setImageResource(R.drawable.user) // imageView가 non-null임이 보장됨
                        }
                    }
                })
        }

        // 1등 처리
        if (profileList.isNotEmpty()) {
            val top1 = profileList[0]
            loadProfileImage(top1.userId, ivProfile1) // ivProfile1 전달
            tvName1?.text = top1.name
            tvScore1?.text = top1.score.toString()
        } else {
            ivProfile1?.setImageResource(R.drawable.user)
            tvName1?.text = ""
            tvScore1?.text = "000"
        }

        // 2등 처리
        if (profileList.size >= 2) {
            val top2 = profileList[1]
            loadProfileImage(top2.userId, ivProfile2) // *** 수정: ivProfile2 전달 ***
            tvName2?.text = top2.name
            tvScore2?.text = top2.score.toString()
        } else {
            ivProfile2?.setImageResource(R.drawable.user)
            tvName2?.text = ""
            tvScore2?.text = "000"
        }

        // 3등 처리
        if (profileList.size >= 3) {
            val top3 = profileList[2]
            loadProfileImage(top3.userId, ivProfile3) // *** 수정: ivProfile3 전달 ***
            tvName3?.text = top3.name
            tvScore3?.text = top3.score.toString()
        } else {
            ivProfile3?.setImageResource(R.drawable.user)
            tvName3?.text = ""
            tvScore3?.text = "000"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // RecyclerView 어댑터 참조 해제
        if (::recyclerView.isInitialized && recyclerView.adapter != null) {
            recyclerView.adapter = null
        }
        fragmentView = null // fragmentView 참조 해제
    }
}
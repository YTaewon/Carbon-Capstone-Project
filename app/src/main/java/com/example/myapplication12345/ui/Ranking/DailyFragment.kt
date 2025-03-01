package com.example.myapplication12345.ui.ranking

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication12345.Profiles
import com.example.myapplication12345.R
import com.example.myapplication12345.RankingAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

class DailyFragment : Fragment() {
    // Firebase 인증 객체 선언
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 프래그먼트 레이아웃을 생성 및 반환
        return inflater.inflate(R.layout.fragment_now, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Firebase 인증 객체 초기화
        auth = Firebase.auth

        // 리사이클러뷰 초기화
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_profile)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true) // 성능 최적화

        // Firebase Realtime Database에서 "users" 노드 가져오기
        FirebaseDatabase.getInstance().reference.child("users").get()
            .addOnSuccessListener { snapshot ->
                val profileList = arrayListOf<Profiles>() // 사용자 프로필 목록 저장

                // 현재 시간 (밀리초 단위)
                val currentTimeMillis = System.currentTimeMillis()
                val twentyFourHoursMillis = 24 * 60 * 60 * 1000 // 24시간(밀리초)

                // 데이터베이스에서 사용자 정보 반복 탐색
                for (userSnapshot in snapshot.children) {
                    val nickname = userSnapshot.child("nickname").value.toString() // 사용자 닉네임
                    var recentScore = 0 // 최근 24시간 내 점수 합산 변수

                    // "scores" 노드 가져오기
                    val scoresSnapshot = userSnapshot.child("scores")
                    for (scoreSnapshot in scoresSnapshot.children) {
                        val scoreValue = scoreSnapshot.child("value").getValue(Int::class.java) ?: 0
                        val timestamp = scoreSnapshot.child("timestamp").getValue(Long::class.java) ?: 0

                        // 최근 24시간 내에 기록된 점수만 합산
                        if (currentTimeMillis - timestamp <= twentyFourHoursMillis) {
                            recentScore += scoreValue
                        }
                    }

                    // 프로필 리스트에 추가 (유저 아바타, 닉네임, 최근 점수)
                    profileList.add(Profiles(R.drawable.user, nickname, recentScore))
                }

                // 점수를 기준으로 내림차순 정렬 (높은 점수가 상위)
                profileList.sortByDescending { it.score }

                // 정렬된 데이터를 리사이클러뷰 어댑터에 적용
                recyclerView.adapter = RankingAdapter(profileList)
            }
            .addOnFailureListener {
                // 데이터 불러오기 실패 시 메시지 표시
                Toast.makeText(requireContext(), "사용자 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT)
                    .show()
            }
    }
}

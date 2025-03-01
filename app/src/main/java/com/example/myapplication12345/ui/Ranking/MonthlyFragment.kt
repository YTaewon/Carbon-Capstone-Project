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

class MonthlyFragment : Fragment() {
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_now, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_profile)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)

        FirebaseDatabase.getInstance().reference.child("users").get()
            .addOnSuccessListener { snapshot ->
                val profileList = arrayListOf<Profiles>()

                // Current time in milliseconds
                val currentTimeMillis = System.currentTimeMillis()
                val twentyFourHoursMillis = 30L * 24 * 60 * 60 * 1000 // 30days in milliseconds

                for (userSnapshot in snapshot.children) {
                    val nickname = userSnapshot.child("nickname").value.toString()
                    var recentScore = 0

                    // Iterate through scores
                    val scoresSnapshot = userSnapshot.child("scores")
                    for (scoreSnapshot in scoresSnapshot.children) {
                        val scoreValue = scoreSnapshot.child("value").getValue(Int::class.java) ?: 0
                        val timestamp = scoreSnapshot.child("timestamp").getValue(Long::class.java) ?: 0

                        // Check if the score was added in the last 24 hours
                        if (currentTimeMillis - timestamp <= twentyFourHoursMillis) {
                            recentScore += scoreValue
                        }
                    }

                    profileList.add(Profiles(R.drawable.user, nickname, recentScore))
                }

                // Sort by score in descending order
                profileList.sortByDescending { it.score }

                recyclerView.adapter = RankingAdapter(profileList)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "사용자 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT)
                    .show()
            }
    }
}
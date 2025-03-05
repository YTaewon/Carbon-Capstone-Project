package com.example.myapplication12345.ui.ranking

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication12345.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

class NowFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ranking_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth

        recyclerView = view.findViewById<RecyclerView>(R.id.rv_profile)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.visibility = View.VISIBLE // 가시성 보장

        rankingAdapter = RankingAdapter(ArrayList())
        recyclerView.adapter = rankingAdapter

        FirebaseDatabase.getInstance().reference.child("users").get()
            .addOnSuccessListener { snapshot ->
                val profileList = arrayListOf<Profiles>()

                for (userSnapshot in snapshot.children) {
                    val nickname = userSnapshot.child("nickname").value?.toString() ?: "Unknown"
                    val score = userSnapshot.child("score").getValue(Int::class.java) ?: 0
                    if (score > 0) { // 점수 0 제외
                        profileList.add(Profiles(R.drawable.user, nickname, score))
                    }
                }

//                Toast.makeText(requireContext(), "로드된 데이터: ${profileList.size}개", Toast.LENGTH_SHORT).show()

                profileList.sortByDescending { it.score }
                updateTopThree(profileList)

                val remainingProfiles = if (profileList.size > 3) {
                    ArrayList(profileList.subList(3, profileList.size))
                } else {
                    arrayListOf()
                }

                // remainingProfiles 크기 확인
//                Toast.makeText(requireContext(), "RecyclerView 데이터: ${remainingProfiles.size}개", Toast.LENGTH_SHORT).show()

                rankingAdapter.updateData(remainingProfiles)
            }
            .addOnFailureListener {
//                Toast.makeText(requireContext(), "데이터 로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTopThree(profileList: ArrayList<Profiles>) {
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
        }else{
            tvName1?.text = ""
            tvScore1?.text = "000"
        }

        if (profileList.size >= 2) {
            val top2 = profileList[1]
            ivProfile2?.setImageResource(top2.profile)
            tvName2?.text = top2.name
            tvScore2?.text = top2.score.toString()
        }else{
            tvName2?.text = ""
            tvScore2?.text = "000"
        }

        if (profileList.size >= 3) {
            val top3 = profileList[2]
            ivProfile3?.setImageResource(top3.profile)
            tvName3?.text = top3.name
            tvScore3?.text = top3.score.toString()
        }else{
            tvName3?.text = ""
            tvScore3?.text = "000"
        }
    }
}
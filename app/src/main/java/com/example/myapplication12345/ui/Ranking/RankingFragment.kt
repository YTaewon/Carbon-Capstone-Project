package com.example.myapplication12345.ui.ranking

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
import com.bumptech.glide.Glide
import com.example.myapplication12345.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import timber.log.Timber

class RankingFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter

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

        recyclerView = view.findViewById(R.id.rv_profile)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.visibility = View.VISIBLE

        rankingAdapter = RankingAdapter(ArrayList())
        recyclerView.adapter = rankingAdapter

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

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fetchRankingData(TYPE_NOW)
        btnNow.textSize = 20f

        return view
    }

    private fun fetchRankingData(rankingType: String) {
        FirebaseDatabase.getInstance().reference.child("users").get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

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
                updateTopThree(profileList)
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
        if (!isAdded || activity == null) return

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

        fun loadProfileImage(userId: String, imageView: ImageView) {
            FirebaseDatabase.getInstance().reference.child("users").child(userId)
                .child("profileImageUrl").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val imageUrl = snapshot.getValue(String::class.java)
                        Glide.with(this@RankingFragment)
                            .load(imageUrl)
                            .placeholder(R.drawable.user)
                            .error(R.drawable.user)
                            .into(imageView)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Timber.w(error.toException(), "loadProfileImage:onCancelled")
                        imageView.setImageResource(R.drawable.user)
                    }
                })
        }

        if (profileList.size >= 1) {
            val top1 = profileList[0]
            loadProfileImage(top1.userId, ivProfile1)
            tvName1?.text = top1.name
            tvScore1?.text = top1.score.toString()
        } else {
            ivProfile1?.setImageResource(R.drawable.user)
            tvName1?.text = ""
            tvScore1?.text = "000"
        }

        if (profileList.size >= 2) {
            val top2 = profileList[1]
            loadProfileImage(top2.userId, ivProfile2)
            tvName2?.text = top2.name
            tvScore2?.text = top2.score.toString()
        } else {
            ivProfile2?.setImageResource(R.drawable.user)
            tvName2?.text = ""
            tvScore2?.text = "000"
        }

        if (profileList.size >= 3) {
            val top3 = profileList[2]
            loadProfileImage(top3.userId, ivProfile3)
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
        recyclerView.adapter = null
    }
}
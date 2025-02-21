package com.example.myapplication12345.ui.home

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication12345.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Firebase Database 인스턴스 가져오기
        val database = FirebaseDatabase.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // 데이터베이스에서 닉네임, 점수, 탄소 포인트 가져오기
        if (userId != null) {
            val userRef = database.getReference("users").child(userId)

            // 닉네임 가져오기
            userRef.child("nickname").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val nickname = dataSnapshot.getValue(String::class.java)
                    binding.nicknameText.text = nickname ?: "익명" // 기본값 "익명"으로 변경
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.w("FirebaseDatabase", "loadNickname:onCancelled", databaseError.toException())
                    binding.nicknameText.text = "익명" // 기본값 "익명"으로 변경
                }
            })

            // 점수 가져오기
            userRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val score = dataSnapshot.getValue(Int::class.java)
                    binding.scoreText.text = "점수: ${score ?: 0}"
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.w("FirebaseDatabase", "loadScore:onCancelled", databaseError.toException())
                    binding.scoreText.text = "점수: 0"
                }
            })

            // 탄소 포인트 가져오기
            userRef.child("point").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val point = dataSnapshot.getValue(Int::class.java)
                    binding.pointText.text = "탄소 포인트: ${point ?: 0}"
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.w("FirebaseDatabase", "loadPoint:onCancelled", databaseError.toException())
                    binding.pointText.text = "탄소 포인트: 0"
                }
            })
        } else {
            binding.nicknameText.text = "익명"
            binding.scoreText.text = "점수: 0"
            binding.pointText.text = "탄소 포인트: 0"
        }

        homeViewModel.text.observe(viewLifecycleOwner) { newText ->
            binding.greetingText.text = newText
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
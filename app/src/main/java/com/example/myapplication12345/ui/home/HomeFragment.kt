package com.example.myapplication12345.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication12345.AI.AITest
import com.example.myapplication12345.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var profileImage: ImageView
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private var doubleClick = false
    private val doubleHandler = Handler(Looper.getMainLooper())
    private var selectedImageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        profileImage = binding.profileImage




        // 갤러리 런처 초기화
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = result.data?.data
                if (selectedImageUri != null) {
                    profileImage.setImageURI(selectedImageUri)
                    Toast.makeText(context, "프로필 이미지가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

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

        // 더블 클릭 이벤트 처리
        profileImage.setOnClickListener {
            if (doubleClick) {
                // 더블 클릭 시 갤러리 열기
                openGallery()
                doubleClick = false
            } else {
                doubleClick = true
                doubleHandler.postDelayed({
                    doubleClick = false
                }, 500) // 500ms 이내에 두 번 클릭해야 더블 클릭으로 인식
            }
        }

        return root
    }

    // 갤러리 열기
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
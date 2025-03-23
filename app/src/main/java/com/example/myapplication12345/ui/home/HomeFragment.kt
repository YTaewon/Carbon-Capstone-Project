package com.example.myapplication12345.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication12345.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import timber.log.Timber

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
        // ViewModel 초기화
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // 바인딩 초기화
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.homeViewModel = homeViewModel
        binding.lifecycleOwner = viewLifecycleOwner

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

        // Firebase 데이터 가져오기
        val database = FirebaseDatabase.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val userRef = database.getReference("users").child(userId)
            userRef.child("nickname").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val nickname = dataSnapshot.getValue(String::class.java)
                    binding.nicknameText.text = nickname ?: "익명"
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.tag("Firebase").w(databaseError.toException(), "loadNickname:onCancelled")
                    binding.nicknameText.text = "익명"
                }
            })
            userRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val score = dataSnapshot.getValue(Int::class.java)
                    "점수: ${score ?: 0}".also { binding.scoreText.text = it }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.tag("Firebase").w(databaseError.toException(), "loadScore:onCancelled")
                    binding.scoreText.text = "점수: 0"
                }
            })
            userRef.child("point").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val point = dataSnapshot.getValue(Int::class.java)
                    "탄소 포인트: ${point ?: 0}".also { binding.pointText.text = it }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.tag("Firebase").w(databaseError.toException(), "loadPoint:onCancelled")
                    binding.pointText.text = "탄소 포인트: 0"
                }
            })
        } else {
            binding.nicknameText.text = "익명"
            binding.scoreText.text = "점수: 0"
            binding.pointText.text = "탄소 포인트: 0"
        }

        // 인사말 텍스트 관찰
        homeViewModel.text.observe(viewLifecycleOwner) { newText ->
            binding.greetingText.text = newText
        }

        // 팁 텍스트 관찰
        homeViewModel.currentTip.observe(viewLifecycleOwner) { newTip ->
            binding.tipText.text = newTip
            Timber.tag("HomeFragment").d("Tip updated: $newTip")
        }

        // "다음 팁" 버튼 클릭 리스너 설정
        binding.nextTipButton.setOnClickListener {
            Timber.tag("HomeFragment").d("Next tip button clicked")
            homeViewModel.showRandomTip()
        }

        // 새로고침 버튼 클릭 시 랜덤 뉴스 갱신
        binding.refreshButton.setOnClickListener {
            homeViewModel.showRandomNews()
            Toast.makeText(context, "뉴스가 갱신되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // 환경 뉴스 섹션 클릭 시 링크로 이동
        binding.newsSection.setOnClickListener {
            homeViewModel.news.value?.let { news ->
                val link = news.originallink
                if (link.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "유효한 링크가 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(context, "뉴스가 없습니다.", Toast.LENGTH_SHORT).show()
        }

        // 더블 클릭 이벤트 처리
        profileImage.setOnClickListener {
            if (doubleClick) {
                openGallery()
                doubleClick = false
            } else {
                doubleClick = true
                doubleHandler.postDelayed({ doubleClick = false }, 500)
            }
        }

        return root
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
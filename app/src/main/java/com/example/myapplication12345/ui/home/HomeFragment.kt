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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.myapplication12345.R
import com.example.myapplication12345.databinding.FragmentHomeBinding
import com.example.myapplication12345.ui.pedometer.PedometerFragment
import com.example.myapplication12345.ui.pedometer.PedometerViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import timber.log.Timber
import java.text.DecimalFormat

class HomeFragment : Fragment() {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var profileImage: ImageView

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val pedometerViewModel: PedometerViewModel by activityViewModels()
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private var doubleClick = false
    private val doubleHandler = Handler(Looper.getMainLooper())
    private var selectedImageUri: Uri? = null

    // Firebase 관련 변수
    private val auth = FirebaseAuth.getInstance()
    private lateinit var storage: FirebaseStorage
    private lateinit var database: FirebaseDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        _binding = FragmentHomeBinding.bind(view)
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding.homeViewModel = homeViewModel
        binding.lifecycleOwner = viewLifecycleOwner

        profileImage = view.findViewById(R.id.profile_img)

        // Firebase 초기화
        storage = FirebaseStorage.getInstance()
        database = FirebaseDatabase.getInstance()
        // 갤러리 런처 설정
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = result.data?.data
                if (selectedImageUri != null) {
                    profileImage.setImageURI(selectedImageUri) // 미리보기
                    uploadImageToFirebase(selectedImageUri!!) // Firebase에 업로드
                }
            }
        }

        // Firebase 데이터 가져오기
        val userId = auth.currentUser?.uid
        loadProfileImage(userId.toString())

        if (userId != null) {
            val userRef = database.getReference("users").child(userId)

            // 닉네임
            userRef.child("nickname").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val nickname = dataSnapshot.getValue(String::class.java)?: "익명"
                    binding.greeting.text = buildString {
                        append("안녕하세요, ")
                        append(nickname)
                        append("님!")
                    }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.tag("Firebase").w(databaseError.toException(), "loadNickname:onCancelled")
                    binding.greeting.text = "안녕하세요, 익명님!"
                }
            })

            // 점수
            userRef.child("score").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val score = dataSnapshot.getValue(Int::class.java)
                    " ${score ?: 0}".also { binding.scoreValue.text = it }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.tag("Firebase").w(databaseError.toException(), "loadScore:onCancelled")
                    binding.scoreValue.text = "점수: 0"
                }
            })

            // 포인트
            userRef.child("point").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val point = dataSnapshot.getValue(Int::class.java)
                    "${point ?: 0}".also { binding.pointsValue.text = it }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.tag("Firebase").w(databaseError.toException(), "loadPoint:onCancelled")
                    binding.pointsValue.text = "0"
                }
            })

        } else {
            binding.greeting.text = "안녕하세요, 익명님!"
            binding.scoreValue.text = "점수: 0"
            binding.pointsValue.text = "탄소 배출량: 0"
        }

        // 팁 텍스트 관찰
        homeViewModel.currentTip.observe(viewLifecycleOwner) { newTip ->
            binding.tipText.text = newTip
            Timber.tag("HomeFragment").d("Tip updated: $newTip")
        }

        // 이동 거리 관찰
        val decimalFormat = DecimalFormat("#.##")
        pedometerViewModel.distanceKm.observe(viewLifecycleOwner) { distance ->
            binding.tvTotalTrees.text = "${decimalFormat.format(distance)}km"
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

        // PedometerFragment 다이얼로그 띄우기
        binding.distanceContainer.setOnClickListener {
            val pedometerFragment = PedometerFragment()
            pedometerFragment.show(childFragmentManager, "PedometerFragment")
        }

        // 오늘의 탄소 절약 목표 프로그레스 관찰
        homeViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.progressMonthlyGoal.progress = progress
            binding.tvMonthlyProgress.text = "$progress/100%"
        }

        // 프로그레스 테스트용 클릭 리스너
        binding.progressMonthlyGoal.setOnClickListener {
            val newProgress = (1..100).random()
            homeViewModel.setProgress(newProgress)
            Toast.makeText(context, "목표 진행률: $newProgress%", Toast.LENGTH_SHORT).show()
        }
        return view
    }

    // 갤러리 열기
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    // Firebase Storage에 이미지 업로드
    private fun uploadImageToFirebase(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveImageUrlToDatabase(downloadUrl.toString(), userId)
                    Toast.makeText(context, "프로필 이미지가 업로드되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Image upload failed")
                Toast.makeText(context, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 서버에 이미지 URL 저장
    private fun saveImageUrlToDatabase(imageUrl: String, userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Timber.tag("Firebase").d("Profile image URL saved: $imageUrl")
                loadProfileImage(userId) // 즉시 UI 업데이트
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Failed to save image URL")
            }
    }

    // 프로필 이미지 로드
    private fun loadProfileImage(userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.child("profileImageUrl").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val imageUrl = snapshot.getValue(String::class.java)
                if (imageUrl != null) {
                    Glide.with(this@HomeFragment)
                        .load(imageUrl)
                        .placeholder(R.drawable.user) // 기본 이미지
                        .error(R.drawable.user) // 로드 실패 시 기본 이미지
                        .placeholder(R.drawable.user)
                        .error(R.drawable.user)
                        .into(profileImage)

                } else {
                    profileImage.setImageResource(R.drawable.user) // 기본 이미지 설정
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.tag("Firebase").w(error.toException(), "loadProfileImage:onCancelled")
                profileImage.setImageResource(R.drawable.user)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

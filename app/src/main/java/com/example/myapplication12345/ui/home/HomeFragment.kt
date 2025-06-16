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
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.myapplication12345.R
import com.example.myapplication12345.chatbot.ChatbotUi
import com.example.myapplication12345.databinding.FragmentHomeBinding
import com.example.myapplication12345.ui.pedometer.PedometerFragment
import com.example.myapplication12345.ui.pedometer.PedometerViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    // View Binding: View 생명주기와 연결하기 위해 nullable로 선언
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModels
    private lateinit var homeViewModel: HomeViewModel
    private val pedometerViewModel: PedometerViewModel by activityViewModels()

    // Activity Result Launcher
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    // UI 상태 변수
    private var doubleClick = false
    private val doubleHandler = Handler(Looper.getMainLooper())

    // Firebase 인스턴스
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // --- Firebase 리스너 및 DB 참조를 멤버 변수로 선언 ---
    private var userRef: DatabaseReference? = null
    private var profileImageRef: DatabaseReference? = null
    private var monthlyPointsRef: DatabaseReference? = null
    private var scoreRef: DatabaseReference? = null

    // 프로필 이미지 리스너
    private val profileImageListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (_binding == null || !isAdded) return
            val imageUrl = snapshot.getValue(String::class.java)
            context?.let { ctx ->
                Glide.with(ctx)
                    .load(imageUrl)
                    .placeholder(R.drawable.user)
                    .error(R.drawable.user)
                    .circleCrop()
                    .into(binding.profileImg)
            }
        }
        override fun onCancelled(error: DatabaseError) {
            Timber.e(error.toException(), "프로필 이미지 로드 실패")
        }
    }

    // 월별 포인트 리스너
    private val monthlyPointsListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (_binding == null) return
            val point = snapshot.getValue(Int::class.java) ?: 0
            binding.pointsValue.text = point.toString()
        }
        override fun onCancelled(error: DatabaseError) {
            Timber.w(error.toException(), "월별 포인트 로드 실패")
        }
    }

    // 점수 리스너
    private val scoreListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (_binding == null) return
            val score = snapshot.getValue(Int::class.java) ?: 0
            binding.scoreValue.text = score.toString()
        }
        override fun onCancelled(error: DatabaseError) {
            Timber.w(error.toException(), "점수 로드 실패")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding.homeViewModel = homeViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupViewModels()
        setupFirebaseData()
    }

    private fun setupUI() {
        binding.openChatbotButton.setOnClickListener { startActivity(Intent(requireContext(), ChatbotUi::class.java)) }
        binding.openChatbotText.setOnClickListener { startActivity(Intent(requireContext(), ChatbotUi::class.java)) }
        binding.nextTipButton.setOnClickListener { homeViewModel.showRandomTip() }
        binding.refreshButton.setOnClickListener {
            homeViewModel.showRandomNews()
            Toast.makeText(context, "뉴스가 갱신되었습니다.", Toast.LENGTH_SHORT).show()
        }
        binding.distanceContainer.setOnClickListener { PedometerFragment().show(childFragmentManager, "PedometerFragment") }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.profileImg.setImageURI(uri)
                    uploadImageToFirebase(uri)
                }
            }
        }

        binding.profileImg.setOnClickListener {
            if (doubleClick) {
                openGallery()
                doubleClick = false
            } else {
                doubleClick = true
                doubleHandler.postDelayed({ doubleClick = false }, 500)
            }
        }
    }

    private fun setupViewModels() {
        homeViewModel.currentTip.observe(viewLifecycleOwner) { binding.tipText.text = it }
        homeViewModel.news.observe(viewLifecycleOwner) { news ->
            binding.newsSection.setOnClickListener {
                news?.originallink?.takeIf { it.isNotEmpty() }?.let { link ->
                    startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                } ?: Toast.makeText(context, "유효한 링크가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        pedometerViewModel.distanceMeters.observe(viewLifecycleOwner) {
            binding.tvTotalTrees.text = pedometerViewModel.getFormattedDistance()
        }
        homeViewModel.progress.observe(viewLifecycleOwner) { progress ->
            binding.progressMonthlyGoal.progress = progress
            binding.tvMonthlyProgress.text = (progress / 100).toString()
        }
    }

    private fun setupFirebaseData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            // 로그아웃 상태 UI 처리
            binding.greeting.text = "안녕하세요, 익명님!"
            binding.scoreValue.text = "0"
            binding.pointsValue.text = "0"
            homeViewModel.setProgress(0)
            binding.profileImg.setImageResource(R.drawable.user)
            return
        }

        // DB 참조 경로 설정
        userRef = database.getReference("users").child(userId)
        profileImageRef = userRef?.child("profileImageUrl")
        monthlyPointsRef = userRef?.child("monthly_points")?.child(getCurrentMonth())?.child("point")
        scoreRef = userRef?.child("score")

        // 일회성 데이터 로드 (닉네임만)
        userRef?.child("nickname")?.get()?.addOnSuccessListener {
            val nickname = it.getValue(String::class.java) ?: "익명"
            if (isAdded) binding.greeting.text = "안녕하세요, ${nickname}님!"
        }

        // 월별 초기화 로직
        checkAndResetMonthlyProgress()
    }

    // --- 리스너 등록 및 제거 ---
    override fun onStart() {
        super.onStart()
        // View가 사용자에게 보일 때, 지속적인 데이터 관찰을 위해 리스너 등록
        profileImageRef?.addValueEventListener(profileImageListener)
        monthlyPointsRef?.addValueEventListener(monthlyPointsListener)
        scoreRef?.addValueEventListener(scoreListener)
    }

    override fun onStop() {
        super.onStop()
        // View가 가려질 때, 불필요한 업데이트와 메모리 누수 방지를 위해 리스너 제거
        profileImageRef?.removeEventListener(profileImageListener)
        monthlyPointsRef?.removeEventListener(monthlyPointsListener)
        scoreRef?.removeEventListener(scoreListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Handler 콜백 제거 (메모리 누수 방지)
        doubleHandler.removeCallbacksAndMessages(null)
        // View Binding 참조 해제 (메모리 누수 방지)
        _binding = null
    }

    // --- 헬퍼 함수 ---
    private fun getCurrentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Calendar.getInstance().time)

    private fun checkAndResetMonthlyProgress() {
        val currentMonth = getCurrentMonth()
        userRef?.child("last_reset_month")?.get()?.addOnSuccessListener { snapshot ->
            if (snapshot.getValue(String::class.java) != currentMonth) {
                // 새로운 달이므로 포인트 초기화 및 last_reset_month 업데이트
                userRef?.child("monthly_points")?.child(currentMonth)?.child("point")?.setValue(0)
                userRef?.child("last_reset_month")?.setValue(currentMonth)
                homeViewModel.setProgress(0)
            }
        }
    }

    private fun openGallery() {
        galleryLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        storage.reference.child("profile_images/$userId.jpg").putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveImageUrlToDatabase(downloadUrl.toString(), userId)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(context, "이미지 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
                Timber.e(e, "Image upload failed")
            }
    }

    private fun saveImageUrlToDatabase(imageUrl: String, userId: String) {
        // 프로필 이미지가 저장되면, onStart에서 등록한 profileImageListener가
        // 자동으로 변경을 감지하여 UI를 업데이트하므로 여기서 별도의 UI 업데이트 불필요.
        database.getReference("users").child(userId).child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Toast.makeText(context, "프로필 이미지가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Timber.e(e, "이미지 URL 저장 실패")
            }
    }
}
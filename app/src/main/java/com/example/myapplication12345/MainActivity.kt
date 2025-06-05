package com.example.myapplication12345

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.myapplication12345.AI.SensorDataService
import com.example.myapplication12345.chatbot.ChatbotUi
import com.example.myapplication12345.databinding.ActivityMainBinding
import com.example.myapplication12345.ui.login.IntroActivity
import com.example.myapplication12345.ui.sidebar.carbonquiz.QuizActivity
import com.example.myapplication12345.ui.sidebar.foodcalculator.FoodCalculatorActivity
import com.example.myapplication12345.ui.sidebar.setting.SettingActivity
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var database: FirebaseDatabase
    private lateinit var binding: ActivityMainBinding
    private lateinit var serverManager: ServerManager
    private lateinit var drawerLayout: DrawerLayout

    private lateinit var profileImage: ImageView
    private lateinit var profileImageChange: Button
    private var selectedImageUri: Uri? = null
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        storage = FirebaseStorage.getInstance()
        database = FirebaseDatabase.getInstance()
        binding = ActivityMainBinding.inflate(layoutInflater)
        serverManager = ServerManager(this)

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

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        // DrawerLayout과 NavigationView 초기화
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        // 메뉴 버튼 클릭 시 사이드바 열기
        val menuButton = findViewById<ImageButton>(R.id.menu)
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // 사용자 프로필 설정
        val headerView = navView.getHeaderView(0) // 헤더 뷰 가져오기
        val profileName = headerView.findViewById<TextView>(R.id.profile_name)
        profileImage = headerView.findViewById<ImageView>(R.id.profile_image)
        profileImageChange = headerView.findViewById<Button>(R.id.profile_image_change)

        profileImageChange.setOnClickListener {
            openGallery()
        }

        val userId = auth.currentUser?.uid
        loadProfileImage(userId.toString());

        lifecycleScope.launch {
            val nickname = serverManager.getNickname() // suspend 호출로 즉시 값 반환
            profileName.text = nickname // "익명" 또는 최신 값 설정
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    binding.viewPager.currentItem = 0
                }

                R.id.nav_settings -> {
                    val intent = Intent(this, SettingActivity::class.java)
                    startActivity(intent)
                }

                R.id.nav_logout -> {
                    auth.signOut()
                    val intent = Intent(this, IntroActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }

                R.id.nav_plus -> {
                    serverManager.addScoreToCurrentUser(5)
                }

                R.id.nav_food -> {
                    val intent = Intent(this, FoodCalculatorActivity::class.java)
                    startActivity(intent)
                }

                R.id.nav_quiz -> {
                    val intent = Intent(this, QuizActivity::class.java)
                    startActivity(intent)
                }

                R.id.nav_chatbot -> {
                    val intent = Intent(this, ChatbotUi::class.java)
                    startActivity(intent)
                }
                R.id.nav_ai_start ->{
                    startSensorService();
                }
                R.id.nav_ai_stop ->{
                    stopSensorService()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        setupViewPager()
        setupBottomNavigation()

        binding.logo.setOnClickListener {
            binding.viewPager.currentItem = 0
            binding.bottomNavigation.menu.findItem(R.id.navigation_home).isChecked = true
        }

        binding.slogan.setOnClickListener {
            binding.viewPager.currentItem = 0
            binding.bottomNavigation.menu.findItem(R.id.navigation_home).isChecked = true
        }

        // OnBackPressedDispatcher 설정
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish() // 기본 뒤로 가기 동작 (Activity 종료)
                }
            }
        })
    }

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
                    Toast.makeText(this, "프로필 이미지가 업로드되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Image upload failed")
                Toast.makeText(this, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Glide.with(this@MainActivity)
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

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = true
    }

    private fun setupBottomNavigation() {
        val viewPager = binding.viewPager
        val bottomNav = binding.bottomNavigation

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNav.menu[position].isChecked = true
            }
        })

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> viewPager.currentItem = 0
                R.id.navigation_ranking -> viewPager.currentItem = 1
                R.id.navigation_camera -> viewPager.currentItem = 2
                R.id.navigation_calendar -> viewPager.currentItem = 3
                R.id.navigation_map -> viewPager.currentItem = 4
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun stopSensorService() {
        val serviceIntent = Intent(this, SensorDataService::class.java)
        // 서비스 정지를 위한 고유 액션 설정
        serviceIntent.setAction(SensorDataService.ACTION_STOP_SERVICE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(this, "AI 서비스 정지 요청", Toast.LENGTH_SHORT).show()
        Timber.tag("MainActivity").d("SensorDataService 정지 요청됨.")
    }

    private fun startSensorService() {
        val serviceIntent: Intent = Intent(this, SensorDataService::class.java)
        // Android O (API 26) 이상에서는 startForegroundService() 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "AI 서비스 시작 요청", Toast.LENGTH_SHORT).show()
        Timber.tag("MainActivity").d("SensorDataService 시작 요청됨.")
    }
}
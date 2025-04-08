package com.example.myapplication12345

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication12345.databinding.ActivityMainBinding
import com.example.myapplication12345.ui.login.IntroActivity
import com.example.myapplication12345.ui.sidebar.foodcalculator.FoodCalculatorActivity
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.activity.OnBackPressedCallback
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.myapplication12345.ui.sidebar.carbonquiz.QuizActivity
import com.google.firebase.ktx.BuildConfig
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding
    private lateinit var serverManager: ServerManager
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }else{
            //릴리즈 할경우 제거 필요
            Timber.plant(Timber.DebugTree())
        }

        auth = Firebase.auth
        binding = ActivityMainBinding.inflate(layoutInflater)
        serverManager = ServerManager(this)
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
        val profileImage = headerView.findViewById<ImageView>(R.id.profile_image)

        lifecycleScope.launch {
            val nickname = serverManager.getNickname() // suspend 호출로 즉시 값 반환
            profileName.text = nickname // "익명" 또는 최신 값 설정
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    binding.viewPager.currentItem = 0
                }
                R.id.nav_settings -> {}
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
}
package com.example.myapplication12345

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication12345.databinding.ActivityMainBinding
import com.example.myapplication12345.ui.login.IntroActivity
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.activity.OnBackPressedCallback
import androidx.core.view.get

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding
    private lateinit var scoreManager: ScoreManager
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        binding = ActivityMainBinding.inflate(layoutInflater)
        scoreManager = ScoreManager(this)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)

        val menuButton = findViewById<ImageButton>(R.id.menu)
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {}
                R.id.nav_settings -> {}
                R.id.nav_logout -> {
                    auth.signOut()
                    val intent = Intent(this, IntroActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                R.id.nav_plus -> scoreManager.addScoreToCurrentUser(5)
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
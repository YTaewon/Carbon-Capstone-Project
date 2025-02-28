package com.example.myapplication12345

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication12345.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar를 ActionBar로 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "" // 기본 제목 제거

        setupViewPager()
        setupBottomNavigation()

        // 로고 클릭 시 홈으로 이동
        binding.logo.setOnClickListener {
            binding.viewPager.currentItem = 0
            binding.bottomNavigation.menu.findItem(R.id.navigation_home).isChecked = true
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = true // 스와이프 비활성화
    }

    private fun setupBottomNavigation() {
        val viewPager = binding.viewPager
        val bottomNav = binding.bottomNavigation

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNav.menu.getItem(position).isChecked = true
            }
        })

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> viewPager.currentItem = 0
                R.id.navigation_stepper -> viewPager.currentItem = 1
                R.id.navigation_camera -> viewPager.currentItem = 2
                R.id.navigation_ranking -> viewPager.currentItem = 3
                R.id.navigation_calendar -> viewPager.currentItem = 4

                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }
}
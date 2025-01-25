package com.example.myapplication12345

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication12345.databinding.ActivityCameraBinding

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 레이아웃 바인딩
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "탄소중립"
        // 뷰페이저와 하단 네비게이션 설정
        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        // ViewPager2 어댑터 설정
        binding.viewPager.adapter = ViewPagerAdapter(this)

        // PageTransformer 설정 (깊이감 있는 애니메이션)
        //binding.viewPager.setPageTransformer(DepthPageTransformer())

    }

    private fun setupBottomNavigation() {
        val viewPager = binding.viewPager
        val bottomNav = binding.navView

        // ViewPager 페이지 변경 시 BottomNavigationView 아이템 체크
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNav.menu.getItem(position).isChecked = true
            }
        })

        // BottomNavigationView 아이템 클릭 시 해당 ViewPager 페이지로 이동
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> viewPager.currentItem = 0
                R.id.navigation_dashboard -> viewPager.currentItem = 1
                R.id.navigation_notifications -> viewPager.currentItem = 2
            }
            true
        }
    }
}
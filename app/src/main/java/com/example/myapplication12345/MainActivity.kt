package com.example.myapplication12345

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication12345.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "탄소중립"

        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)


        binding.viewPager.currentItem = 0
        //기본페이지는 homefragment(0)


        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.navView.menu.getItem(position).isChecked = true
            }
        }) //화면 스와이프 시 네비게이션 바 바뀜

    }

    private fun setupBottomNavigation() {
        val viewPager = binding.viewPager
        val bottomNav = binding.navView


        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> viewPager.currentItem = 0
                R.id.navigation_stepper -> viewPager.currentItem = 1
                R.id.navigation_dashboard -> viewPager.currentItem = 2
                R.id.navigation_ranking -> viewPager.currentItem = 3
                R.id.navigation_calendar -> viewPager.currentItem = 4

            }
            true
        } //네비게이션 바 클릭 시 해당 페이지로 이동
    }
}

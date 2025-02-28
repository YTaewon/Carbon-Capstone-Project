package com.example.myapplication12345

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.example.myapplication12345.ui.home.HomeFragment
import com.example.myapplication12345.ui.camera.CameraFragment
import com.example.myapplication12345.fragments.RankingFragment
import com.example.myapplication12345.ui.calendar.CalendarFragment
import com.example.myapplication12345.ui.stepper.StepperFragment


class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 5 // 페이지 수 (홈, 카메라, 랭킹, 캘린더) 추가 안하면 작동 안함

    //이미지 추가 방법  res/menu/bottom_nav_menu 에 추가
    //순서 설정
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> StepperFragment()
            2 -> CameraFragment()
            3 -> RankingFragment()
            4 -> CalendarFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}
package com.example.myapplication12345

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.myapplication12345.ui.home.HomeFragment
import com.example.myapplication12345.ui.dashboard.DashboardFragment
import com.example.myapplication12345.fragments.RankingFragment
import com.example.myapplication12345.ui.calendar.CalendarFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> DashboardFragment()
            2 -> RankingFragment()
            3 -> CalendarFragment()
            else -> HomeFragment()
        }
    }
}

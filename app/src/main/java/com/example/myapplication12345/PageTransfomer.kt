package com.example.myapplication12345

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class DepthPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        page.apply {
            val pageWidth = width
            when {
                position < -1 || position > 1 -> {
                    // 화면 밖의 페이지
                    alpha = 0f
                }

                else -> {
                    // 화면 안의 페이지
                    // 위치에 따른 투명도 계산
                    alpha = 1 - abs(position)

                    // 스케일 효과
                    val scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - abs(position))
                    scaleX = scaleFactor
                    scaleY = scaleFactor

                    // 이동 효과
                    translationX = -position * pageWidth
                }
            }
        }
    }

    companion object {
        private const val MIN_SCALE = 1.00f
    }
}
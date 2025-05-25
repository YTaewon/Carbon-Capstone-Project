package com.example.myapplication12345.ui.calendar

import androidx.lifecycle.ViewModel
import com.example.myapplication12345.ServerManager
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarViewModel(private val serverManager: ServerManager) : ViewModel() {

    // 특정 날짜의 productEmissions 업데이트
    fun updateProductEmissions(date: Calendar, emissions: Int, onComplete: (Boolean) -> Unit) {
        serverManager.updateProductEmissions(date, emissions) { success ->
            Timber.tag("CalendarViewModel").d("제품 탄소 배출량 업데이트: ${getFormattedDate(date)}: $emissions, 성공: $success")
            onComplete(success)
        }
    }

    // 특정 날짜의 transportEmissions 업데이트
    fun updateTransportEmissions(date: Calendar, emissions: Int, onComplete: (Boolean) -> Unit) {
        serverManager.updateTransportEmissions(date, emissions) { success ->
            Timber.tag("CalendarViewModel").d("이동경로 탄소 배출량 업데이트: ${getFormattedDate(date)}: $emissions, 성공: $success")
            onComplete(success)
        }
    }

    // 날짜 포맷팅 (YYYY-MM-DD)
    fun getFormattedDate(date: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(date.time)
    }
}
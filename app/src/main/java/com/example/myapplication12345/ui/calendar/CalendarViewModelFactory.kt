package com.example.myapplication12345.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication12345.ServerManager

// ServerManager를 받아서 CalendarViewModel을 생성하는 팩토리
class CalendarViewModelFactory(private val serverManager: ServerManager) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // 생성하려는 ViewModel이 CalendarViewModel 클래스와 호환되는지 확인
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            // 호환된다면, ServerManager를 전달하여 CalendarViewModel 인스턴스를 생성하고 반환
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(serverManager) as T
        }
        // 모르는 ViewModel 클래스일 경우 예외 발생
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
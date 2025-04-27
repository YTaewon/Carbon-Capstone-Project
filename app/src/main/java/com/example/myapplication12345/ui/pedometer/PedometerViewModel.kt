package com.example.myapplication12345.ui.pedometer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.DecimalFormat

class PedometerViewModel : ViewModel() {

    private val _steps = MutableLiveData<Int>(0)
    val steps: LiveData<Int> = _steps

    private val _distanceMeters = MutableLiveData<Double>(0.0) // km → meters로 변경
    val distanceMeters: LiveData<Double> = _distanceMeters

    private val distancePerStep = 0.64 // 1걸음당 0.64m
    private val decimalFormat = DecimalFormat("#.##")

    fun updateSteps(newSteps: Int) {
        _steps.value = newSteps
        _distanceMeters.value = newSteps * distancePerStep // m 단위로 계산
    }

    // 거리 표시를 위한 함수 (1000m 이상이면 km으로 변환)
    fun getFormattedDistance(): String {
        val meters = _distanceMeters.value ?: 0.0
        return if (meters >= 1000) {
            "${decimalFormat.format(meters / 1000.0)}km"
        } else {
            "${meters.toInt()}m"
        }
    }

    private val _text = MutableLiveData<String>().apply {
        value = "만보기 화면"
    }
    val text: LiveData<String> = _text
}

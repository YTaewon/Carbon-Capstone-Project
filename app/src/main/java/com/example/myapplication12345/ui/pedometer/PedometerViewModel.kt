package com.example.myapplication12345.ui.pedometer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PedometerViewModel : ViewModel() {

    private val _steps = MutableLiveData<Int>(0)
    val steps: LiveData<Int> = _steps

    private val _distanceKm = MutableLiveData<Double>(0.0)
    val distanceKm: LiveData<Double> = _distanceKm

    private val distancePerStep = 0.64 // 1걸음당 0.64m

    fun updateSteps(newSteps: Int) {
        _steps.value = newSteps
        _distanceKm.value = (newSteps * distancePerStep) / 1000.0 // km로 변환
    }
    private val _text = MutableLiveData<String>().apply {
        value = "만보기 화면"
    }
    val text: LiveData<String> = _text
}

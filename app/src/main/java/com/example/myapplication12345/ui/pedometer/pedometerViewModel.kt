package com.example.myapplication12345.ui.pedometer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class pedometerViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "만보기 화면"
    }
    val text: LiveData<String> = _text
}

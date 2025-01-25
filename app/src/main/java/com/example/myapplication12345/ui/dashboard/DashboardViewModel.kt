package com.example.myapplication12345.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class DashboardViewModel {

    private val _text = MutableLiveData<String>().apply {
        value = "카메라 나올 화면"
    }
    val text: LiveData<String> = _text
}
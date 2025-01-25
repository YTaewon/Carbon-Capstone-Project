package com.example.myapplication12345.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "메인화면(홈화면)"
    }
    val text: LiveData<String> = _text
}
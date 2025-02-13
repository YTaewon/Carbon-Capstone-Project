package com.example.myapplication12345.ui.Ranking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RankingViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "알림 나올 화면"
    }
    val text: LiveData<String> = _text
}
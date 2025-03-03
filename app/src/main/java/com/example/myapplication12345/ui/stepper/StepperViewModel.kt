package com.example.myapplication12345.ui.stepper

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class StepperViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "만보기 나올 화면"
    }
    val text: LiveData<String> = _text
}

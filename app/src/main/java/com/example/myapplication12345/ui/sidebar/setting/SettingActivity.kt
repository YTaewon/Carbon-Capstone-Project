package com.example.myapplication12345.ui.sidebar.setting

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication12345.R

class SettingActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        backButton = findViewById(R.id.back_button)

        backButton.setOnClickListener { finish() }
    }
}
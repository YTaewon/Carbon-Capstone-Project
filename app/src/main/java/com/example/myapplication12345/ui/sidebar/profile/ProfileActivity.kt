package com.example.myapplication12345.ui.sidebar.profile

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication12345.R

class ProfileActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        backButton = findViewById(R.id.back_button)

        backButton.setOnClickListener { finish() }
    }
}
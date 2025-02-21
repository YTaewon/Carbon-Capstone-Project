package com.example.myapplication12345

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication12345.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // HomeFragment를 화면에 표시
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame, HomeFragment())
            .commit()
    }
}
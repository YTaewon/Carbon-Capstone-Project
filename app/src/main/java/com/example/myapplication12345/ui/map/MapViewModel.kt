package com.example.myapplication12345.ui.map

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication12345.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapViewModel : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val selectedDate = intent.getStringExtra("selectedDate") ?: SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
            Date()
        )
        val mapFragment = MapFragment()
        val bundle = Bundle().apply {
            putString("selectedDate", selectedDate)
        }
        mapFragment.arguments = bundle

        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()
    }
}
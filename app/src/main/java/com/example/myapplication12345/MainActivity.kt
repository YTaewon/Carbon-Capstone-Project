package com.example.myapplication12345

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
<<<<<<< HEAD
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication12345.AI.AI
import com.example.myapplication12345.AI.AITest
=======
>>>>>>> 614cd7f (실험목적)

// 앱 실행 시 바로 CameraActivity 로 이동
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

<<<<<<< HEAD
        val rankButton = findViewById<Button>(R.id.rankbutton)
        rankButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        val aiButton = findViewById<Button>(R.id.aiTest)
        aiButton.setOnClickListener {
            val intent = Intent(this, AITest::class.java)
            startActivity(intent)
        }

        val ai2Button = findViewById<Button>(R.id.aiTest2)
        aiButton.setOnClickListener {
            val intent = Intent(this, AI::class.java)
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
=======
        // CameraActivity로 이동
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
        finish() // MainActivity 종료
>>>>>>> 614cd7f (실험목적)
    }
}

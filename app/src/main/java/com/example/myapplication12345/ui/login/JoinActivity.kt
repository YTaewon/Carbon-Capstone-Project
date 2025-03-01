package com.example.myapplication12345.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.myapplication12345.R
import com.example.myapplication12345.databinding.ActivityJoinBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

class JoinActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityJoinBinding
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = Firebase.auth
        database = FirebaseDatabase.getInstance().reference
        binding = DataBindingUtil.setContentView(this, R.layout.activity_join)

        binding.joinBtn.setOnClickListener {
            // 가입 처리 로직
            if (isValidInput()) {
                registerUser()
            }
        }
    }

    // 입력값 검증 함수
    private fun isValidInput(): Boolean {
        val email = binding.emailEditText.text.toString()
        val password1 = binding.passwordEditText.text.toString()
        val password2 = binding.confirmPasswordEditText.text.toString()
        val nickname = binding.nicknameEditText.text.toString()

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password1.isEmpty()) {
            Toast.makeText(this, "비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password2.isEmpty()) {
            Toast.makeText(this, "비밀번호 확인을 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        if (nickname.isEmpty()) {
            Toast.makeText(this, "닉네임을 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password1 != password2) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password1.length < 6) {
            Toast.makeText(this, "비밀번호를 6자리 이상 입력해주세요", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // 회원가입 진행 함수
    private fun registerUser() {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()
        val nickname = binding.nicknameEditText.text.toString()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                    // 닉네임과 이메일을 Firebase에 저장
                    val userMap = mapOf(
                        "email" to email,
                        "nickname" to nickname,
                        "score" to 0,
                        "point" to 0
                    )
                    database.child("users").child(userId).setValue(userMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "회원가입 성공", Toast.LENGTH_SHORT).show()
                            navigateToIntro()
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this, "데이터 저장 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 회원가입 성공 후 IntroActivity로 이동
    private fun navigateToIntro() {
        val intent = Intent(this, IntroActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}

package com.example.myapplication12345.ui.sidebar.setting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.myapplication12345.MainActivity
import com.example.myapplication12345.R
import com.example.myapplication12345.databinding.ActivitySettingBinding // View Binding 자동 생성 클래스
import com.example.myapplication12345.ui.login.IntroActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import timber.log.Timber

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var profileImage: ImageView

    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        storage = FirebaseStorage.getInstance()
        database = FirebaseDatabase.getInstance()

        // View Binding 초기화
        binding = ActivitySettingBinding.inflate(layoutInflater)

        setContentView(binding.root) // binding.root를 통해 레이아웃 설정
        profileImage = findViewById<ImageView>(R.id.profile_image)

        val userId = auth.currentUser?.uid
        loadProfileImage(userId.toString());


        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = result.data?.data
                if (selectedImageUri != null) {
                    profileImage.setImageURI(selectedImageUri) // 미리보기
                    uploadImageToFirebase(selectedImageUri!!) // Firebase에 업로드
                }
            }
        }

        // 1. 뒤로가기 버튼 처리
        binding.backButton.setOnClickListener {
            finish() // 현재 Activity 종료
        }

        // 3. 저장 버튼 처리
//        binding.saveButton.setOnClickListener {
//            // 여기에 새로고침 또는 설정 저장 로직 구현
//            Toast.makeText(this, "저장 완료", Toast.LENGTH_SHORT).show()
//        }

        // 4. 프로필 이미지 변경 버튼 처리
        binding.profileImageChange.setOnClickListener {
            openGallery()
            Toast.makeText(this, "프로필 이미지 변경", Toast.LENGTH_SHORT).show()
        }

        // 5. AI 저전력 모드 스위치 처리 (XML ID가 switch_ai_low_power_mode로 변경되었다고 가정)
        binding.switchAiLowPowerMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Toast.makeText(this, "AI 저전력 모드 켜짐", Toast.LENGTH_SHORT).show()
                // 저전력 모드 활성화 로직
            } else {
                Toast.makeText(this, "AI 저전력 모드 꺼짐", Toast.LENGTH_SHORT).show()
                // 저전력 모드 비활성화 로직
            }
        }

        // 6. 계정 관리 클릭 처리
        binding.settingAccountManagement.setOnClickListener {
            // 계정 관리 화면으로 이동하는 인텐트 생성 및 시작
            Toast.makeText(this, "계정 관리", Toast.LENGTH_SHORT).show()
            // 예: startActivity(Intent(this, AccountManagementActivity::class.java))
        }

        // 7. 앱 정보 클릭 처리
        binding.settingAppInfo.setOnClickListener {
            // 앱 정보 화면으로 이동하는 인텐트 생성 및 시작
            Toast.makeText(this, "Made by Team 호라이즌", Toast.LENGTH_SHORT).show()
        }

        // 9. 로그아웃 클릭 처리
        binding.settingLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, IntroActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            Toast.makeText(this, "로그아웃", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {

                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveImageUrlToDatabase(downloadUrl.toString(), userId)
                    Toast.makeText(this, "프로필 이미지가 업로드되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Image upload failed")
                Toast.makeText(this, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 서버에 이미지 URL 저장
    private fun saveImageUrlToDatabase(imageUrl: String, userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Timber.tag("Firebase").d("Profile image URL saved: $imageUrl")
                loadProfileImage(userId) // 즉시 UI 업데이트
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Failed to save image URL")
            }
    }

    // 프로필 이미지 로드
    private fun loadProfileImage(userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.child("profileImageUrl").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val imageUrl = snapshot.getValue(String::class.java)
                if (imageUrl != null) {
                    Glide.with(this@SettingActivity)
                        .load(imageUrl)
                        .placeholder(R.drawable.user) // 기본 이미지
                        .error(R.drawable.user) // 로드 실패 시 기본 이미지
                        .placeholder(R.drawable.user)
                        .error(R.drawable.user)
                        .into(profileImage)

                } else {
                    profileImage.setImageResource(R.drawable.user) // 기본 이미지 설정
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.tag("Firebase").w(error.toException(), "loadProfileImage:onCancelled")
                profileImage.setImageResource(R.drawable.user)
            }
        })
    }

}
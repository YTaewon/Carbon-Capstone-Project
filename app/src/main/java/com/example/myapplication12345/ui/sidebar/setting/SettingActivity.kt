package com.example.myapplication12345.ui.sidebar.setting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.myapplication12345.R
import com.example.myapplication12345.databinding.ActivitySettingBinding
import com.example.myapplication12345.ui.login.IntroActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
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

    // 1. DB 참조와 리스너를 멤버 변수로 분리합니다.
    private lateinit var userProfileRef: DatabaseReference
    private lateinit var profileListener: ValueEventListener

    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase 인스턴스 초기화
        auth = Firebase.auth
        storage = FirebaseStorage.getInstance()
        database = FirebaseDatabase.getInstance()

        // 현재 사용자 ID 가져오기
        val userId = auth.currentUser?.uid
        // 로그인된 사용자가 없으면 액티비티 종료
        if (userId == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. DB 참조 객체를 미리 생성합니다.
        userProfileRef = database.getReference("users").child(userId).child("profileImageUrl")

        setupClickListeners()
        setupGalleryLauncher()
    }

    override fun onStart() {
        super.onStart()
        // 3. 액티비티가 화면에 보일 때 리스너를 정의하고 등록합니다.
        // 이렇게 하면 화면이 보일 때마다 항상 최신 프로필 이미지를 가져옵니다.
        profileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // (안전장치) Glide 호출 전에 액티비티가 파괴되었는지 확인
                if (isFinishing || isDestroyed) {
                    return
                }

                val imageUrl = snapshot.getValue(String::class.java)
                Glide.with(this@SettingActivity)
                    .load(imageUrl)
                    .placeholder(R.drawable.user) // 로딩 중에 보여줄 이미지
                    .error(R.drawable.user)       // 로드 실패 시 보여줄 이미지
                    .into(binding.profileImage)
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.tag("Firebase").w(error.toException(), "loadProfileImage:onCancelled")
                binding.profileImage.setImageResource(R.drawable.user)
            }
        }
        userProfileRef.addValueEventListener(profileListener)
    }

    override fun onStop() {
        super.onStop()
        // 4. (가장 중요) 액티비티가 화면에서 사라질 때 리스너를 반드시 제거합니다.
        // 이렇게 하지 않으면 액티비티가 파괴된 후에도 콜백이 호출되어 크래시가 발생합니다.
        userProfileRef.removeEventListener(profileListener)
    }

    private fun setupGalleryLauncher() {
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = result.data?.data
                if (selectedImageUri != null) {
                    binding.profileImage.setImageURI(selectedImageUri) // 미리보기
                    uploadImageToFirebase(selectedImageUri!!) // Firebase에 업로드
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.profileImageChange.setOnClickListener {
            openGallery()
        }

        binding.switchAiLowPowerMode.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "AI 저전력 모드 켜짐" else "AI 저전력 모드 꺼짐"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding.settingAccountManagement.setOnClickListener {
            Toast.makeText(this, "계정 관리", Toast.LENGTH_SHORT).show()
        }

        binding.settingAppInfo.setOnClickListener {
            Toast.makeText(this, "Made by Team 호라이즌", Toast.LENGTH_SHORT).show()
        }

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
                }
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Image upload failed")
                Toast.makeText(this, "이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveImageUrlToDatabase(imageUrl: String, userId: String) {
        // 이미지 URL을 저장하기만 하면, onStart()에서 등록한 profileListener가
        // 변경을 자동으로 감지하여 UI를 업데이트하므로 여기서 UI를 직접 업데이트할 필요가 없습니다.
        database.getReference("users").child(userId).child("profileImageUrl").setValue(imageUrl)
            .addOnSuccessListener {
                Timber.tag("Firebase").d("Profile image URL saved: $imageUrl")
                Toast.makeText(this, "프로필 이미지가 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Timber.tag("Firebase").e(e, "Failed to save image URL")
            }
    }
}
package com.example.myapplication12345

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.myapplication12345.AI.SensorDataService
import com.example.myapplication12345.ui.login.IntroActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import timber.log.Timber

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // 1. 최신 권한 처리 방식: ActivityResultLauncher 사용
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 모든 권한이 허용되었는지 확인
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Timber.tag("SplashActivity").d("모든 권한이 허용되었습니다.")
            checkUserAndProceed()
        } else {
            Timber.tag("SplashActivity").w("일부 권한이 거부되었습니다.")
            // 거부된 권한에 대해 사용자에게 설명 후 재요청 또는 앱 종료
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 2. 시스템 스플래시 스크린 API 설치
        installSplashScreen()

        super.onCreate(savedInstanceState)
        // 주의: 이 액티비티는 UI를 보여주지 않으므로 setContentView()를 호출하지 않습니다.
        // 호출하면 빈 화면이 잠깐 보일 수 있습니다.

        auth = Firebase.auth
        Timber.plant(Timber.DebugTree())

        // 3. 권한 확인 및 요청
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = SensorDataService.getRequiredPermissions(this)

        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            Timber.tag("SplashActivity").d("모든 권한이 이미 있습니다. 인증 절차로 진행합니다.")
            checkUserAndProceed()
        } else {
            Timber.tag("SplashActivity").d("필요한 권한을 요청합니다.")
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    // 5. 필요한 권한 목록을 반환하는 헬퍼 함수
    private fun getRequiredPermissions(): List<String> {
        return mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION) // 보통 FINE만 있어도 충분

            // 안드로이드 14+ (API 34)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            // 안드로이드 13 (API 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            // 안드로이드 10 (API 29)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }

            // ACCESS_WIFI_STATE와 READ_PHONE_STATE는 매우 민감한 정보.
            // 정말 필요한지 재고해보고, 필요하다면 추가.
            // add(Manifest.permission.ACCESS_WIFI_STATE)
            // add(Manifest.permission.READ_PHONE_STATE)

            // WRITE_EXTERNAL_STORAGE는 API 29부터 거의 필요 없음.
            // 안드로이드 13 미만 미디어 접근은 READ_EXTERNAL_STORAGE로 충분.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun checkUserAndProceed() {
        if (auth.currentUser == null) {
            Timber.tag("SplashActivity").d("사용자 정보 없음. IntroActivity로 이동합니다.")
            navigateTo(IntroActivity::class.java)
        } else {
            Timber.tag("SplashActivity").d("사용자 정보 있음. 서비스 시작 및 MainActivity로 이동합니다.")
            startSensorService()
            setDailyAlarm()
            navigateTo(MainActivity::class.java)
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("앱의 원활한 작동을 위해 모든 권한이 필요합니다. 권한을 허용하지 않으면 앱을 사용할 수 없습니다. 설정 화면으로 이동하여 권한을 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                // 사용자가 직접 설정 화면으로 가서 권한을 켜도록 유도
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                finish() // 설정 화면 이동 후 액티비티 종료
            }
            .setNegativeButton("종료") { _, _ ->
                Timber.tag("SplashActivity").e("필수 권한 거부로 앱을 종료합니다.")
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setDailyAlarm() {
        val alarmIntent = Intent(this, AlarmBootReceiver::class.java).apply {
            action = "SET_ALARM"
        }
        sendBroadcast(alarmIntent)
    }

    private fun startSensorService() {
        val serviceIntent = Intent(this, SensorDataService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent) // API 26+ 호환
        Timber.tag("SplashActivity").d("SensorDataService 시작 요청")
    }

    // 6. 화면 전환을 위한 헬퍼 함수
    private fun navigateTo(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish() // SplashActivity는 항상 종료
    }
}
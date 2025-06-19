package com.example.myapplication12345

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication12345.ui.login.IntroActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import timber.log.Timber
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.myapplication12345.AI.SensorDataService
import java.util.ArrayList

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val PERMISSION_REQUEST_CODE = 1
    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        auth = Firebase.auth

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

//        Timber.plant(Timber.DebugTree())

        // 권한 확인 및 즉시 진행
        if (!hasPermissions()) {
            Timber.tag(TAG).d("권한 확인 실패, 요청 시작")
            requestPermissions()
        } else {
            Timber.tag(TAG).d("모든 권한 확인됨, 인증 상태 확인으로 진행")
            checkUserAndProceed()
        }
    }

    private fun checkUserAndProceed() {
        if (auth.currentUser?.uid == null) {
            Timber.tag(TAG).d("User is null")
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
        } else {
            Timber.tag(TAG).d("User is not null")
            Timber.tag(TAG).d("모든 권한 확인됨, 서비스 시작")
            startSensorService()
            setDailyAlarm()
            navigateToMain()
        }
    }

    private fun setDailyAlarm() {
        val alarmIntent = Intent(this, AlarmBootReceiver::class.java).apply {
            action = "SET_ALARM"
        }
        sendBroadcast(alarmIntent)
    }


    private fun hasPermissions(): Boolean {
        val requiredPermissions = ArrayList<String>().apply {
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }


        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }.also { allGranted ->
            if (!allGranted) {
                requiredPermissions.forEach { permission ->
                    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                        Timber.tag(TAG).w("$permission 권한 없음")
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = ArrayList<String>().apply {
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )

        // 정확한 알람 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Timber.tag(TAG).d("모든 권한 허용됨")
                checkUserAndProceed()
            } else {
                Timber.tag(TAG).w("일부 권한 거부됨")
                grantResults.forEachIndexed { index, result ->
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Timber.tag(TAG).w("${permissions[index]} 거부됨")
                    }
                }
                showPermissionRationale()
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun showPermissionRationale() {
        val message = when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> {
                "이 앱은 Wi-Fi, 위치, 전화 상태, 저장소 권한이 필요합니다. 권한을 허용하지 않으면 데이터 수집이 불가능합니다. 다시 요청하시겠습니까?"
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                "이 앱은 Wi-Fi, 위치, 전화 상태, 미디어 읽기 권한이 필요합니다. 권한을 허용하지 않으면 데이터 수집이 불가능합니다. 다시 요청하시겠습니까?"
            }
            else -> {
                "이 앱은 Wi-Fi, 위치, 전화 상태 권한이 필요합니다. 권한을 허용하지 않으면 데이터 수집이 불가능합니다. 다시 요청하시겠습니까?"
            }
        }

        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage(message)
            .setPositiveButton("다시 요청") { _, _ ->
                Timber.tag(TAG).d("권한 재요청 선택됨")
                requestPermissions()
            }
            .setNegativeButton("종료") { _, _ ->
                Timber.tag(TAG).e("필수 권한 거부로 앱 종료")
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startSensorService() {
        val serviceIntent = Intent(this, SensorDataService::class.java)
        startService(serviceIntent)
        Timber.tag(TAG).d("SensorDataService 시작 요청")
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
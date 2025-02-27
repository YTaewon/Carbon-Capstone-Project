package com.example.myapplication12345.AI;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import com.example.myapplication12345.R;

import java.util.ArrayList;

import timber.log.Timber;

public class AITest extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "AITest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 버전에 따른 권한 확인 및 요청
        if (!hasPermissions()) {
            Timber.tag(TAG).d("권한 확인 실패, 요청 시작");
            requestPermissions();
        } else {
            Timber.tag(TAG).d("모든 권한 확인됨, 서비스 시작");
            startSensorService();
        }
        //모델 태스트용
        //PyTorchHelper.runPrediction(this);
    }

    private boolean hasPermissions() {
        ArrayList<String> requiredPermissions = new ArrayList<>();

        // 공통 권한 추가 (모든 버전에서 필요)
        requiredPermissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredPermissions.add(Manifest.permission.READ_PHONE_STATE);

        // API 32 이하에서만 저장소 권한 필요
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // API 32 이하 (Android 12L)
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // API 33 이상에서 미디어 권한 추가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상 (Android 13)
            requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            // 필요 시 READ_MEDIA_VIDEO, READ_MEDIA_AUDIO 추가
        }

        boolean allGranted = true;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Timber.tag(TAG).w("%s 권한 없음", permission);
                allGranted = false;
            }
        }
        return allGranted;
    }

    private void requestPermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        // 공통 권한
        permissionsToRequest.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);

        // API 32 이하에서 저장소 권한
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // API 32 이하
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // API 33 이상에서 미디어 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            // 필요 시 READ_MEDIA_VIDEO, READ_MEDIA_AUDIO 추가
        }

        ActivityCompat.requestPermissions(this,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Timber.tag(TAG).w("%s 거부됨", permissions[i]);
                    allGranted = false;
                }
            }
            if (allGranted) {
                Timber.tag(TAG).d("모든 권한 허용됨");
                startSensorService();
            } else {
                Timber.tag(TAG).w("일부 권한 거부됨");
                showPermissionRationale();
            }
        }
    }

    private void showPermissionRationale() {
        String message;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // API 32 이하
             message = "이 앱은 Wi-Fi, 위치, 전화 상태, 저장소 권한이 필요합니다. 권한을 허용하지 않으면 데이터 수집이 불가능합니다. 다시 요청하시겠습니까?";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 이상
            message = "이 앱은 Wi-Fi, 위치, 전화 상태, 미디어 읽기 권한이 필요합니다. 권한을 허용하지 않으면 데이터 수집이 불가능합니다. 다시 요청하시겠습니까?";
        } else { // API 29~32
            message = "이 앱은 Wi-Fi, 위치, 전화 상태 권한이 필요합니다. 권한을 허용하지 않으면 데이터 수집이 불가능합니다. 다시 요청하시겠습니까?";
        }

        new AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage(message)
                .setPositiveButton("다시 요청", (dialog, which) -> {
                    Timber.tag(TAG).d("권한 재요청 선택됨");
                    requestPermissions();
                })
                .setNegativeButton("종료", (dialog, which) -> {
                    Timber.tag(TAG).e("필수 권한 거부로 앱 종료");
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void startSensorService() {
        Intent serviceIntent = new Intent(this, SensorDataService.class);
        startService(serviceIntent);
        SensorDataProcessor.scheduleBackgroundPrediction(this);
        Timber.tag(TAG).d("SensorDataService 시작");
        Timber.tag(TAG).d("SensorDataProcessor 시작");

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new MapFragment())
                .commit();
    }
}
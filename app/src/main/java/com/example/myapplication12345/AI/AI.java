package com.example.myapplication12345.AI;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.myapplication12345.R;

public class AI extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100; // 요청 코드
    private boolean isScanning = false;
    private TextView resultTextView;
    private Button startButton;
    private Preprocessing preprocessing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_test);

        resultTextView = findViewById(R.id.resultTextView);
        startButton = findViewById(R.id.startButton);
        preprocessing = new Preprocessing(this);

        // 필요한 권한 요청
        checkAndRequestPermissions();

        // 버튼 클릭 시 WiFi 스캔 실행
        startButton.setOnClickListener(view -> {
            if (hasAllPermissions()) {
                if (!isScanning) {
                    isScanning = true;
                    startButton.setText("Scanning...");
                    startWiFiScan();
                } else {
                    isScanning = false;
                    startButton.setText("Start WiFi Scan");
                    resultTextView.setText("WiFi Scanning Stopped.");
                }
            } else {
                Toast.makeText(this, "WiFi 스캔을 실행하려면 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                checkAndRequestPermissions();
            }
        });
    }

    /**
     * 📌 모든 권한이 허용되었는지 확인
     */
    private boolean hasAllPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.CAMERA
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 📌 필요한 권한을 확인하고 사용자에게 요청
     */
    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.CAMERA
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 📌 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            Map<String, Integer> permissionResults = new HashMap<>();
            boolean allGranted = true;

            for (int i = 0; i < permissions.length; i++) {
                permissionResults.put(permissions[i], grantResults[i]);
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "모든 권한이 승인되었습니다!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "일부 권한이 거부되었습니다. 설정에서 허용해주세요.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 📌 WiFi 스캔 실행 (버튼 클릭 시)
     */
    private void startWiFiScan() {
        new Thread(() -> {
            while (isScanning) {
                preprocessing.processAPData();

                runOnUiThread(() -> {
                    String latestResult = preprocessing.getLastResult();
                    resultTextView.setText(latestResult);
                });

                try {
                    Thread.sleep(10 * 1000); // 10초마다 WiFi 데이터 수집
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
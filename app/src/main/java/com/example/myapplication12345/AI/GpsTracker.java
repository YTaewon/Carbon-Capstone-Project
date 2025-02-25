package com.example.myapplication12345.AI;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;

public class GpsTracker {
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location previousLocation;
    private long previousTime;
    private LocationUpdateCallback locationUpdateCallback;
    private static final String TAG = "GpsTracker";

    public interface LocationUpdateCallback {
        void onLocationUpdated(Location location, float speed);
    }

    public GpsTracker(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        initializeLocationUpdates();
        getLastLocation(); // 앱 실행 시 마지막 위치 가져오기
    }

    private void initializeLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    long currentTime = System.currentTimeMillis();
                    float speed = 0;

                    if (previousLocation != null) {
                        float distance = previousLocation.distanceTo(location);
                        long timeElapsed = (currentTime - previousTime) / 1000;
                        speed = (timeElapsed > 0) ? (distance / timeElapsed) : 0;
                    }

                    previousLocation = location;
                    previousTime = currentTime;

                    Log.d(TAG, "새로운 위치 업데이트: " + location.getLatitude() + ", " + location.getLongitude() + " 속도: " + speed);

                    if (locationUpdateCallback != null) {
                        locationUpdateCallback.onLocationUpdated(location, speed);
                    }
                }
            }
        };
    }

    public void setLocationUpdateCallback(LocationUpdateCallback callback) {
        this.locationUpdateCallback = callback;
    }

    @SuppressLint("MissingPermission")
    public void startTracking() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "위치 권한이 부족하여 추적을 시작할 수 없습니다.");
            return;
        }
        Log.d(TAG, "GPS 추적 시작");
        fusedLocationClient.requestLocationUpdates(LocationRequest.create(), locationCallback, null);
    }

    public void stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        Log.d(TAG, "GPS 추적 중지");
    }

    @SuppressLint("MissingPermission")
    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "위치 권한이 없어 마지막 위치를 가져올 수 없습니다.");
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                previousLocation = location;
                Log.d(TAG, "마지막 위치 가져옴: " + location.getLatitude() + ", " + location.getLongitude());
            } else {
                Log.d(TAG, "마지막 위치 없음");
            }
        }).addOnFailureListener(e -> Log.e(TAG, "마지막 위치 가져오기 실패: " + e.getMessage()));
    }

    public Location getLastKnownLocation() {
        return previousLocation;
    }
}

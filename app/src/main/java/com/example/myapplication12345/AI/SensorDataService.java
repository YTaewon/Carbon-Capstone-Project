package com.example.myapplication12345.AI;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SensorDataService extends Service {
    private static final int PROCESS_INTERVAL_01 = 1000; // 1초 간격
    private static final int IMU_INTERVAL_MS = 10; // 10ms 간격
    private static final int MAX_IMU_PER_SECOND = 100; // 1초에 최대 25개
    private static final int INITIAL_DELAY_MS = 3000; // 최초 3초 지연
    private static final String TAG = "SensorDataService";

    private WifiManager wifiManager;
    private TelephonyManager telephonyManager;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private SensorManager sensorManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    private String currentDate;

    @Override
    public void onCreate() {
        super.onCreate();
        currentDate = dateFormat.format(new Date());

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        if (!checkPermissions()) {
            Log.e(TAG, "필수 권한이 없음. 서비스 중단");
            stopSelf();
            return;
        }

        handler.postDelayed(this::startDataCollection, INITIAL_DELAY_MS);
    }

    private boolean checkPermissions() {
        boolean hasWifiPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasPhoneStatePermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;

        return hasWifiPermission && hasLocationPermission && hasPhoneStatePermission;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startDataCollection() {
        Runnable dataCollectionRunnable = new Runnable() {
            @Override
            public void run() {
                long timestamp = System.currentTimeMillis();
                collectIMUData(timestamp);
                collectAPData(timestamp);
                collectBTSData(timestamp);
                collectGPSData(timestamp);
                handler.postDelayed(this, PROCESS_INTERVAL_01);
            }
        };
        handler.post(dataCollectionRunnable);
    }

    private void collectAPData(long timestamp) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                if (wifiManager != null) {
                    List<ScanResult> scanResults = wifiManager.getScanResults();
                    if (!scanResults.isEmpty()) {
                        ScanResult scanResult = scanResults.get(0);
                        String ssid = "UNKNOWN_SSID";
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            ssid = wifiInfo.getSSID();
                        }
                        Map<String, Object> data = new LinkedHashMap<>(6);
                        data.put("timestamp", timestamp);
                        data.put("bssid", scanResult.BSSID); // String 타입 보장
                        data.put("ssid", ssid); // String 타입 보장
                        data.put("level", (float) scanResult.level); // 명시적 Float
                        data.put("frequency", (float) scanResult.frequency); // 명시적 Float
                        data.put("capabilities", scanResult.capabilities); // String 타입 보장
                        saveToCSV("AP", data);
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Wi-Fi 데이터 수집 실패: 권한 부족", e);
            }
        }
    }

    private void collectBTSData(long timestamp) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                if (telephonyManager != null) {
                    List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                    for (CellInfo cellInfo : cellInfoList) {
                        if (cellInfo instanceof CellInfoLte) {
                            CellIdentityLte cellIdentity = ((CellInfoLte) cellInfo).getCellIdentity();
                            Map<String, Object> data = new LinkedHashMap<>(3);
                            data.put("timestamp", timestamp);
                            data.put("ci", cellIdentity.getCi());
                            data.put("pci", cellIdentity.getPci());
                            saveToCSV("BTS", data);
                        }
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "BTS 데이터 수집 실패: 권한 부족", e);
            }
        }
    }

    private void collectGPSData(long timestamp) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    Map<String, Object> data = new LinkedHashMap<>(3);
                    data.put("timestamp", timestamp);
                    data.put("latitude", location.getLatitude());
                    data.put("longitude", location.getLongitude());
                    saveToCSV("GPS", data);
                } else {
                    Log.w(TAG, "GPS 위치 데이터 없음");
                }
            }).addOnFailureListener(e -> Log.e(TAG, "GPS 데이터 수집 실패: " + e.getMessage()));
        }
    }

    private void collectIMUData(long timestamp) {
        if (sensorManager == null) {
            Log.e(TAG, "SensorManager가 초기화되지 않음");
            return;
        }

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        Sensor gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        Sensor linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        if (accelerometer == null || gyroscope == null || magnetometer == null ||
                rotationVector == null || pressureSensor == null ||
                gravitySensor == null || linearAccelSensor == null) {
            Log.e(TAG, "필요한 센서가 장치에 없음");
            return;
        }

        final List<Map<String, Object>> imuDataBuffer = new ArrayList<>(MAX_IMU_PER_SECOND);
        final long startTime = timestamp;

        class SensorDataHolder {
            float[] accel = new float[3];
            float[] gyro = new float[3];
            float[] mag = new float[3];
            float[] rot = new float[4];
            float[] pressure = new float[1];
            float[] gravity = new float[3];
            float[] linear = new float[3];
            boolean accelSet, gyroSet, magSet, rotSet, pressureSet, gravitySet, linearSet;

            void update(SensorEvent event) {
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        System.arraycopy(event.values, 0, accel, 0, 3);
                        accelSet = true;
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        System.arraycopy(event.values, 0, gyro, 0, 3);
                        gyroSet = true;
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        System.arraycopy(event.values, 0, mag, 0, 3);
                        magSet = true;
                        break;
                    case Sensor.TYPE_ROTATION_VECTOR:
                        System.arraycopy(event.values, 0, rot, 0, Math.min(event.values.length, 4));
                        rotSet = true;
                        break;
                    case Sensor.TYPE_PRESSURE:
                        pressure[0] = event.values[0];
                        pressureSet = true;
                        break;
                    case Sensor.TYPE_GRAVITY:
                        System.arraycopy(event.values, 0, gravity, 0, 3);
                        gravitySet = true;
                        break;
                    case Sensor.TYPE_LINEAR_ACCELERATION:
                        System.arraycopy(event.values, 0, linear, 0, 3);
                        linearSet = true;
                        break;
                }
            }

            boolean isAllSet() {
                return accelSet && gyroSet && magSet && rotSet && pressureSet && gravitySet && linearSet;
            }
        }

        final SensorDataHolder sensorData = new SensorDataHolder();

        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                sensorData.update(event);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, linearAccelSensor, SensorManager.SENSOR_DELAY_NORMAL);

        Runnable imuCollector = new Runnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= MAX_IMU_PER_SECOND || System.currentTimeMillis() - startTime >= 1000) {
                    sensorManager.unregisterListener(listener);
                    if (imuDataBuffer.isEmpty()) {
                        Log.w(TAG, "IMU 데이터 버퍼가 비어 있음");
                    } else {
                        Log.d(TAG, "IMU 데이터 저장: " + imuDataBuffer.size() + "개");
                        for (Map<String, Object> data : imuDataBuffer) {
                            saveToCSV("IMU", data);
                        }
                    }
                    imuDataBuffer.clear();
                    return;
                }

                if (sensorData.isAllSet()) {
                    Map<String, Object> data = new LinkedHashMap<>(20);
                    data.put("timestamp", startTime);
                    data.put("accel.x", sensorData.accel[0]);
                    data.put("accel.y", sensorData.accel[1]);
                    data.put("accel.z", sensorData.accel[2]);
                    data.put("gyro.x", sensorData.gyro[0]);
                    data.put("gyro.y", sensorData.gyro[1]);
                    data.put("gyro.z", sensorData.gyro[2]);
                    data.put("mag.x", sensorData.mag[0]);
                    data.put("mag.y", sensorData.mag[1]);
                    data.put("mag.z", sensorData.mag[2]);
                    float[] quat = new float[4];
                    SensorManager.getQuaternionFromVector(quat, sensorData.rot);
                    data.put("rot.w", quat[0]);
                    data.put("rot.x", quat[1]);
                    data.put("rot.y", quat[2]);
                    data.put("rot.z", quat[3]);
                    data.put("pressure", sensorData.pressure[0]);
                    data.put("gravity.x", sensorData.gravity[0]);
                    data.put("gravity.y", sensorData.gravity[1]);
                    data.put("gravity.z", sensorData.gravity[2]);
                    data.put("linear_accel.x", sensorData.linear[0]);
                    data.put("linear_accel.y", sensorData.linear[1]);
                    data.put("linear_accel.z", sensorData.linear[2]);

                    imuDataBuffer.add(data);
                    count++;
                }
                handler.postDelayed(this, IMU_INTERVAL_MS);
            }
        };

        handler.post(imuCollector);
    }

    private void saveToCSV(String sensorType, Map<String, Object> data) {
        executorService.execute(() -> {
            try {
                File directory = new File(getExternalFilesDir(null), "SensorData");
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                String currentDate = dateFormat.format(new Date());
                String fileName = currentDate + "_" + sensorType + ".csv";
                File file = new File(directory, fileName);
                boolean needsHeader = !file.exists() || file.length() == 0;

                if (!data.containsKey("timestamp") || data.get("timestamp") == null) {
                    Log.e(TAG, sensorType + " 데이터에 timestamp 누락");
                    return;
                }

                try (FileWriter writer = new FileWriter(file, true)) {
                    if (needsHeader) {
                        String header = String.join(",", data.keySet());
                        writer.append(header).append("\n");
                        Log.d(TAG, sensorType + " CSV 헤더 기록: " + header);
                    }
                    StringBuilder line = new StringBuilder(data.size() * 10);
                    for (Object value : data.values()) {
                        if (line.length() > 0) line.append(",");
                        // timestamp는 소수점 없이 기록
                        if (value instanceof Long) {
                            line.append(Long.toString((Long) value));
                        } else {
                            line.append(value != null ? value.toString() : "null");
                        }
                    }
                    writer.append(line.toString()).append("\n");
//                    Log.d(TAG, sensorType + " CSV 데이터 기록: " + line.toString());
                }
            } catch (IOException e) {
                Log.e(TAG, "CSV 저장 실패: " + sensorType, e);
            }
        });
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executorService.shutdown();
    }
}
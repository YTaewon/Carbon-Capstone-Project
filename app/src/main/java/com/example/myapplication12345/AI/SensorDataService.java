package com.example.myapplication12345.AI;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication12345.R;
import com.example.myapplication12345.SplashActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

public class SensorDataService extends Service implements SensorEventListener {
    // --- 상수 정의 ---
    private static final long DATA_PROCESS_INTERVAL_MS = 1000;
    private static final int SENSOR_SAMPLING_PERIOD_US = 10000;
    private static final int IMU_SAMPLE_INTERVAL_MS = 10;
    private static final int MAX_IMU_SAMPLES_PER_SECOND = 100;
    private static final int MIN_UNIQUE_TIMESTAMPS = 60;
    // ================== [변경] 초기 웜업 시간 추가 ==================
    private static final int INITIAL_WARMUP_DELAY_MS = 3000; // 3초의 준비 시간
    // ==============================================================
    private static final String TAG = "SensorDataService";
    private static final String NOTIFICATION_CHANNEL_ID = "sensor_service_channel";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STOP_SERVICE = "com.example.myapplication12345.AI.ACTION_STOP_SERVICE";

    // ... (다른 멤버 변수들은 이전과 동일)
    private WifiManager wifiManager;
    private TelephonyManager telephonyManager;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private SensorManager sensorManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<Map<String, Object>> gpsBuffer = new ArrayList<>();
    private final List<Map<String, Object>> apBuffer = new ArrayList<>();
    private final List<Map<String, Object>> btsBuffer = new ArrayList<>();
    private final List<Map<String, Object>> imuBuffer = new ArrayList<>();
    private final Object bufferLock = new Object();
    private final Set<Long> uniqueTimestamps = new HashSet<>();
    private SensorDataProcessor dataProcessor;
    private LocationCallback locationCallback;
    private Location lastKnownLocation;
    private Sensor accelerometer, gyroscope, magnetometer, rotationVector, pressureSensor, gravitySensor, linearAccelSensor;
    private final float[] accelValues = new float[3];
    private final float[] gyroValues = new float[3];
    private final float[] magValues = new float[3];
    private final float[] rotValues = new float[4];
    private final float[] pressureValues = new float[1];
    private final float[] gravityValues = new float[3];
    private final float[] linearValues = new float[3];


    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIFICATION_ID, createForegroundNotification());

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        if (!checkPermissions()) {
            Timber.tag(TAG).e("필수 권한이 없어 서비스를 중단합니다.");
            stopSelf();
            return;
        }

        executorService.execute(() -> dataProcessor = SensorDataProcessor.getInstance(this));

        // ================== [변경] 초기 위치 정보 즉시 요청 추가 ==================
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    lastKnownLocation = location;
                    Timber.d("초기 마지막 위치 확보: Lat: %f, Lon: %f", location.getLatitude(), location.getLongitude());
                }
            });
        }
        // ======================================================================

        startDataCollection();
    }

    private void startDataCollection() {
        Timber.tag(TAG).d("데이터 수집을 시작합니다. (웜업 대기: %dms)", INITIAL_WARMUP_DELAY_MS);
        startGPSUpdates();
        startIMUListener();

        // ================== [변경] 초기 지연 시간을 두고 수집 루프 시작 ==================
        handler.postDelayed(dataCollectionRunnable, INITIAL_WARMUP_DELAY_MS);
        // ===========================================================================
    }

    private final Runnable dataCollectionRunnable = new Runnable() {
        @Override
        public void run() {
            long timestamp = System.currentTimeMillis();
            Timber.d("dataCollectionRunnable 실행. Timestamp: %d", timestamp);

            collectGPSData(timestamp);
            collectAPData(timestamp);
            collectBTSData(timestamp);
            collectIMUDataForOneSecond(timestamp);

            synchronized (bufferLock) {
                uniqueTimestamps.add(timestamp);
                if (uniqueTimestamps.size() >= MIN_UNIQUE_TIMESTAMPS) {
                    processBuffers();
                }
            }
            handler.postDelayed(this, DATA_PROCESS_INTERVAL_MS);
        }
    };

    // ... 나머지 코드는 이전과 동일합니다 ...
    private void startGPSUpdates() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            LocationRequest locationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, DATA_PROCESS_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(DATA_PROCESS_INTERVAL_MS / 2)
                    .build();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    lastKnownLocation = locationResult.getLastLocation();
                }
            };
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void startIMUListener() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        if (accelerometer == null || gyroscope == null || magnetometer == null) {
            Timber.tag(TAG).e("필수 IMU 센서(가속도, 자이로, 지자기)가 없습니다.");
            return;
        }

        Timber.d("IMU 센서 리스너를 등록합니다.");
        sensorManager.registerListener(this, accelerometer, SENSOR_SAMPLING_PERIOD_US);
        sensorManager.registerListener(this, gyroscope, SENSOR_SAMPLING_PERIOD_US);
        sensorManager.registerListener(this, magnetometer, SENSOR_SAMPLING_PERIOD_US);
        if (rotationVector != null) sensorManager.registerListener(this, rotationVector, SENSOR_SAMPLING_PERIOD_US);
        if (pressureSensor != null) sensorManager.registerListener(this, pressureSensor, SENSOR_SAMPLING_PERIOD_US);
        if (gravitySensor != null) sensorManager.registerListener(this, gravitySensor, SENSOR_SAMPLING_PERIOD_US);
        if (linearAccelSensor != null) sensorManager.registerListener(this, linearAccelSensor, SENSOR_SAMPLING_PERIOD_US);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    System.arraycopy(event.values, 0, accelValues, 0, 3);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    System.arraycopy(event.values, 0, gyroValues, 0, 3);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    System.arraycopy(event.values, 0, magValues, 0, 3);
                    break;
                case Sensor.TYPE_ROTATION_VECTOR:
                    int rotLength = Math.min(event.values.length, 4);
                    System.arraycopy(event.values, 0, rotValues, 0, rotLength);
                    break;
                case Sensor.TYPE_PRESSURE:
                    pressureValues[0] = event.values[0];
                    break;
                case Sensor.TYPE_GRAVITY:
                    System.arraycopy(event.values, 0, gravityValues, 0, 3);
                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    System.arraycopy(event.values, 0, linearValues, 0, 3);
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void collectIMUDataForOneSecond(final long timestamp) {
        final AtomicInteger seqCounter = new AtomicInteger(0);

        Runnable imuSampler = new Runnable() {
            @Override
            public void run() {
                int currentSeq = seqCounter.getAndIncrement();
                if (currentSeq >= MAX_IMU_SAMPLES_PER_SECOND) {
                    return;
                }

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("timestamp", timestamp);
                data.put("seq", currentSeq);

                synchronized (SensorDataService.this) {
                    data.put("accel.x", accelValues[0]);
                    data.put("accel.y", accelValues[1]);
                    data.put("accel.z", accelValues[2]);
                    data.put("gyro.x", gyroValues[0]);
                    data.put("gyro.y", gyroValues[1]);
                    data.put("gyro.z", gyroValues[2]);
                    data.put("mag.x", magValues[0]);
                    data.put("mag.y", magValues[1]);
                    data.put("mag.z", magValues[2]);

                    float[] quat = new float[4];
                    SensorManager.getQuaternionFromVector(quat, rotValues);
                    data.put("rot.w", quat[0]);
                    data.put("rot.x", quat[1]);
                    data.put("rot.y", quat[2]);
                    data.put("rot.z", quat[3]);

                    data.put("pressure", pressureValues[0]);
                    data.put("gravity.x", gravityValues[0]);
                    data.put("gravity.y", gravityValues[1]);
                    data.put("gravity.z", gravityValues[2]);
                    data.put("linear_accel.x", linearValues[0]);
                    data.put("linear_accel.y", linearValues[1]);
                    data.put("linear_accel.z", linearValues[2]);
                }

                synchronized (bufferLock) {
                    imuBuffer.add(data);
                }

                if (currentSeq < MAX_IMU_SAMPLES_PER_SECOND - 1) {
                    handler.postDelayed(this, IMU_SAMPLE_INTERVAL_MS);
                }
            }
        };
        handler.post(imuSampler);
    }
    private void collectGPSData(long timestamp) {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamp", timestamp);

            if (lastKnownLocation != null) {
                data.put("latitude", lastKnownLocation.getLatitude());
                data.put("longitude", lastKnownLocation.getLongitude());
                data.put("accuracy", lastKnownLocation.getAccuracy());
            } else {
                data.put("latitude", 0.0);
                data.put("longitude", 0.0);
                data.put("accuracy", 0.0f);
            }
            synchronized (bufferLock) {
                gpsBuffer.add(data);
            }
        }
    }
    private void collectAPData(long timestamp) {
        if (checkPermission(Manifest.permission.ACCESS_WIFI_STATE) && checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            try {
                if (wifiManager != null) {
                    List<ScanResult> scanResults = wifiManager.getScanResults();
                    if (!scanResults.isEmpty()) {
                        ScanResult scanResult = scanResults.get(0);
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        String ssid = (wifiInfo != null) ? wifiInfo.getSSID() : "UNKNOWN_SSID";

                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("timestamp", timestamp);
                        data.put("bssid", scanResult.BSSID);
                        data.put("ssid", ssid);
                        data.put("level", (float) scanResult.level);
                        data.put("frequency", (float) scanResult.frequency);
                        data.put("capabilities", scanResult.capabilities);
                        synchronized (bufferLock) {
                            apBuffer.add(data);
                        }
                    }
                }
            } catch (SecurityException e) {
                Timber.tag(TAG).e(e, "Wi-Fi 데이터 수집 실패: 권한 부족");
            }
        }
    }
    private void collectBTSData(long timestamp) {
        if (checkPermission(Manifest.permission.READ_PHONE_STATE) && checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            try {
                if (telephonyManager != null) {
                    List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                    for (CellInfo cellInfo : cellInfoList) {
                        if (cellInfo instanceof CellInfoLte && cellInfo.isRegistered()) {
                            CellIdentityLte cellIdentity = ((CellInfoLte) cellInfo).getCellIdentity();
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("timestamp", timestamp);
                            data.put("ci", cellIdentity.getCi());
                            data.put("pci", cellIdentity.getPci());
                            synchronized (bufferLock) {
                                btsBuffer.add(data);
                            }
                            break;
                        }
                    }
                }
            } catch (SecurityException e) {
                Timber.tag(TAG).e(e, "BTS 데이터 수집 실패: 권한 부족");
            }
        }
    }
    private void processBuffers() {
        synchronized (bufferLock) {
            Timber.tag(TAG).d("60초 데이터 처리 시작 - GPS: %d, AP: %d, BTS: %d, IMU: %d",
                    gpsBuffer.size(), apBuffer.size(), btsBuffer.size(), imuBuffer.size());
            if (gpsBuffer.isEmpty() && apBuffer.isEmpty() && btsBuffer.isEmpty() && imuBuffer.isEmpty()) {
                Timber.tag(TAG).w("처리할 데이터가 없어 버퍼를 비우고 계속합니다.");
                uniqueTimestamps.clear();
                return;
            }
            List<Map<String, Object>> gpsDataCopy = new ArrayList<>(gpsBuffer);
            List<Map<String, Object>> apDataCopy = new ArrayList<>(apBuffer);
            List<Map<String, Object>> btsDataCopy = new ArrayList<>(btsBuffer);
            List<Map<String, Object>> imuDataCopy = new ArrayList<>(imuBuffer);
            gpsBuffer.clear();
            apBuffer.clear();
            btsBuffer.clear();
            imuBuffer.clear();
            uniqueTimestamps.clear();
            if (dataProcessor != null) {
                executorService.execute(() -> {
                    dataProcessor.processSensorData(gpsDataCopy, apDataCopy, btsDataCopy, imuDataCopy);
                    Timber.tag(TAG).d("SensorDataProcessor 처리 완료");
                    saveImuDataToCsv(imuDataCopy);
                });
            } else {
                Timber.tag(TAG).w("SensorDataProcessor가 초기화되지 않았습니다.");
            }
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            Timber.tag(TAG).d("서비스 정지 액션 수신. 서비스를 종료합니다.");
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Timber.tag(TAG).d("서비스를 종료합니다.");
        stopDataCollection();
        processBuffers();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        stopForeground(true);
    }
    private void stopDataCollection() {
        handler.removeCallbacksAndMessages(null);
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        if (sensorManager != null) {
            Timber.d("IMU 센서 리스너를 해제합니다.");
            sensorManager.unregisterListener(this);
        }
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
    private boolean checkPermissions() {
        return checkPermission(Manifest.permission.ACCESS_WIFI_STATE) &&
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                checkPermission(Manifest.permission.READ_PHONE_STATE);
    }
    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }
    private Notification createForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "Sensor Service Channel", NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Intent notificationIntent = new Intent(this, SplashActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        Intent stopSelfIntent = new Intent(this, SensorDataService.class);
        stopSelfIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopSelfIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.img_logo)
                .setContentTitle("센서 데이터 수집 서비스")
                .setContentText("백그라운드에서 센서 데이터를 수집 중입니다.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_delete, "서비스 정지", stopPendingIntent)
                .build();
    }
    private void saveImuDataToCsv(List<Map<String, Object>> imuDataList) {
        if (imuDataList == null || imuDataList.isEmpty()) {
            Timber.tag(TAG).w("저장할 IMU 데이터가 없습니다.");
            return;
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "imu_data_" + timeStamp + ".csv";
        File path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (path == null) {
            Timber.tag(TAG).e("외부 저장소 경로를 가져올 수 없습니다.");
            return;
        }
        if (!path.exists()) {
            path.mkdirs();
        }
        File file = new File(path, fileName);
        try (FileWriter fw = new FileWriter(file);
             BufferedWriter bw = new BufferedWriter(fw)) {
            Map<String, Object> firstPoint = imuDataList.get(0);
            StringBuilder header = new StringBuilder();
            for (String key : firstPoint.keySet()) {
                header.append(key).append(",");
            }
            bw.write(header.substring(0, header.length() - 1));
            bw.newLine();
            for (Map<String, Object> dataPoint : imuDataList) {
                StringBuilder row = new StringBuilder();
                for (Object value : dataPoint.values()) {
                    row.append(value.toString()).append(",");
                }
                bw.write(row.substring(0, row.length() - 1));
                bw.newLine();
            }
            Timber.tag(TAG).d("IMU 데이터가 CSV 파일로 성공적으로 저장되었습니다: %s", file.getAbsolutePath());
        } catch (IOException e) {
            Timber.tag(TAG).e(e, "IMU 데이터를 CSV 파일로 저장하는 중 오류 발생");
        }
    }
}
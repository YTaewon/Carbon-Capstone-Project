package com.example.myapplication12345.AI;

import static com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication12345.R;
import com.example.myapplication12345.SplashActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

public class SensorDataService extends Service {
    private static final int PROCESS_INTERVAL_01 = 1000; // 1초 간격
    private static final int IMU_INTERVAL_MS = 10; // 10ms 간격
    private static final int MAX_IMU_PER_SECOND = 200; // 1초에 최대 200개
    private static final int INITIAL_DELAY_MS = 3000; // 최초 3초 지연
    private static final int MIN_TIMESTAMP_COUNT = 60; // 60초(60개의 고유 타임스탬프)
    private static final String TAG = "SensorDataService";
    private static final String NOTIFICATION_CHANNEL_ID = "sensor_service_channel";
    private static final int NOTIFICATION_ID = 1;
    // 서비스 정지 액션 정의
    public static final String ACTION_STOP_SERVICE = "com.example.myapplication12345.AI.ACTION_STOP_SERVICE";

    private WifiManager wifiManager;
    private TelephonyManager telephonyManager;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private SensorManager sensorManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 센서 데이터 버퍼
    private final List<Map<String, Object>> gpsBuffer = new ArrayList<>();
    private final List<Map<String, Object>> apBuffer = new ArrayList<>();
    private final List<Map<String, Object>> btsBuffer = new ArrayList<>();
    private final List<Map<String, Object>> imuBuffer = new ArrayList<>();

    // 고유 타임스탬프 추적
    private final Set<Long> uniqueTimestamps = new HashSet<>();

    private SensorDataProcessor dataProcessor;
    private LocationCallback locationCallback;
    private Location lastKnownLocation;

    @Override
    public void onCreate() {
        super.onCreate();

        startForeground(NOTIFICATION_ID, createForegroundNotification());

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        if (!checkPermissions()) {
            Timber.tag(TAG).e("필수 권한이 없음. 서비스 중단");
            stopSelf();
            return;
        }

        executorService.execute(() -> {
            dataProcessor = SensorDataProcessor.getInstance(this);
            Timber.tag(TAG).d("SensorDataProcessor 초기화 완료 (비동기)");
        });

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            lastKnownLocation = location;
                            Timber.tag(TAG).d("초기 마지막 위치 설정: %f, %f", location.getLatitude(), location.getLongitude());
                        }
                    });
        }

        handler.postDelayed(this::startDataCollection, INITIAL_DELAY_MS);
    }

    private Notification createForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Sensor Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("센서 데이터 수집 서비스 알림");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // 1. 알림 본문을 클릭했을 때 열릴 MainActivity에 대한 Intent 생성
        Intent notificationIntent = new Intent(this, SplashActivity.class);
        // 이미 실행 중인 Activity 스택을 재활용하고, MainActivity를 최상단으로 가져옴
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 2. Intent를 감싸는 PendingIntent 생성
        // FLAG_IMMUTABLE은 Android 12(API 31) 이상에서 필수
        // FLAG_UPDATE_CURRENT는 기존 PendingIntent가 있다면 업데이트하도록 함
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0, // requestCode, 여러 PendingIntent를 구분하기 위한 고유 ID
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // 서비스 정지 Intent 생성
        Intent stopSelfIntent = new Intent(this, SensorDataService.class);
        stopSelfIntent.setAction(ACTION_STOP_SERVICE); // 정의한 액션 설정

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                0, // requestCode, 여러 PendingIntent를 구분하기 위한 고유 ID
                stopSelfIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.img_logo)
                .setContentTitle("센서 데이터 수집 서비스")
                .setContentText("백그라운드에서 센서 데이터를 수집 중입니다.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_delete, "서비스 정지", stopPendingIntent)
                .build();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 인텐트가 null이 아니고, 정지 액션을 포함하는지 확인
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            Timber.tag(TAG).d("정지 요청 액션 수신. 서비스 종료.");
            stopSelf(); // 서비스 종료
            return START_NOT_STICKY; // 서비스가 종료되면 다시 시작하지 않도록 설정
        }
        return START_STICKY; // 기본 동작: 시스템에 의해 종료되면 다시 시작 시도
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startDataCollection() {
        startGPSUpdates();

        Runnable dataCollectionRunnable = new Runnable() {
            @Override
            public void run() {
                long timestamp = System.currentTimeMillis();
                collectIMUData(timestamp);
                collectAPData(timestamp);
                collectBTSData(timestamp);
                collectGPSData(timestamp);

                synchronized (uniqueTimestamps) {
                    uniqueTimestamps.add(timestamp);
                    if (uniqueTimestamps.size() >= MIN_TIMESTAMP_COUNT) {
                        processBuffers();
                    }
                }

                handler.postDelayed(this, PROCESS_INTERVAL_01);
            }
        };
        handler.post(dataCollectionRunnable);
    }

    private void startGPSUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // LocationRequest.Builder 사용
            LocationRequest locationRequest = new LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, PROCESS_INTERVAL_01)
                    .setMinUpdateIntervalMillis(PROCESS_INTERVAL_01 / 2) // setFastestInterval 대체
                    .build();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null) {
                        Location location = locationResult.getLastLocation();
                        if (location != null) {
                            lastKnownLocation = location;
//                            Timber.tag(TAG).d("GPS 업데이트 수신: %f, %f", location.getLatitude(), location.getLongitude());
                        }
                    }
                }
            };

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void collectGPSData(long timestamp) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamp", timestamp);

            if (lastKnownLocation != null) {
                data.put("latitude", lastKnownLocation.getLatitude());
                data.put("longitude", lastKnownLocation.getLongitude());
                data.put("accuracy", lastKnownLocation.getAccuracy());
//                Timber.tag(TAG).d("GPS 데이터 추가: %f, %f", lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            } else {
                data.put("latitude", 0.0);
                data.put("longitude", 0.0);
                data.put("accuracy", 0.0f);
                Timber.tag(TAG).w("GPS 데이터 없음, 기본값 사용");
            }

            synchronized (gpsBuffer) {
                gpsBuffer.add(data);
            }
        }
    }

    private void collectAPData(long timestamp) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (wifiManager != null) {
                    List<ScanResult> scanResults = wifiManager.getScanResults();
                    if (!scanResults.isEmpty()) {
                        ScanResult scanResult = scanResults.get(0);
                        String ssid = "UNKNOWN_SSID";
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            ssid = wifiInfo.getSSID();
                        }
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("timestamp", timestamp);
                        data.put("bssid", scanResult.BSSID);
                        data.put("ssid", ssid);
                        data.put("level", (float) scanResult.level);
                        data.put("frequency", (float) scanResult.frequency);
                        data.put("capabilities", scanResult.capabilities);
                        synchronized (apBuffer) {
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
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (telephonyManager != null) {
                    List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                    for (CellInfo cellInfo : cellInfoList) {
                        if (cellInfo instanceof CellInfoLte) {
                            CellIdentityLte cellIdentity = ((CellInfoLte) cellInfo).getCellIdentity();
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("timestamp", timestamp);
                            data.put("ci", cellIdentity.getCi());
                            data.put("pci", cellIdentity.getPci());
                            synchronized (btsBuffer) {
                                btsBuffer.add(data);
                            }
                        }
                    }
                }
            } catch (SecurityException e) {
                Timber.tag(TAG).e(e, "BTS 데이터 수집 실패: 권한 부족");
            }
        }
    }

    private void collectIMUData(long timestamp) {
        if (sensorManager == null) {
            Timber.tag(TAG).e("SensorManager가 초기화되지 않음");
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
            Timber.tag(TAG).e("필요한 센서가 장치에 없음");
            return;
        }

        final List<Map<String, Object>> tempImuBuffer = new ArrayList<>(MAX_IMU_PER_SECOND);
        final long startTime = timestamp;

        class SensorDataHolder {
            final float[] accel = new float[3];
            final float[] gyro = new float[3];
            final float[] mag = new float[3];
            final float[] rot = new float[4];
            final float[] pressure = new float[1];
            final float[] gravity = new float[3];
            final float[] linear = new float[3];
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

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, linearAccelSensor, SensorManager.SENSOR_DELAY_FASTEST);

        Runnable imuCollector = new Runnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= MAX_IMU_PER_SECOND || System.currentTimeMillis() - startTime >= 1000) {
                    sensorManager.unregisterListener(listener);
                    if (!tempImuBuffer.isEmpty()) {
                        synchronized (imuBuffer) {
                            imuBuffer.addAll(tempImuBuffer);
                        }
                    }
                    return;
                }

                if (sensorData.isAllSet()) {
                    Map<String, Object> data = new LinkedHashMap<>();
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
                    tempImuBuffer.add(data);
                    count++;
                }
                handler.postDelayed(this, IMU_INTERVAL_MS);
            }
        };

        handler.post(imuCollector);
    }

    private void processBuffers() {
        synchronized (gpsBuffer) {
            synchronized (apBuffer) {
                synchronized (btsBuffer) {
                    synchronized (imuBuffer) {
                        synchronized (uniqueTimestamps) {
                            if (uniqueTimestamps.size() >= MIN_TIMESTAMP_COUNT) {
                                Timber.tag(TAG).d("60초 데이터 수집 완료 - GPS: %d, AP: %d, BTS: %d, IMU: %d",
                                        gpsBuffer.size(), apBuffer.size(), btsBuffer.size(), imuBuffer.size());

                                List<Map<String, Object>> gpsDataCopy = cloneData(gpsBuffer);
                                List<Map<String, Object>> apDataCopy = cloneData(apBuffer);
                                List<Map<String, Object>> btsDataCopy = cloneData(btsBuffer);
                                List<Map<String, Object>> imuDataCopy = cloneData(imuBuffer);

                                if (dataProcessor != null) {
                                    executorService.execute(() -> {
                                        dataProcessor.processSensorData(gpsDataCopy, apDataCopy, btsDataCopy, imuDataCopy);
                                        Timber.tag(TAG).d("SensorDataProcessor 처리 완료");
                                    });
                                } else {
                                    Timber.tag(TAG).w("SensorDataProcessor가 초기화 되지 않음");
                                }

                                gpsBuffer.clear();
                                apBuffer.clear();
                                btsBuffer.clear();
                                imuBuffer.clear();
                                uniqueTimestamps.clear();
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Map<String, Object>> cloneData(List<Map<String, Object>> originalList) {
        List<Map<String, Object>> clonedList = new ArrayList<>();
        for (Map<String, Object> originalMap : originalList) {
            Map<String, Object> clonedMap = new LinkedHashMap<>(originalMap);
            clonedList.add(clonedMap);
        }
        return clonedList;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        processBuffers();
        executorService.shutdown();
        stopForeground(true);
    }
}
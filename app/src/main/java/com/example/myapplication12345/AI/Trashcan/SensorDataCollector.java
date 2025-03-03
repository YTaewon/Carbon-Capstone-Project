//package com.example.movedistance;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.hardware.Sensor;
//import android.hardware.SensorEvent;
//import android.hardware.SensorEventListener;
//import android.hardware.SensorManager;
//import android.net.wifi.ScanResult;
//import android.net.wifi.WifiInfo;
//import android.net.wifi.WifiManager;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.telephony.CellIdentityLte;
//import android.telephony.CellInfo;
//import android.telephony.CellInfoLte;
//import android.telephony.TelephonyManager;
//import android.util.Log;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.example.movedistance.AP.APProcessor;
//import com.example.movedistance.BTS.BTSProcessor;
//import com.example.movedistance.GPS.GPSProcessor;
//import com.example.movedistance.IMU.IMUProcessor;
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationServices;
//import org.pytorch.Tensor;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//public class SensorDataCollector extends AppCompatActivity {
//    private static final int PROCESS_INTERVAL_60 = 60000; // 60초 간격
//    private static final int PROCESS_INTERVAL_01 = 1000;  // 1초 간격
//    private static final int PERMISSION_REQUEST_CODE = 1;
//
//    // 기본 센서 데이터는 최대 60개, IMU 데이터는 최대 60000개 저장하도록 설정
//    private static final int MAX_SIZE_DEFAULT = 60;
//    private static final int MAX_SIZE_IMU = 6000;
//
//    private final Handler handler = new Handler(Looper.getMainLooper());
//
//    // AP (WiFi)
//    private final List<Map<String, Object>> apDataList = new ArrayList<>();
//    private final List<Map<String, Object>> apProcessedDataList = new ArrayList<>();
//
//    // BTS
//    private final List<Map<String, Object>> btsDataList = new ArrayList<>();
//    private final List<Map<String, Object>> btsProcessedDataList = new ArrayList<>();
//
//    // GPS
//    private final List<Map<String, Object>> gpsDataList = new ArrayList<>();
//    private final List<Map<String, Object>> gpsProcessedDataList = new ArrayList<>();
//
//    // IMU
//    private final List<Map<String, Object>> imuDataList = new ArrayList<>();
//    private final List<Map<String, Object>> imuProcessedDataList = new ArrayList<>();
//
//    private WifiManager wifiManager;
//    private TelephonyManager telephonyManager;
//    private FusedLocationProviderClient fusedLocationProviderClient;
//    private SensorManager sensorManager;
//    private PyTorchHelper pyTorchHelper;
//    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
//    private boolean notstartAI = true;
//
//    //거리 개산 추가
//
//    TextView textAP, textBTS, textGPS, textIMU, textAIResult, textViewProcessed;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_ai);
//
//        textAP = findViewById(R.id.textAP);
//        textBTS = findViewById(R.id.textBTS);
//        textGPS = findViewById(R.id.textGPS);
//        textIMU = findViewById(R.id.textIMU);
//        textAIResult = findViewById(R.id.textViewAI);
//        textViewProcessed = findViewById(R.id.textViewProcessed);
//
//        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
//        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
//
//        requestPermissions();
//        processAI();
//    }
//
//    private void requestPermissions() {
//        if (!hasPermissions()) {
//            ActivityCompat.requestPermissions(this, new String[]{
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_WIFI_STATE,
//                    Manifest.permission.READ_PHONE_STATE
//            }, PERMISSION_REQUEST_CODE);
//        } else {
//            startDataCollection();
//        }
//    }
//
//    private boolean hasPermissions() {
//        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_REQUEST_CODE) {
//            boolean allGranted = true;
//            for (int result : grantResults) {
//                if (result != PackageManager.PERMISSION_GRANTED) {
//                    allGranted = false;
//                    break;
//                }
//            }
//            if (allGranted) {
//                startDataCollection();
//            } else {
//                Log.e("Permissions", "필수 권한이 거부됨. 앱 기능이 제한됩니다.");
//            }
//        }
//    }
//
//    private void startDataCollection() {
//        // 데이터 수집을 위한 Runnable 정의
//        Runnable dataCollectionRunnable = new Runnable() {
//            @Override
//            public void run() {
//                long timestamp = System.currentTimeMillis();
//                collectAPData(timestamp);
//                collectBTSData(timestamp);
//                collectGPSData(timestamp);
//                collectIMUData(timestamp);
//                handler.postDelayed(this, PROCESS_INTERVAL_01); // 지속적인 데이터 수집을 위해 다시 예약
//            }
//        };
//        // 데이터 처리를 위한 Runnable 정의
//        Runnable dataProcessingRunnable = new Runnable() {
//            @Override
//            public void run() {
//                processAPData();
//                processBTSData();
//                processGPSData();
//                processIMUData();
//
//                handler.postDelayed(this, PROCESS_INTERVAL_60); // 지속적인 데이터 처리를 위해 다시 예약
//            }
//        };
//        // Runnable 예약
//        handler.postDelayed(dataCollectionRunnable, PROCESS_INTERVAL_01);
//        handler.postDelayed(dataProcessingRunnable, PROCESS_INTERVAL_60);
//    }
//
//    // AP 데이터 수집
//    private void collectAPData(long timestamp) {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE)
//                == PackageManager.PERMISSION_GRANTED) {
//            try {
//                if (wifiManager != null) {
//                    List<ScanResult> scanResults = wifiManager.getScanResults();
//                    if (!scanResults.isEmpty()) {
//                        ScanResult scanResult = scanResults.get(0);
//                        String ssid = "UNKNOWN_SSID";
//                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                                == PackageManager.PERMISSION_GRANTED) {
//                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
//                            ssid = wifiInfo.getSSID();
//                        } else {
//                            Log.e("WiFi", "WiFi SSID 가져오기 실패: 위치 권한이 없음");
//                        }
//                        Map<String, Object> data = new HashMap<>();
//                        data.put("timestamp", timestamp);
//                        data.put("bssid", scanResult.BSSID);
//                        data.put("ssid", ssid);
//                        data.put("level", scanResult.level);
//                        data.put("frequency", scanResult.frequency);
//                        data.put("capabilities", scanResult.capabilities);
//                        addData(apDataList, data, "WiFi AP");
//                    }
//                }
//            } catch (SecurityException e) {
//                Log.e("WiFi", "Wi-Fi 데이터 수집 실패: 권한이 부족함", e);
//            }
//        }
//    }
//
//
//    // AP 데이터 처리
//    private void processAPData() {
//        if (!apDataList.isEmpty()) {
//            //복제 후 삭제
//            List<Map<String, Object>> clonedApDataList = cloneAndClearAPDataList(apDataList);
//
//            List<Map<String, Object>> processedData = APProcessor.processAP(clonedApDataList, findEarliestTimestamp(clonedApDataList));
//            StringBuilder dataText = new StringBuilder("Processed AP Data:\n");
//            for (Map<String, Object> entry : processedData) {
//                addData(apProcessedDataList, entry, "WiFi Processed");
//                dataText.append(entry.toString()).append("\n");
//            }
//            runOnUiThread(() -> textAP.setText(dataText.toString()));
//        }
//    }
//
//    // BTS 데이터 수집
//    private void collectBTSData(long timestamp) {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
//                == PackageManager.PERMISSION_GRANTED) {
//            try {
//                if (telephonyManager != null) {
//                    List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
//                    for (CellInfo cellInfo : cellInfoList) {
//                        if (cellInfo instanceof CellInfoLte) {
//                            CellIdentityLte cellIdentity = ((CellInfoLte) cellInfo).getCellIdentity();
//                            Map<String, Object> data = new HashMap<>();
//                            data.put("timestamp", timestamp);
//                            data.put("ci", cellIdentity.getCi());
//                            data.put("pci", cellIdentity.getPci());
//                            addData(btsDataList, data, "BTS Raw");
//                        }
//                    }
//                }
//            } catch (SecurityException e) {
//                Log.e("BTS", "BTS 데이터 수집 실패: 권한이 부족함", e);
//            }
//        }
//    }
//
//    // BTS 데이터 처리
//    private void processBTSData() {
//        if (!btsDataList.isEmpty()) {
//            //복제 후 삭제
//            List<Map<String, Object>> clonedApDataList = cloneAndClearAPDataList(btsDataList);
//
//            List<Map<String, Object>> processedData = BTSProcessor.processBTS(clonedApDataList, findEarliestTimestamp(clonedApDataList));
//            StringBuilder dataText = new StringBuilder("Processed BTS Data:\n");
//            for (Map<String, Object> entry : processedData) {
//                addData(btsProcessedDataList, entry, "BTS Processed");
//                dataText.append(entry.toString()).append("\n");
//            }
//            runOnUiThread(() -> textBTS.setText(dataText.toString()));
//        }
//    }
//
//    // GPS 데이터 수집
//    private void collectGPSData(long timestamp) {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                == PackageManager.PERMISSION_GRANTED) {
//            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
//                if (location != null) {
//                    Map<String, Object> data = new HashMap<>();
//                    data.put("timestamp", timestamp);
//                    data.put("latitude", location.getLatitude());
//                    data.put("longitude", location.getLongitude());
//                    addData(gpsDataList, data, "GPS Raw");
//                }
//            });
//        } else {
//            Log.e("GPS", "GPS permission is missing.");
//        }
//    }
//
//    // GPS 데이터 처리
//    private void processGPSData() {
//        if (!gpsDataList.isEmpty()) {
//            List<Map<String, Object>> clonedApDataList = cloneAndClearAPDataList(gpsDataList);
//
//            List<Map<String, Object>> processedData = GPSProcessor.processGPS(clonedApDataList, findEarliestTimestamp(clonedApDataList));
//            StringBuilder dataText = new StringBuilder("Processed GPS Data:\n");
//            for (Map<String, Object> entry : processedData) {
//                addData(gpsProcessedDataList, entry, "GPS Processed");
//                dataText.append(entry.toString()).append("\n");
//            }
//            runOnUiThread(() -> textGPS.setText(dataText.toString()));
//        }
//    }
//    // IMU 데이터 누적 수집
//    private void collectIMUData(long timestamp) {
//        if (sensorManager == null) {
//            Log.e("IMU", "SensorManager is not initialized.");
//            return;
//        }
//        //거리 계산용
//        Sensor accelrometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        Sensor Rotation = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//        Sensor Pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
//
//        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//        Sensor rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
//        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
//        Sensor gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
//        Sensor linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
//
//        if (accelerometer == null || gyroscope == null || magnetometer == null ||
//                rotationVector == null || pressureSensor == null ||
//                gravitySensor == null || linearAccelSensor == null) {
//            Log.e("IMU", "One or more sensors are not available on this device.");
//            return;
//        }
//
//        final Map<Integer, float[]> sensorValues = new ConcurrentHashMap<>();
//
//        SensorEventListener accumulationListener = new SensorEventListener() {
//            @Override
//            public void onSensorChanged(SensorEvent event) {
//                sensorValues.put(event.sensor.getType(), event.values.clone());
//            }
//            @Override
//            public void onAccuracyChanged(Sensor sensor, int accuracy) { }
//        };
//
//        sensorManager.registerListener(accumulationListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(accumulationListener, gyroscope, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(accumulationListener, magnetometer, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(accumulationListener, rotationVector, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(accumulationListener, pressureSensor, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(accumulationListener, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(accumulationListener, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME);
//
//        // 10ms 간격으로 센서 데이터를 기록
//        handler.postDelayed(new Runnable() {
//            @SuppressLint("SetTextI18n")
//            @Override
//            public void run() {
//                if (System.currentTimeMillis() - timestamp >= 1000) { // 1초(1000ms) 경과 후 종료
//                    sensorManager.unregisterListener(accumulationListener);
//                    return;
//                }
//
//                if (sensorValues.containsKey(Sensor.TYPE_ACCELEROMETER) &&
//                        sensorValues.containsKey(Sensor.TYPE_GYROSCOPE) &&
//                        sensorValues.containsKey(Sensor.TYPE_MAGNETIC_FIELD) &&
//                        sensorValues.containsKey(Sensor.TYPE_ROTATION_VECTOR) &&
//                        sensorValues.containsKey(Sensor.TYPE_PRESSURE) &&
//                        sensorValues.containsKey(Sensor.TYPE_GRAVITY) &&
//                        sensorValues.containsKey(Sensor.TYPE_LINEAR_ACCELERATION)) {
//
//                    Map<String, Object> data = new HashMap<>();
//                    data.put("timestamp", timestamp);
//
//                    float[] accel = sensorValues.get(Sensor.TYPE_ACCELEROMETER);
//                    assert accel != null;
//                    data.put("accel.x", accel[0]);
//                    data.put("accel.y", accel[1]);
//                    data.put("accel.z", accel[2]);
//
//                    float[] gyro = sensorValues.get(Sensor.TYPE_GYROSCOPE);
//                    assert gyro != null;
//                    data.put("gyro.x", gyro[0]);
//                    data.put("gyro.y", gyro[1]);
//                    data.put("gyro.z", gyro[2]);
//
//                    float[] mag = sensorValues.get(Sensor.TYPE_MAGNETIC_FIELD);
//                    assert mag != null;
//                    data.put("mag.x", mag[0]);
//                    data.put("mag.y", mag[1]);
//                    data.put("mag.z", mag[2]);
//
//                    float[] rot = sensorValues.get(Sensor.TYPE_ROTATION_VECTOR);
//                    float[] quat = new float[4];
//                    SensorManager.getQuaternionFromVector(quat, rot);
//                    data.put("rot.w", quat[0]);
//                    data.put("rot.x", quat[1]);
//                    data.put("rot.y", quat[2]);
//                    data.put("rot.z", quat[3]);
//
//                    float[] pressure = sensorValues.get(Sensor.TYPE_PRESSURE);
//                    assert pressure != null;
//                    data.put("pressure.x", pressure[0]);
//
//                    float[] gravity = sensorValues.get(Sensor.TYPE_GRAVITY);
//                    assert gravity != null;
//                    data.put("gravity.x", gravity[0]);
//                    data.put("gravity.y", gravity[1]);
//                    data.put("gravity.z", gravity[2]);
//
//                    float[] linear = sensorValues.get(Sensor.TYPE_LINEAR_ACCELERATION);
//                    assert linear != null;
//                    data.put("linear_accel.x", linear[0]);
//                    data.put("linear_accel.y", linear[1]);
//                    data.put("linear_accel.z", linear[2]);
//
//                    addData(imuDataList, data, "IMU Accumulated");
//                }
//                handler.postDelayed(this, 10);  // 10ms 간격으로 실행 (총 100회 반복)
//            }
//        }, 5);
//    }
//
//    // IMU 데이터 처리 (누적 데이터 기반)
//    private void processIMUData() {
//        if(!imuDataList.isEmpty()){
//            List<Map<String, Object>> processedData = IMUProcessor.preImu(cloneAndClearAPDataList(imuDataList));
//
//            StringBuilder dataText = new StringBuilder("Processed IMU Data:\n");
//            for (Map<String, Object> entry : processedData) {
//                addData(imuProcessedDataList, entry, "IMU Processed");
//                dataText.append(entry.toString()).append("\n");
//            }
//            runOnUiThread(() -> textIMU.setText(dataText.toString()));
//        }
//        processAI();
//    }
//
//    //리스트 복제
//    public static List<Map<String, Object>> cloneAndClearAPDataList(List<Map<String, Object>> originalList) {
//        // 1. 원본 리스트 복제
//        List<Map<String, Object>> clonedList = new ArrayList<>();
//        for (Map<String, Object> originalMap : originalList) {
//            Map<String, Object> clonedMap = new HashMap<>(originalMap); // 각 Map을 개별적으로 복사
//            clonedList.add(clonedMap);
//        }
//
//        // 2. 원본 리스트 초기화
//        originalList.clear();
//
//        return clonedList;
//    }
//
//    private static long findEarliestTimestamp(List<Map<String, Object>> dataList) {
//        return dataList.stream()
//                .filter(map -> map.containsKey("timestamp"))
//                .map(map -> (long) map.get("timestamp"))
//                .min(Long::compare)
//                .orElse(System.currentTimeMillis());
//    }
//
//    // AI 처리 - 여기서 AP, BTS, GPS, IMU 순서로 병합된 피처 벡터를 AI 모델의 입력으로 사용합니다.
//    @SuppressLint("SetTextI18n")
//    private void processAI() {
//        executorService.execute(() -> {
//            if (pyTorchHelper == null) {
//                pyTorchHelper = new PyTorchHelper(this);
//                runOnUiThread(() -> textAIResult.setText("PyTorchHelper 초기화 완료"));
//            }
//
//            if (notstartAI) {
//                notstartAI = false;
//            } else {
//                //float[] outputData = pyTorchHelper.predict(getProcessedFeatureVector());
//
//                //Log.d("PyTorch Output", "Result: " + Arrays.toString(outputData));
//                //runOnUiThread(() -> textAIResult.setText(String.valueOf(outputData[0])));
//            }
//        });
//    }
//
//    private static Map<String, Object> getWithFallback(List<Map<String, Object>> list, int index) {
//        if (list.isEmpty()) {
//            return new HashMap<>();  // 빈 리스트일 경우 빈 Map 반환
//        }
//        return list.get(Math.min(index, list.size() - 1));  // 초과하면 마지막 요소 반환
//    }
//
//    private Tensor getProcessedFeatureVector() {
//        printsize("AP", apProcessedDataList);
//        printsize("BTS", btsProcessedDataList);
//        printsize("GPS", gpsProcessedDataList);
//        printsize("IMU", imuProcessedDataList);
//
//        if (apProcessedDataList.isEmpty()) {
//            Log.e("AI", "❌ AP 데이터가 없습니다.");
//            return null;
//        }
//        if (btsProcessedDataList.isEmpty()) {
//            Log.e("AI", "❌ BTS 데이터가 없습니다.");
//            return null;
//        }
//        if (gpsProcessedDataList.isEmpty()) {
//            Log.e("AI", "❌ GPS 데이터가 없습니다.");
//            return null;
//        }
//        if (imuProcessedDataList.isEmpty()) {
//            Log.e("AI", "❌ IMU 데이터가 없습니다.");
//            return null;
//        }
//
//        Log.d("AI", "📌 AP 데이터 리스트 크기: " + apProcessedDataList.size());
//        List<Map<String, Object>> sortedAPDataList = sortAndRemoveTimestamp(apProcessedDataList);
//        Log.d("AI", "📌 정렬된 AP 데이터 리스트 크기: " + sortedAPDataList.size());
//
//
//        Log.d("AI", "📌 BTS 데이터 리스트 크기: " + btsProcessedDataList.size());
//        List<Map<String, Object>> sortedBTSDataList = sortAndRemoveTimestamp(btsProcessedDataList);
//        Log.d("AI", "📌 정렬된 BTS 데이터 리스트 크기: " + sortedAPDataList.size());
//
//
//        Log.d("AI", "📌 GPS 데이터 리스트 크기: " + gpsProcessedDataList.size());
//        List<Map<String, Object>> sortedGPSDataList = sortAndRemoveTimestamp(gpsProcessedDataList);
//        Log.d("AI", "📌 정렬된 GPS 데이터 리스트 크기: " + sortedAPDataList.size());
//
//
//        Log.d("AI", "📌 IMU 데이터 리스트 크기: " + imuProcessedDataList.size());
//        List<Map<String, Object>> sortedIIMUDataList = sortAndRemoveTimestamp(imuProcessedDataList);
//        Log.d("AI", "📌 정렬된 IMU 데이터 리스트 크기: " + sortedAPDataList.size());
//
//        List<Map<String, Object>> max = new ArrayList<>();
//
//        for (int i = 0; i < 60; i++) {
//            Map<String, Object> row = new LinkedHashMap<>();
//
//            // ap에서 첫 번째 데이터 사용 (60번)
//            row.putAll(sortedAPDataList.get(0));
//
//            // bts에서 12개씩 5번 반복
//            row.putAll(getWithFallback(sortedBTSDataList,i % 12));
//
//            // gps에서 12개씩 5번 반복
//            row.putAll(getWithFallback(sortedGPSDataList, i % 12));
//
//            // imu에서 60줄 그대로 사용
//            row.putAll(getWithFallback(sortedIIMUDataList, i));
//
//            max.add(row);
//        }
//
//        Log.d("AI", "📌 MAX 데이터 리스트 크기: " + max.size());
//        printsize("MAX",max);
//        //max 크기 확인용
////        for (int i = 0; i < max.size(); i++) {
////            System.out.println("Row " + (i + 1) + " length: " + max.get(i).size());
////        }
//        //max 한줄 출력
//        if (!max.isEmpty()) {
//            System.out.println(max.get(0)); // 첫 번째 Map을 출력
//        } else {
//            System.out.println("List is empty");
//        }
//
//
//        apProcessedDataList.clear();
//        btsProcessedDataList.clear();
//        gpsProcessedDataList.clear();
//        imuProcessedDataList.clear();
//
//        Tensor tensor = convertListMapToTensor(max);
//        float[] data = tensor.getDataAsFloatArray();
//        System.out.println(Arrays.toString(data));
//
//        return tensor;
//    }
//
//    public static Tensor convertListMapToTensor(List<Map<String, Object>> dataList) {
//        int numRows = 340;  // 각 Map에 340개의 데이터 포인트가 있다고 가정
//        int numCols = 60;   // 60개의 행을 가정
//
//        // 340 x 60의 데이터를 저장할 배열
//        float[] dataArray = new float[numRows * numCols];
//
//        int index = 0;
//        for (int col = 0; col < numCols; col++) {
//            Map<String, Object> map = dataList.get(col);
//            int dataCount = 0;  // 각 Map에서 수집한 데이터 수
//            for (String key : map.keySet()) {
//                Object value = map.get(key);
//                if (value instanceof Number) {
//                    // Number를 float로 변환하여 저장
//                    dataArray[index++] = ((Number) value).floatValue();
//                    dataCount++;
//                }
//            }
//            if (dataCount != numRows) {
//                throw new IllegalArgumentException("Each map must contain exactly " + numRows + " data points.");
//            }
//        }
//
//        // 데이터의 길이가 예상되는 크기와 맞는지 확인
//        if (index != numRows * numCols) {
//            throw new IllegalArgumentException("Data array size does not match expected tensor shape size.");
//        }
//
//        // Tensor 생성 (1 x 340 x 60 형태)
//        return Tensor.fromBlob(dataArray, new long[]{1, numRows, numCols});
//    }
//
//    private void printsize( String tag, List<Map<String, Object>> data){
//        if (!data.isEmpty()) {
//            int numberOfColumns = data.get(0).size();
//            Log.d(tag,"열의 개수:"+numberOfColumns);
//        } else {
//            Log.d(tag,"리스트가 비어 있습니다.");
//        }
//    }
//
//    private List<Map<String, Object>> sortAndRemoveTimestamp(List<Map<String, Object>> dataList) {
//        Collections.sort(dataList, new Comparator<Map<String, Object>>() {
//            @Override
//            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
//                Long timestamp1 = (Long) o1.get("timestamp");
//                Long timestamp2 = (Long) o2.get("timestamp");
//
//                if (timestamp1 == null || timestamp2 == null) {
//                    Log.e("Sorting", "One or both timestamps are null");
//                    return 0; // Consider equal if either timestamp is null
//                }
//
//                return timestamp1.compareTo(timestamp2);
//            }
//        });
//
//        // Remove the timestamp from each map
//        for (Map<String, Object> data : dataList) {
//            data.remove("timestamp");
//        }
//
//        return dataList;
//    }
//
//    // 맵에서 피처 벡터 추출
//    private <T> void addData(List<T> dataList, T data, String tag) {
//        int maxSize = MAX_SIZE_DEFAULT;
//        if (tag.contains("IMU")) {
//            maxSize = MAX_SIZE_IMU;
//        }
//        if (dataList.size() >= maxSize) {
//            dataList.remove(0);
////            Log.d(tag, "데이터 제거");
//        }
//        dataList.add(data);
//    }
//}

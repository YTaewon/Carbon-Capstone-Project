package com.example.myapplication12345.AI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myapplication12345.AI.AP.APProcessor;
import com.example.myapplication12345.AI.BTS.BTSProcessor;
import com.example.myapplication12345.AI.GPS.GPSProcessor;
import com.example.myapplication12345.AI.IMU.IMUProcessor;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import timber.log.Timber;

public class SensorDataProcessor {
    private static final String TAG = "SensorDataProcessor";
    private static final String MODEL_FILENAME = "model.pt";
    private static final String[] TRANSPORT_MODES = {
            "WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC", "OTHER1", "OTHER2", "OTHER3", "OTHER4", "OTHER5"
    };
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    static final long ONE_MINUTE_MS = 60 * 1000;
    private static final int MIN_DATA_SIZE = 60;

    private final Context context;
    private Module model;
    private String predictedResult;
    private final List<Map<String, Object>> apDataList = new ArrayList<>();
    private final List<Map<String, Object>> apProcessedDataList = new ArrayList<>();
    private final List<Map<String, Object>> btsDataList = new ArrayList<>();
    private final List<Map<String, Object>> btsProcessedDataList = new ArrayList<>();
    private final List<Map<String, Object>> gpsDataList = new ArrayList<>();
    private final List<Map<String, Object>> gpsProcessedDataList = new ArrayList<>();
    private final List<Map<String, Object>> imuDataList = new ArrayList<>();
    private final List<Map<String, Object>> imuProcessedDataList = new ArrayList<>();

    public SensorDataProcessor(Context context) {
        this.context = context;
        this.predictedResult = "알 수 없음";
        try {
            String modelPath = assetFilePath(context, MODEL_FILENAME);
            model = Module.load(modelPath);
            Timber.tag(TAG).d("PyTorch 모델 로드 완료: " + modelPath);
        } catch (IOException e) {
            Log.e(TAG, "모델 파일 복사 오류: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "모델 로드 중 오류: " + e.getMessage(), e);
        }
    }

    private String assetFilePath(Context context, String filename) throws IOException {
        File file = new File(context.getFilesDir(), filename);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }
        try (InputStream is = context.getAssets().open(filename);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        }
        return file.getAbsolutePath();
    }

    private List<Map<String, Object>> loadOneMinuteCSVData(String sensorType) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        String currentDate = dateFormat.format(calendar.getTime());
        int minSize = (sensorType.equals("IMU") ? MIN_DATA_SIZE * 100 : MIN_DATA_SIZE);

        dataList = loadCSVDataForDate(sensorType, currentDate);
        if (dataList.size() >= minSize) {
            return filterOneMinuteData(dataList);
        } else {
            Log.w(TAG, sensorType + " 데이터 부족: " + dataList.size() + ", 최소: " + minSize);
        }

        calendar.add(Calendar.DAY_OF_YEAR, 1);
        String nextDate = dateFormat.format(calendar.getTime());
        File nextFile = new File(context.getExternalFilesDir(null), "SensorData/" + nextDate + "_" + sensorType + ".csv");
        if (nextFile.exists()) {
            dataList = loadCSVDataForDate(sensorType, nextDate);
            Log.d(TAG, sensorType + " 다음 날 데이터 로드: " + nextDate);
            if (dataList.size() >= minSize) {
                return filterOneMinuteData(dataList);
            } else {
                Log.w(TAG, sensorType + " 다음 날 데이터도 부족: " + dataList.size());
            }
        } else {
            Log.e(TAG, sensorType + " 다음 날 파일 없음: " + nextDate);
        }

        return dataList;
    }

    private List<Map<String, Object>> loadCSVDataForDate(String sensorType, String date) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        String fileName = date + "_" + sensorType + ".csv";
        File file = new File(context.getExternalFilesDir(null), "SensorData/" + fileName);

        if (!file.exists()) {
            Log.e(TAG, "CSV 파일이 존재하지 않음: " + fileName);
            return dataList;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                Log.e(TAG, "CSV 헤더가 없음: " + fileName);
                return dataList;
            }
            String[] headers = headerLine.split(",");

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != headers.length) {
                    Log.w(TAG, "CSV 데이터 불일치: " + line);
                    continue;
                }
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String value = values[i];
                    if (headers[i].equals("timestamp")) {
                        try {
                            if (value.contains(".")) {
                                data.put(headers[i], (long) Float.parseFloat(value));
                            } else {
                                data.put(headers[i], Long.parseLong(value));
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "타임스탬프 파싱 실패: " + value + ", 데이터 제외");
                            data = null;
                            break;
                        }
                    } else if (headers[i].equals("bssid") || headers[i].equals("ssid") || headers[i].equals("capabilities")) {
                        data.put(headers[i], value);
                    } else if (value.isEmpty()) {
                        data.put(headers[i], 0.0f);
                    } else {
                        try {
                            data.put(headers[i], Float.parseFloat(value));
                        } catch (NumberFormatException e) {
                            data.put(headers[i], value);
                        }
                    }
                }
                if (data != null) dataList.add(data);
            }
            Log.d(TAG, sensorType + " 데이터 로드 완료 (" + date + "), 크기: " + dataList.size());
        } catch (IOException e) {
            Log.e(TAG, "CSV 로드 실패: " + sensorType + " (" + date + ")", e);
        }
        return dataList;
    }

    private List<Map<String, Object>> filterOneMinuteData(List<Map<String, Object>> dataList) {
        if (dataList.isEmpty()) return dataList;
        Long earliestTimestamp = findEarliestTimestamp(dataList);
        return dataList.stream()
                .filter(data -> (Long) data.get("timestamp") <= earliestTimestamp + ONE_MINUTE_MS)
                .collect(Collectors.toList());
    }

    private void removeProcessedDataFromCSV(String sensorType, List<Map<String, Object>> usedData) {
        String date = dateFormat.format(System.currentTimeMillis());
        String fileName = date + "_" + sensorType + ".csv";
        File file = new File(context.getExternalFilesDir(null), "SensorData/" + fileName);

        if (!file.exists()) {
            Log.d(TAG, "제거할 CSV 파일 없음: " + fileName);
            return;
        }

        List<Map<String, Object>> remainingData = new ArrayList<>();
        String headerLine;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            headerLine = br.readLine();
            if (headerLine == null) {
                Log.e(TAG, "CSV 헤더 없음: " + fileName);
                return;
            }
            String[] headers = headerLine.split(",");

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != headers.length) continue;
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String value = values[i];
                    if (headers[i].equals("timestamp")) {
                        try {
                            if (value.contains(".")) {
                                data.put(headers[i], (long) Float.parseFloat(value));
                            } else {
                                data.put(headers[i], Long.parseLong(value));
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "타임스탬프 파싱 실패: " + value + ", 데이터 제외");
                            data = null;
                            break;
                        }
                    } else if (headers[i].equals("bssid") || headers[i].equals("ssid") || headers[i].equals("capabilities")) {
                        data.put(headers[i], value);
                    } else if (value.isEmpty()) {
                        data.put(headers[i], 0.0f);
                    } else {
                        try {
                            data.put(headers[i], Float.parseFloat(value));
                        } catch (NumberFormatException e) {
                            data.put(headers[i], value);
                        }
                    }
                }
                if (data != null) {
                    Map<String, Object> finalData = data;
                    boolean isUsed = usedData.stream().anyMatch(used -> {
                        Object usedTs = used.get("timestamp");
                        Object dataTs = finalData.get("timestamp");
                        return usedTs != null && dataTs != null && usedTs.equals(dataTs);
                    });
                    if (!isUsed) remainingData.add(data);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "CSV 읽기 실패: " + sensorType, e);
            return;
        }

        try (FileWriter writer = new FileWriter(file, false)) {
            if (headerLine != null) {
                writer.append(headerLine).append("\n");
                if (remainingData.isEmpty()) {
                    Log.d(TAG, sensorType + " CSV 업데이트: 남은 데이터 없음, 헤더만 유지");
                } else {
                    for (Map<String, Object> data : remainingData) {
                        StringBuilder line = new StringBuilder();
                        for (Object value : data.values()) {
                            if (line.length() > 0) line.append(",");
                            line.append(value.toString());
                        }
                        writer.append(line.toString()).append("\n");
                    }
                    Log.d(TAG, sensorType + " CSV 업데이트 완료, 남은 데이터 크기: " + remainingData.size());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "CSV 쓰기 실패: " + sensorType, e);
        }
    }

    public boolean processAPData() {
        apDataList.clear();
        apDataList.addAll(loadOneMinuteCSVData("AP"));
        if (!apDataList.isEmpty()) {
            List<Map<String, Object>> clonedApDataList = cloneAndClearAPDataList(apDataList);
            List<Map<String, Object>> processedData = APProcessor.processAP(clonedApDataList, findEarliestTimestamp(clonedApDataList));
            if (!processedData.isEmpty()) {
                apProcessedDataList.clear();
                apProcessedDataList.addAll(processedData);
                Log.d(TAG, "Processed AP Data: " + processedData.toString());
                removeProcessedDataFromCSV("AP", clonedApDataList); // 전처리 성공 시에만 삭제
                return true;
            } else {
                Log.w(TAG, "AP 데이터 전처리 실패: 결과가 비어 있음");
            }
        }
        return false;
    }

    public boolean processBTSData() {
        btsDataList.clear();
        btsDataList.addAll(loadOneMinuteCSVData("BTS"));
        if (!btsDataList.isEmpty()) {
            List<Map<String, Object>> clonedBtsDataList = cloneAndClearAPDataList(btsDataList);
            List<Map<String, Object>> processedData = BTSProcessor.processBTS(clonedBtsDataList, findEarliestTimestamp(clonedBtsDataList));
            if (!processedData.isEmpty()) {
                btsProcessedDataList.clear();
                btsProcessedDataList.addAll(processedData);
                Log.d(TAG, "Processed BTS Data: " + processedData.toString());
                removeProcessedDataFromCSV("BTS", clonedBtsDataList); // 전처리 성공 시에만 삭제
                return true;
            } else {
                Log.w(TAG, "BTS 데이터 전처리 실패: 결과가 비어 있음");
            }
        }
        return false;
    }

    public boolean processGPSData() {
        gpsDataList.clear();
        gpsDataList.addAll(loadOneMinuteCSVData("GPS"));
        if (!gpsDataList.isEmpty()) {
            List<Map<String, Object>> clonedGpsDataList = cloneAndClearAPDataList(gpsDataList);
            List<Map<String, Object>> processedData = GPSProcessor.processGPS(clonedGpsDataList, findEarliestTimestamp(clonedGpsDataList));
            if (!processedData.isEmpty()) {
                gpsProcessedDataList.clear();
                gpsProcessedDataList.addAll(processedData);
                Log.d(TAG, "Processed GPS Data: " + processedData.toString());
                removeProcessedDataFromCSV("GPS", clonedGpsDataList); // 전처리 성공 시에만 삭제
                return true;
            } else {
                Log.w(TAG, "GPS 데이터 전처리 실패: 결과가 비어 있음");
            }
        }
        return false;
    }

    public boolean processIMUData() {
        imuDataList.clear();
        imuDataList.addAll(loadOneMinuteCSVData("IMU"));
        if (!imuDataList.isEmpty()) {
            List<Map<String, Object>> clonedImuDataList = cloneAndClearAPDataList(imuDataList);
            List<Map<String, Object>> processedData = IMUProcessor.preImu(clonedImuDataList);
            if (!processedData.isEmpty()) {
                imuProcessedDataList.clear();
                imuProcessedDataList.addAll(processedData);
                Log.d(TAG, "Processed IMU Data: " + processedData.toString());
                removeProcessedDataFromCSV("IMU", clonedImuDataList); // 전처리 성공 시에만 삭제
                return true;
            } else {
                Log.w(TAG, "IMU 데이터 전처리 실패: 결과가 비어 있음");
            }
        }
        return false;
    }

    public static List<Map<String, Object>> cloneAndClearAPDataList(List<Map<String, Object>> originalList) {
        List<Map<String, Object>> clonedList = new ArrayList<>();
        for (Map<String, Object> originalMap : originalList) {
            Map<String, Object> clonedMap = new HashMap<>(originalMap);
            clonedList.add(clonedMap);
        }
        originalList.clear();
        return clonedList;
    }

    private static long findEarliestTimestamp(List<Map<String, Object>> dataList) {
        return dataList.stream()
                .filter(map -> map.containsKey("timestamp"))
                .map(map -> (Long) map.get("timestamp"))
                .min(Long::compare)
                .orElse(System.currentTimeMillis());
    }

    public Tensor getProcessedFeatureVector() {
        boolean apSuccess = processAPData();
        boolean btsSuccess = processBTSData();
        boolean gpsSuccess = processGPSData();
        boolean imuSuccess = processIMUData();

        if (!apSuccess || !btsSuccess || !gpsSuccess || !imuSuccess) {
            Timber.tag(TAG).e("❌ 하나 이상의 데이터 전처리가 실패했습니다.");
            return null;
        }

        List<Map<String, Object>> sortedAPDataList = sortAndRemoveTimestamp(apProcessedDataList);
        List<Map<String, Object>> sortedBTSDataList = sortAndRemoveTimestamp(btsProcessedDataList);
        List<Map<String, Object>> sortedGPSDataList = sortAndRemoveTimestamp(gpsProcessedDataList);
        List<Map<String, Object>> sortedIMUDataList = sortAndRemoveTimestamp(imuProcessedDataList);

        List<Map<String, Object>> max = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.putAll(getWithFallback(sortedAPDataList, 0));
            row.putAll(getWithFallback(sortedBTSDataList, i % 12));
            row.putAll(getWithFallback(sortedGPSDataList, i % 12));
            row.putAll(getWithFallback(sortedIMUDataList, i));
            max.add(row);
        }

        Timber.tag(TAG).d("📌 MAX 데이터 리스트 크기: %s", max.size());
        return convertListMapToTensor(max);
    }

    private static Map<String, Object> getWithFallback(List<Map<String, Object>> list, int index) {
        if (list.isEmpty()) return new HashMap<>();
        return list.get(Math.min(index, list.size() - 1));
    }

    public static Tensor convertListMapToTensor(List<Map<String, Object>> dataList) {
        int numRows = 340;
        int numCols = 60;
        float[] dataArray = new float[numRows * numCols];
        int index = 0;

        for (int col = 0; col < Math.min(numCols, dataList.size()); col++) {
            Map<String, Object> map = dataList.get(col);
            for (Object value : map.values()) {
                if (value instanceof Number && index < dataArray.length) {
                    dataArray[index++] = ((Number) value).floatValue();
                }
            }
        }

        while (index < numRows * numCols) {
            dataArray[index++] = 0.0f;
        }

        return Tensor.fromBlob(dataArray, new long[]{1, numRows, numCols});
    }

    private List<Map<String, Object>> sortAndRemoveTimestamp(List<Map<String, Object>> dataList) {
        Collections.sort(dataList, Comparator.comparingLong(m -> (Long) m.get("timestamp")));
        for (Map<String, Object> data : dataList) {
            data.remove("timestamp");
        }
        return dataList;
    }

    private double calculateDistance(List<Map<String, Object>> gpsData) {
        double totalDistance = 0.0;
        for (int i = 0; i < gpsData.size() - 1; i++) {
            double lat1 = ((Number) gpsData.get(i).get("latitude")).doubleValue();
            double lon1 = ((Number) gpsData.get(i).get("longitude")).doubleValue();
            double lat2 = ((Number) gpsData.get(i + 1).get("latitude")).doubleValue();
            double lon2 = ((Number) gpsData.get(i + 1).get("longitude")).doubleValue();

            totalDistance += haversineDistance(lat1, lon1, lat2, lon2);
        }
        return totalDistance;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // 지구 반지름 (미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @SuppressLint("DefaultLocale")
    private void savePredictionToCSV(String transportMode, double distance, long startTimestamp,
                                     double startLat, double startLon, double endLat, double endLon) {
        String date = dateFormat.format(startTimestamp);
        String fileName = date + "_predictions.csv";
        File file = new File(context.getExternalFilesDir(null), "SensorData/" + fileName);

        try (FileWriter writer = new FileWriter(file, true)) {
            if (!file.exists() || file.length() == 0) {
                writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n");
            }
            writer.append(String.format("%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n",
                    startTimestamp, transportMode, distance, startLat, startLon, endLat, endLon));
            Timber.tag(TAG).d("예측 결과 CSV 저장 (" + fileName + "): " + transportMode + ", 거리: " + distance + " 미터, " +
                    "시작: (" + startLat + ", " + startLon + "), 끝: (" + endLat + ", " + endLon + ")");
        } catch (IOException e) {
            Timber.tag(TAG).e(e, "예측 결과 CSV 저장 실패: %s", e.getMessage());
        }
    }

    public void predictMovingMode(Tensor inputTensor) {
        try {
            long[] inputShape = inputTensor.shape();
            Timber.tag(TAG).d("✅ 입력 텐서 크기: %s", Arrays.toString(inputShape));
            Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
            Timber.tag(TAG).d("✅ 출력 텐서 크기: %s", Arrays.toString(outputTensor.shape()));

            float[] logits = outputTensor.getDataAsFloatArray();
            float[] probabilities = softmax(logits);
            Timber.tag(TAG).d("전체 확률 값: %s", Arrays.toString(probabilities));

            int batchSize = (int) inputShape[0];
            if (probabilities.length != 11 * batchSize) {
                Log.e(TAG, "출력 크기 불일치: " + probabilities.length);
                predictedResult = "알 수 없음 (출력 오류)";
                return;
            }
            for (float prob : probabilities) {
                if (Float.isNaN(prob)) {
                    Log.e(TAG, "NaN 값 감지");
                    predictedResult = "알 수 없음 (NaN 오류)";
                    return;
                }
            }

            int maxIndex = 0;
            float maxProb = probabilities[0];
            for (int i = 1; i < 11; i++) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i];
                    maxIndex = i;
                }
            }

            float threshold = 0.9f;
            if (maxProb >= threshold) {
                predictedResult = TRANSPORT_MODES[maxIndex];
                Log.d(TAG, "예측된 이동수단: " + predictedResult + ", 확률: " + maxProb);

                if (!gpsDataList.isEmpty() && gpsDataList.size() >= 2) { // 전처리 전 데이터 사용
                    double distance = calculateDistance(gpsProcessedDataList);
                    long startTimestamp = findEarliestTimestamp(gpsProcessedDataList);

                    // 전처리 전 GPS 데이터에서 맨 앞과 뒤 값 가져오기
                    Map<String, Object> startData = gpsDataList.get(0);
                    Map<String, Object> endData = gpsDataList.get(gpsDataList.size() - 1);
                    double startLat = ((Number) startData.get("latitude")).doubleValue();
                    double startLon = ((Number) startData.get("longitude")).doubleValue();
                    double endLat = ((Number) endData.get("latitude")).doubleValue();
                    double endLon = ((Number) endData.get("longitude")).doubleValue();

                    savePredictionToCSV(predictedResult, distance, startTimestamp, startLat, startLon, endLat, endLon);
                } else {
                    Log.w(TAG, "GPS 데이터 부족으로 위치 정보 저장 불가");
                }
            } else {
                predictedResult = "알 수 없음 (확률 낮음)";
                Log.w(TAG, "확률 낮음: " + maxProb);
            }
        } catch (Exception e) {
            Log.e(TAG, "예측 중 오류: " + e.getMessage(), e);
            predictedResult = "알 수 없음 (예측 실패)";
        }
    }

    private float[] softmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) {
                maxLogit = logit;
            }
        }
        float sum = 0.0f;
        float[] expLogits = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            expLogits[i] = (float) Math.exp(logits[i] - maxLogit);
            sum += expLogits[i];
        }
        float[] probabilities = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = expLogits[i] / sum;
        }
        return probabilities;
    }

    public String getPredictedResult() {
        return predictedResult;
    }

    public void setPredictedResult(String result) {
        this.predictedResult = result;
        Log.d(TAG, "예측 결과 설정: " + result);
    }

    public static void scheduleBackgroundPrediction(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(SensorPredictionWorker.class, 1, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
        Log.d(TAG, "1분 주기 백그라운드 작업 예약 완료");
    }

    public static class SensorPredictionWorker extends Worker {
        public SensorPredictionWorker(Context context, WorkerParameters params) {
            super(context, params);
        }

        @Override
        public Result doWork() {
            try {
                SensorDataProcessor processor = new SensorDataProcessor(getApplicationContext());
                Tensor inputTensor = processor.getProcessedFeatureVector();
                if (inputTensor != null) {
                    processor.predictMovingMode(inputTensor);
                    Log.d(TAG, "백그라운드 작업 완료, 결과: " + processor.getPredictedResult());
                    return Result.success();
                } else {
                    Log.e(TAG, "Tensor 생성 실패");
                    return Result.retry();
                }
            } catch (Exception e) {
                Log.e(TAG, "백그라운드 작업 중 오류: " + e.getMessage(), e);
                return Result.retry();
            }
        }
    }

    public static void runPrediction(Context context) {
        scheduleBackgroundPrediction(context);
    }
}
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SensorDataProcessor {
    private static final String TAG = "SensorDataProcessor";
    private static final String MODEL_FILENAME = "model.pt";
    // 수정된 TRANSPORT_MODES: 0~4만 유효, 나머지는 None으로 처리
    private static final String[] TRANSPORT_MODES = {
            "WALK", "BIKE", "BUS", "CAR", "SUBWAY"  // 0~4
    };
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    static final long ONE_MINUTE_MS = 60 * 1000;
    private static final int MIN_TIMESTAMP_COUNT = 60;

    private final Context context;
    private Module model;
    private String predictedResult;
    private final List<Map<String, Object>> apDataList = new ArrayList<>();
    private final List<Map<String, Object>> apProcessedDataList = new ArrayList<>();
    private final List<Map<String, Object>> btsDataList = new ArrayList<>();
    private final List<Map<String, Object>> btsProcessedDataList = new ArrayList<>();
    private final List<Map<String, Object>> gpsDataList = new ArrayList<>();
    private final List<Map<String, Object>> gpsProcessedDataList = new ArrayList<>();
    private List<Map<String, Object>> clonedGpsDataList = new ArrayList<>();
    private final List<Map<String, Object>> imuDataList = new ArrayList<>();
    private final List<Map<String, Object>> imuProcessedDataList = new ArrayList<>();
    private List<Map<String, Object>> clonedImuDataList = new ArrayList<>();

    public SensorDataProcessor(Context context) {
        this.context = context;
        this.predictedResult = "None";
        try {
            String modelPath = assetFilePath(context, MODEL_FILENAME);
            model = Module.load(modelPath);
            if (model == null) {
                throw new IllegalStateException("Module.load() returned null");
            }
            Log.d(TAG, "PyTorch 모델 로드 완료: " + modelPath);
        } catch (IOException e) {
            Log.e(TAG, "모델 파일 복사 오류: " + e.getMessage(), e);
            throw new IllegalStateException("Failed to load model due to IO error", e);
        } catch (Exception e) {
            Log.e(TAG, "모델 로드 중 오류: " + e.getMessage(), e);
            throw new IllegalStateException("Failed to load model", e);
        }
    }

    private String assetFilePath(Context context, String filename) throws IOException {
        File file = new File(context.getFilesDir(), filename);
        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "기존 모델 파일 사용: " + file.getAbsolutePath());
            return file.getAbsolutePath();
        }
        Log.d(TAG, "Assets에서 모델 파일 복사 시작: " + filename);
        try (InputStream is = context.getAssets().open(filename);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            Log.d(TAG, "모델 파일 복사 완료: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Assets에서 파일 복사 실패: " + filename, e);
            throw e;
        }
        return file.getAbsolutePath();
    }

    private List<Map<String, Object>> loadOneMinuteCSVData(String sensorType) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        String currentDate = dateFormat.format(calendar.getTime());
        int minTimestampCount = MIN_TIMESTAMP_COUNT;

        dataList = loadCSVDataForDate(sensorType, currentDate);
        Set<Long> uniqueTimestamps = new HashSet<>();
        for (Map<String, Object> data : dataList) {
            uniqueTimestamps.add((Long) data.get("timestamp"));
        }
        int uniqueTimestampCount = uniqueTimestamps.size();

        if (uniqueTimestampCount == minTimestampCount) {
            Log.d(TAG, sensorType + " 고유 타임스탬프 개수 정확히 일치: " + uniqueTimestampCount);
            return dataList; // 정확히 일치하면 필터링 불필요
        } else if (uniqueTimestampCount < minTimestampCount) {
            Log.w(TAG, sensorType + " 고유 타임스탬프 개수 부족: " + uniqueTimestampCount);
        } else {
            Log.d(TAG, sensorType + " 고유 타임스탬프 개수 초과: " + uniqueTimestampCount + ", 가장 빠른 60개 선택");
            List<Map<String, Object>> filteredDataList = new ArrayList<>();
            Set<Long> selectedTimestamps = new HashSet<>();
            Collections.sort(dataList, Comparator.comparingLong(m -> (Long) m.get("timestamp")));
            for (Map<String, Object> data : dataList) {
                Long timestamp = (Long) data.get("timestamp");
                if (selectedTimestamps.size() < minTimestampCount) {
                    if (selectedTimestamps.add(timestamp)) {
                        filteredDataList.add(data);
                    }
                } else {
                    break;
                }
            }
            dataList = filteredDataList;
            Log.d(TAG, sensorType + " 60개 타임스탬프 데이터로 제한: " + filteredDataList.size());
            return dataList; // 자른 리스트를 바로 반환
        }
        // 다음 날 로직은 변경 없음
        return dataList;
    }

    private List<Map<String, Object>> filterOneMinuteData(List<Map<String, Object>> dataList) {
        if (dataList.size() <= MIN_TIMESTAMP_COUNT) {
            Log.d(TAG, "필터링 스킵: 데이터 크기 (" + dataList.size() + ")가 MIN_TIMESTAMP_COUNT 이하");
            return dataList; // 크기가 60 이하이면 필터링 스킵
        }
        Long earliestTimestamp = findEarliestTimestamp(dataList);
        List<Map<String, Object>> filteredList = dataList.stream()
                .filter(data -> (Long) data.get("timestamp") <= earliestTimestamp + ONE_MINUTE_MS)
                .collect(Collectors.toList());
        Log.d(TAG, "필터링 후 데이터 크기: " + filteredList.size());
        return filteredList;
    }

    private List<Map<String, Object>> loadCSVDataForDate(String sensorType, String date) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        String fileName = date + "_" + sensorType + ".csv";
        File file = new File(context.getExternalFilesDir(null), "SensorData/" + fileName);

        if (!file.exists()) {
            Log.e(TAG, "CSV 파일이 존재하지 않음: " + fileName);
            return dataList;
        }

        boolean parsingFailed = false; // 파싱 실패 여부를 추적하는 플래그

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                Log.e(TAG, "CSV 헤더가 없음: " + fileName);
                parsingFailed = true; // 헤더가 없으면 파싱 실패로 간주
                return dataList;
            }
            String[] headers = headerLine.split(",");

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != headers.length) {
                    Log.w(TAG, "CSV 데이터 불일치: " + line);
                    parsingFailed = true; // 데이터와 헤더 길이가 맞지 않으면 실패
                    continue;
                }
                Map<String, Object> data = new HashMap<>(headers.length);

                for (int i = 0; i < headers.length; i++) {
                    String header = headers[i];
                    String value = values[i].trim();

                    try {
                        if (header.equals("timestamp")) {
                            data.put(header, Long.parseLong(value));
                        } else if (header.equals("bssid") || header.equals("ssid") || header.equals("capabilities")) {
                            data.put(header, value);
                        } else if (value.isEmpty()) {
                            data.put(header, 0.0f);
                        } else {
                            data.put(header, Float.parseFloat(value));
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "파싱 실패: " + header + "=" + value + ", 기본값 사용");
                        if (header.equals("timestamp")) {
                            data = null;
                            parsingFailed = true; // 타임스탬프 파싱 실패는 치명적
                            break;
                        } else {
                            data.put(header, 0.0f);
                        }
                    }
                }

                if (data != null) {
                    dataList.add(data);
                }
            }
            Log.d(TAG, sensorType + " 데이터 로드 완료 (" + date + "), 크기: " + dataList.size());
        } catch (IOException e) {
            Log.e(TAG, "CSV 로드 실패: " + sensorType + " (" + date + ")", e);
            parsingFailed = true; // 입출력 오류도 실패로 간주
        }

        // 파싱이 실패했으면 파일을 삭제
        if (parsingFailed) {
            if (file.delete()) {
                Log.d(TAG, "파싱 실패로 인해 파일 삭제됨: " + fileName);
            } else {
                Log.e(TAG, "파싱 실패 후 파일 삭제 실패: " + fileName);
            }
            dataList.clear(); // 부분적으로 파싱된 데이터가 사용되지 않도록 리스트 초기화
        }

        return dataList;
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
                            data.put(headers[i], Long.parseLong(value));
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
                            data.put(headers[i], 0.0f);
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
        Log.d(TAG, "AP 데이터 로드 후 크기: " + apDataList.size());  // Log loaded data size

        if (apDataList.isEmpty()) {
            Log.w(TAG, "AP 데이터 리스트가 비어 있음 - CSV 파일 확인 필요");
            return false;
        }

        Set<Long> uniqueTimestamps = new HashSet<>();
        for (Map<String, Object> data : apDataList) {
            Object timestamp = data.get("timestamp");
            if (timestamp instanceof Long) {
                uniqueTimestamps.add((Long) timestamp);
            } else {
                Log.w(TAG, "잘못된 타임스탬프 형식 발견: " + timestamp);
            }
        }
        Log.d(TAG, "AP 고유 타임스탬프 개수: " + uniqueTimestamps.size());

        if (uniqueTimestamps.size() < MIN_TIMESTAMP_COUNT) {
            Log.w(TAG, "AP 고유 타임스탬프 부족: " + uniqueTimestamps.size() + " < " + MIN_TIMESTAMP_COUNT);
            return false;
        }

        List<Map<String, Object>> clonedApDataList = cloneAndClearAPDataList(apDataList);
        List<Map<String, Object>> processedData = APProcessor.processAP(clonedApDataList, findEarliestTimestamp(clonedApDataList));
        Log.d(TAG, "AP 처리 후 데이터 크기: " + processedData.size());

        if (processedData.isEmpty()) {
            Log.w(TAG, "AP 데이터 전처리 실패: APProcessor.processAP()에서 빈 결과 반환");
            return false;
        }

        apProcessedDataList.clear();
        apProcessedDataList.addAll(processedData);
        Log.d(TAG, "Processed AP Data: " + processedData.toString());
        removeProcessedDataFromCSV("AP", clonedApDataList);
        return true;
    }

    public boolean processBTSData() {
        btsDataList.clear();
        btsDataList.addAll(loadOneMinuteCSVData("BTS"));
        if (!btsDataList.isEmpty()) {
            Set<Long> uniqueTimestamps = new HashSet<>();
            for (Map<String, Object> data : btsDataList) {
                uniqueTimestamps.add((Long) data.get("timestamp"));
            }
            if (uniqueTimestamps.size() >= MIN_TIMESTAMP_COUNT) {
                List<Map<String, Object>> clonedBtsDataList = cloneAndClearAPDataList(btsDataList);
                List<Map<String, Object>> processedData = BTSProcessor.processBTS(clonedBtsDataList, findEarliestTimestamp(clonedBtsDataList));
                if (!processedData.isEmpty()) {
                    btsProcessedDataList.clear();
                    btsProcessedDataList.addAll(processedData);
                    Log.d(TAG, "Processed BTS Data: " + processedData.toString());
                    removeProcessedDataFromCSV("BTS", clonedBtsDataList); // 처리 성공 시 데이터 제거
                    return true;
                } else {
                    Log.w(TAG, "BTS 데이터 전처리 실패: 결과가 비어 있음");
                }
            }
        }
        return false;
    }

    public boolean processGPSData() {
        gpsDataList.clear();
        gpsDataList.addAll(loadOneMinuteCSVData("GPS"));
        if (!gpsDataList.isEmpty()) {
            Set<Long> uniqueTimestamps = new HashSet<>();
            for (Map<String, Object> data : gpsDataList) {
                uniqueTimestamps.add((Long) data.get("timestamp"));
            }
            if (uniqueTimestamps.size() >= MIN_TIMESTAMP_COUNT) {
                clonedGpsDataList = cloneAndClearAPDataList(gpsDataList);
                List<Map<String, Object>> processedData = GPSProcessor.processGPS(clonedGpsDataList, findEarliestTimestamp(clonedGpsDataList));
                if (!processedData.isEmpty()) {
                    gpsProcessedDataList.clear();
                    gpsProcessedDataList.addAll(processedData);
                    Log.d(TAG, "Processed GPS Data: " + processedData.toString());
                    removeProcessedDataFromCSV("GPS", clonedGpsDataList); // 처리 성공 시 데이터 제거
                    return true;
                } else {
                    Log.w(TAG, "GPS 데이터 전처리 실패: 결과가 비어 있음");
                }
            }
        }
        return false;
    }

    public boolean processIMUData() {
        imuDataList.clear();
        imuDataList.addAll(loadOneMinuteCSVData("IMU"));
        if (!imuDataList.isEmpty()) {
            Set<Long> uniqueTimestamps = new HashSet<>();
            for (Map<String, Object> data : imuDataList) {
                uniqueTimestamps.add((Long) data.get("timestamp"));
            }
            if (uniqueTimestamps.size() >= MIN_TIMESTAMP_COUNT) {
                clonedImuDataList = cloneAndClearAPDataList(imuDataList);
                List<Map<String, Object>> processedData = IMUProcessor.preImu(clonedImuDataList);
                if (!processedData.isEmpty()) {
                    imuProcessedDataList.clear();
                    imuProcessedDataList.addAll(processedData);
                    Log.d(TAG, "Processed IMU Data: " + processedData.toString());
                    removeProcessedDataFromCSV("IMU", clonedImuDataList); // 처리 성공 시 데이터 제거
                    return true;
                } else {
                    Log.w(TAG, "IMU 데이터 전처리 실패: 결과가 비어 있음");
                }
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

        if (!apSuccess) {
            Log.e(TAG, "❌ AP 데이터 전처리가 실패했습니다.");
            return null;
        }
        if (!btsSuccess) {
            Log.e(TAG, "❌ BTS 데이터 전처리가 실패했습니다.");
            return null;
        }
        if (!gpsSuccess) {
            Log.e(TAG, "❌ GPS 데이터 전처리가 실패했습니다.");
            return null;
        }
        if (!imuSuccess) {
            Log.e(TAG, "❌ IMU 데이터 전처리가 실패했습니다.");
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

        Log.d(TAG, "📌 MAX 데이터 리스트 크기: " + max.size());
        Tensor tensor = convertListMapToTensor(max);
        if (tensor == null) {
            Log.e(TAG, "Tensor 변환 실패 - 데이터 확인 필요");
        } else {
            Log.d(TAG, "Tensor 생성 성공 - 크기: " + Arrays.toString(tensor.shape()));
        }

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
            Log.d(TAG, "예측 결과 CSV 저장 (" + fileName + "): " + transportMode + ", 거리: " + distance + " 미터, " +
                    "시작: (" + startLat + ", " + startLon + "), 끝: (" + endLat + ", " + endLon + ")");
        } catch (IOException e) {
            Log.e(TAG, "예측 결과 CSV 저장 실패: " + e.getMessage(), e);
        }
    }

    @SuppressLint("DefaultLocale")
    public void predictMovingMode(Tensor inputTensor) {
        if (model == null || inputTensor == null) {
            predictedResult = "None";
            Log.e(TAG, "모델 또는 입력 텐서가 null - 예측 불가능");
            return;
        }

        try {
            Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
            float[] logits = outputTensor.getDataAsFloatArray();
            float[] probabilities = softmax(logits);

            int maxIndex = 0;
            float maxProb = probabilities[0];
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i];
                    maxIndex = i;
                }
            }

            float threshold = 0.9f;
            if (maxProb >= threshold && maxIndex <= 4) {
                predictedResult = TRANSPORT_MODES[maxIndex];
                Log.d(TAG, "예측된 이동수단: " + predictedResult + ", 확률: " + maxProb);
            } else {
                predictedResult = "None";
                if (maxProb < threshold) {
                    Log.w(TAG, "확률이 임계값 미만: " + maxProb);
                } else {
                    Log.w(TAG, "클래스 인덱스 " + maxIndex + "는 유효하지 않음 (5 이상), None으로 설정");
                }
            }

            // GPS 데이터가 충분한 경우에만 위치 정보 처리
            if (!clonedGpsDataList.isEmpty() && clonedGpsDataList.size() >= 2) {
                long startTimestamp = findEarliestTimestamp(clonedGpsDataList);
                Map<String, Object> startData = clonedGpsDataList.get(0);
                Map<String, Object> endData = clonedGpsDataList.get(clonedGpsDataList.size() - 1);
                double startLat = ((Number) startData.get("latitude")).doubleValue();
                double startLon = ((Number) startData.get("longitude")).doubleValue();
                double endLat = ((Number) endData.get("latitude")).doubleValue();
                double endLon = ((Number) endData.get("longitude")).doubleValue();

                double distance;
                String transportModeForSave;

                // 모델 예측이 None이 아닌 경우
                if (!predictedResult.equals("None")) {
                    transportModeForSave = predictedResult;
                    MovementAnalyzer analyzer = new MovementAnalyzer(clonedGpsDataList, clonedImuDataList);
                    analyzer.analyze();
                    distance = analyzer.getDistance();
                    Log.d(TAG, "모델 예측 사용: " + transportModeForSave + ", 거리: " + distance + "m");
                } else {
                    // 모델 예측이 None인 경우에만 MovementAnalyzer의 transportMode 사용
                    MovementAnalyzer analyzer = new MovementAnalyzer(clonedGpsDataList, clonedImuDataList);
                    analyzer.analyze();
                    distance = analyzer.getDistance();
                    String analyzedTransportMode = analyzer.getTransportMode();
                    if (Arrays.asList(TRANSPORT_MODES).contains(analyzedTransportMode)) {
                        transportModeForSave = analyzedTransportMode;
                        Log.d(TAG, "모델 예측이 None - MovementAnalyzer 결과 사용: " + transportModeForSave);
                    } else {
                        transportModeForSave = "None";
                        Log.d(TAG, "MovementAnalyzer 결과도 유효하지 않음: " + analyzedTransportMode);
                    }
                }

                savePredictionToCSV(transportModeForSave, distance, startTimestamp, startLat, startLon, endLat, endLon);
            } else {
                Log.w(TAG, "GPS 데이터 부족으로 위치 정보 저장 불가");
            }
        } catch (Exception e) {
            Log.e(TAG, "예측 중 오류: " + e.getMessage(), e);
            predictedResult = "None";
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
            } catch (IllegalStateException e) {
                Log.e(TAG, "모델 초기화 실패로 백그라운드 작업 중단: " + e.getMessage(), e);
                return Result.failure();
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
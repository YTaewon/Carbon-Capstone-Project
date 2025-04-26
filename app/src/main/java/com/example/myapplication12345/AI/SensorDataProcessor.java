package com.example.myapplication12345.AI;

import android.annotation.SuppressLint;
import android.content.Context;

import com.example.myapplication12345.AI.AP.APProcessor;
import com.example.myapplication12345.AI.BTS.BTSProcessor;
import com.example.myapplication12345.AI.GPS.GPSProcessor;
import com.example.myapplication12345.AI.IMU.IMUProcessor;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class SensorDataProcessor {
    private static final String TAG = "SensorDataProcessor";
    private static final String MODEL_FILENAME = "model_optimized88.ptl";
    //334 or 340
    private static final int MODEL_SIZE = 334;
    // TRANSPORT_MODES 확장: 11개 요소로 정의
    private static final String[] TRANSPORT_MODES = {
            "WALK", "WALK", "BIKE", "CAR", "BUS",
            "ETC", "SUBWAY", "ETC", "BIKE", "ETC", "CAR"
    };
    private static final int MIN_TIMESTAMP_COUNT = 60;
    private static final int SEGMENT_SIZE = 10;
    private static final SimpleDateFormat dateFormat;

    static {
        dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    }

    private static SensorDataProcessor instance;
    private final Context context;
    private Module model;
    private String predictedResult = "ETC";
    private volatile boolean isModelLoaded = false;

    // 기존 생성자와 모델 로드 로직 유지
    public static synchronized SensorDataProcessor getInstance(Context context) {
        if (instance == null) {
            instance = new SensorDataProcessor(context);
        }
        return instance;
    }

    private SensorDataProcessor(Context context) {
        this.context = context;
        loadModelAsync();
    }

    private void loadModelAsync() {
        new Thread(() -> {
            try {
                String modelPath = assetFilePath(context);
                model = Module.load(modelPath);
                isModelLoaded = true;
                Timber.tag(TAG).d("PyTorch 모델 로드 완료: %s", modelPath);
            } catch (IOException e) {
                Timber.tag(TAG).e(e, "모델 파일 복사 오류: %s", e.getMessage());
                throw new IllegalStateException("Failed to load model due to IO error", e);
            } catch (Exception e) {
                Timber.tag(TAG).e(e, "모델 로드 중 오류: %s", e.getMessage());
                throw new IllegalStateException("Failed to load model", e);
            }
        }).start();
    }

    private String assetFilePath(Context context) throws IOException {
        File file = new File(context.getFilesDir(), SensorDataProcessor.MODEL_FILENAME);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }
        try (InputStream is = context.getAssets().open(SensorDataProcessor.MODEL_FILENAME);
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

    public void processSensorData(List<Map<String, Object>> gpsData,
                                  List<Map<String, Object>> apData,
                                  List<Map<String, Object>> btsData,
                                  List<Map<String, Object>> imuData) {

        MovementAnalyzer analyzer = new MovementAnalyzer(gpsData, imuData);
        analyzer.analyze();
        float distance = analyzer.getDistance();

        if (!isModelLoaded) {
            Timber.tag(TAG).w("모델이 아직 로드되지 않음. 데이터 처리 스킵");
            predictedResult = "ETC"; // 기본값 변경
            return;
        }

        Timber.tag(TAG).d("수신된 데이터 크기 - GPS: " + gpsData.size() + ", AP: " + apData.size() +
                ", BTS: " + btsData.size() + ", IMU: " + imuData.size());

        if (gpsData.size() < MIN_TIMESTAMP_COUNT || apData.isEmpty() || btsData.isEmpty() || imuData.size() < MIN_TIMESTAMP_COUNT) {
            Timber.tag(TAG).w("필요한 최소 데이터 요구사항 충족되지 않음");
            predictedResult = "ETC"; // 기본값 변경
            return;
        }

        List<Map<String, Object>> processedAP = APProcessor.processAP(apData, findEarliestTimestamp(apData));
        List<Map<String, Object>> processedBTS = BTSProcessor.processBTS(btsData, findEarliestTimestamp(btsData));
        List<Map<String, Object>> processedGPS = GPSProcessor.processGPS(gpsData, findEarliestTimestamp(gpsData));
        List<Map<String, Object>> processedIMU = IMUProcessor.preImu(imuData);

        if (processedAP.isEmpty() || processedBTS.isEmpty() || processedGPS.isEmpty() || processedIMU.isEmpty()) {
            Timber.tag(TAG).w("데이터 전처리 실패 - 하나 이상의 센서 데이터가 비어 있음");
            predictedResult = "ETC"; // 기본값 변경
            return;
        }

        Tensor inputTensor = getProcessedFeatureVector(processedAP, processedBTS, processedGPS, processedIMU);
        predictMovingMode(inputTensor, gpsData, distance);
    }

    private Tensor getProcessedFeatureVector(List<Map<String, Object>> apData,
                                             List<Map<String, Object>> btsData,
                                             List<Map<String, Object>> gpsData,
                                             List<Map<String, Object>> imuData) {
        List<Map<String, Object>> sortedAP = sortAndRemoveTimestamp(apData);
        List<Map<String, Object>> sortedBTS = sortAndRemoveTimestamp(btsData);
        List<Map<String, Object>> sortedGPS = sortAndRemoveTimestamp(gpsData);
        List<Map<String, Object>> sortedIMU = sortAndRemoveTimestamp(imuData);

        List<Map<String, Object>> combinedData = new ArrayList<>();

        final int repetitionsPerGpsRecord = 5;

        for (int i = 0; i < MIN_TIMESTAMP_COUNT; i++) {
            Map<String, Object> row = new LinkedHashMap<>();

            int gpsIndex = i / repetitionsPerGpsRecord;
            row.putAll(getWithFallback(sortedGPS, gpsIndex));

//            row.putAll(getWithFallback(sortedAP, 0));
//            row.putAll(getWithFallback(sortedBTS, i % Math.min(12, sortedBTS.size())));
//            row.putAll(getWithFallback(sortedGPS, i % Math.min(12, sortedGPS.size())));
            row.putAll(getWithFallback(sortedIMU, i));
            combinedData.add(row);
        }
        Timber.tag(TAG).d(sortedIMU.toString());
        return convertListMapToTensor(combinedData);
    }

    private static Map<String, Object> getWithFallback(List<Map<String, Object>> list, int index) {
        if (list.isEmpty()) return new HashMap<>();
        return list.get(Math.min(index, list.size() - 1));
    }

    private static Tensor convertListMapToTensor(List<Map<String, Object>> dataList) {
        int numRows = MODEL_SIZE;
        int numCols = MIN_TIMESTAMP_COUNT;
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

        File directory = new File(context.getExternalFilesDir(null), "SensorData");
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                Timber.tag(TAG).d("SensorData 디렉토리 생성 성공: %s", directory.getAbsolutePath());
            } else {
                Timber.tag(TAG).e("SensorData 디렉토리 생성 실패");
                return;
            }
        }

        File file = new File(directory, fileName);

        try (FileWriter writer = new FileWriter(file, true)) {
            if (!file.exists() || file.length() == 0) {
                writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n");
            }
            writer.append(String.format("%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n",
                    startTimestamp, transportMode, distance, startLat, startLon, endLat, endLon));
            Timber.tag(TAG).d("예측 결과 CSV 저장 성공 (" + fileName + "): " + transportMode + ", 거리: " + distance + " 미터, " +
                    "시작: (" + startLat + ", " + startLon + "), 끝: (" + endLat + ", " + endLon + ")");
        } catch (IOException e) {
            Timber.tag(TAG).e(e, "예측 결과 CSV 저장 실패: %s", e.getMessage());
        }
    }

    private void predictMovingMode(Tensor inputTensor, List<Map<String, Object>> gpsData, float distance) {
        if (model == null || inputTensor == null) {
            predictedResult = "ETC"; // 기본값 변경
            Timber.tag(TAG).e("모델 또는 입력 텐서가 null");
            return;
        }

        try {
            Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
            float[] logits = outputTensor.getDataAsFloatArray();
            float[] probabilities = softmax(logits);

            // 출력 크기 확인
            if (logits.length != 11) {
                Timber.tag(TAG).e("모델 출력 크기가 예상과 다름: " + logits.length + " (예상: 11)");
                predictedResult = "ETC";
                return;
            }

            int maxIndex = 0;
            float maxProb = probabilities[0];
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i];
                    maxIndex = i;
                }
            }

            float threshold = 0.9f;
            if (maxProb >= threshold && maxIndex < TRANSPORT_MODES.length) {
                predictedResult = TRANSPORT_MODES[maxIndex];
                Timber.tag(TAG).d("예측된 이동수단: " + predictedResult + ", 확률: " + maxProb);
            } else {
                predictedResult = "ETC"; // 기본적으로 ETC 설정
                Timber.tag(TAG).w("확률이 임계값 미만 또는 유효하지 않은 인덱스: " + maxProb + ", " + maxIndex);
            }

            if (!gpsData.isEmpty() && gpsData.size() >= MIN_TIMESTAMP_COUNT) {
                int totalSegments = gpsData.size() / SEGMENT_SIZE;
                for (int segment = 0; segment < totalSegments; segment++) {
                    int startIndex = segment * SEGMENT_SIZE;
                    int endIndex = Math.min(startIndex + SEGMENT_SIZE, gpsData.size() - 1);

                    Map<String, Object> startData = gpsData.get(startIndex);
                    Map<String, Object> endData = gpsData.get(endIndex);

                    long startTimestamp = ((Number) Objects.requireNonNull(startData.get("timestamp"))).longValue();
                    double startLat = ((Number) Objects.requireNonNull(startData.get("latitude"))).doubleValue();
                    double startLon = ((Number) Objects.requireNonNull(startData.get("longitude"))).doubleValue();
                    double endLat = ((Number) Objects.requireNonNull(endData.get("latitude"))).doubleValue();
                    double endLon = ((Number) Objects.requireNonNull(endData.get("longitude"))).doubleValue();

                    String transportMode = predictedResult;
                    if (distance <= 0.05) {
                        transportMode = "ETC"; // 거리 임계값에 따른 기본값 변경
                    }
                    Timber.tag(TAG).d("구간 " + segment + " 최종 이동수단: " + transportMode + ", 거리: " + distance + "m, " +
                            "시작: (" + startLat + ", " + startLon + "), 끝: (" + endLat + ", " + endLon + ")");
                    savePredictionToCSV(transportMode, distance/6, startTimestamp, startLat, startLon, endLat, endLon);
                }
            } else {
                Timber.tag(TAG).w("GPS 데이터 부족으로 CSV 저장 불가");
            }
        } catch (Exception e) {
            Timber.tag(TAG).e(e, "예측 중 오류: %s", e.getMessage());
            predictedResult = "ETC"; // 오류 시 기본값 변경
        }
    }

    private float[] softmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) maxLogit = logit;
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

    private static long findEarliestTimestamp(List<Map<String, Object>> dataList) {
        return dataList.stream()
                .filter(map -> map.containsKey("timestamp"))
                .map(map -> (Long) map.get("timestamp"))
                .min(Long::compare)
                .orElse(System.currentTimeMillis());
    }
}
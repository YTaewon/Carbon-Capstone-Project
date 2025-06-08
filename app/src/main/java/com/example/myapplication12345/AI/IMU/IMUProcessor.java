package com.example.myapplication12345.AI.IMU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap; // [수정] NavigableMap 임포트
import java.util.TreeMap;

public class IMUProcessor {

    // SensorDataService의 스펙과 일치하는 상수 정의
    private static final int TARGET_WINDOW_SIZE = 100; // 1초에 100개 샘플 목표

    public static List<Map<String, Object>> preImu(List<Map<String, Object>> imu) {
        if (imu == null || imu.isEmpty()) {
            throw new IllegalArgumentException("⚠ IMU 데이터가 비어 있습니다!");
        }

        // 1. 타임스탬프별로 데이터 그룹화
        Map<Long, List<Map<String, Object>>> groupedByTimestamp = groupByTimestamp(imu);

        // 2. [핵심 보강] 각 윈도우를 균일한 100Hz 데이터로 리샘플링 및 패딩
        Map<String, double[][][]> dfs = createUniformWindows(groupedByTimestamp);

        // 3. 피처 추출
        List<String> enabledSensors = Arrays.asList("gyro", "accel", "linear_accel", "accel_h", "accel_v", "jerk_h",
                "jerk_v", "mag", "gravity", "pressure");

        Map<String, Object> calcDfs = new HashMap<>();
        calcDfs.put("timestamp", createTimestampArray(groupedByTimestamp));

        for (String sensor : enabledSensors) {
            String dataSourceKey = IMUConfig.getUsingSensorData(sensor);
            if (!dfs.containsKey(dataSourceKey)) {
                continue;
            }

            Map<String, double[][]> processed = IMUProcessing.processingImu(
                    dfs.get(dataSourceKey),
                    IMUConfig.getSensorChannels(sensor),
                    IMUConfig.getProcessType(sensor),
                    IMUConfig.isProcessEachAxis(sensor),
                    IMUConfig.isCalculateJerkEnabled(sensor),
                    dfs.get("rot"),
                    dfs.get("gravity"),
                    sensor
            );
            calcDfs.putAll(replaceNaNWithZero(processed));
        }

        // 4. 최종 결과 포맷팅
        return formatToFinalList(calcDfs);
    }

    private static Map<String, double[][][]> createUniformWindows(Map<Long, List<Map<String, Object>>> groupedData) {
        List<String> sensors = Arrays.asList("gyro", "accel", "mag", "rot", "pressure", "gravity", "linear_accel");
        Map<String, Integer> channels = getSensorChannelCounts();
        Map<String, double[][][]> finalDfs = new HashMap<>();

        for (String sensor : sensors) {
            finalDfs.put(sensor, new double[groupedData.size()][TARGET_WINDOW_SIZE][channels.get(sensor)]);
        }

        int windowIndex = 0;
        for (List<Map<String, Object>> windowData : new TreeMap<>(groupedData).values()) {
            if (windowData.isEmpty()) {
                windowIndex++;
                continue;
            }

            // [수정] 변수 타입을 Map에서 NavigableMap으로 변경
            NavigableMap<Integer, Map<String, Object>> seqMap = new TreeMap<>();
            for (Map<String, Object> sample : windowData) {
                seqMap.put((Integer) sample.getOrDefault("seq", 0), sample);
            }

            for (String sensor : sensors) {
                int numChannels = channels.get(sensor);
                String[] axes = getAxesForSensor(sensor);

                for (int seq = 0; seq < TARGET_WINDOW_SIZE; seq++) {
                    if (seqMap.containsKey(seq)) {
                        Map<String, Object> sample = seqMap.get(seq);
                        for (int ch = 0; ch < numChannels; ch++) {
                            finalDfs.get(sensor)[windowIndex][seq][ch] = getNumberAsDouble(sample.get(sensor + "." + axes[ch]));
                        }
                    } else {
                        // [수정] 이제 floorEntry, ceilingEntry 등이 정상적으로 동작합니다.
                        Map.Entry<Integer, Map<String, Object>> before = seqMap.floorEntry(seq);
                        Map.Entry<Integer, Map<String, Object>> after = seqMap.ceilingEntry(seq);

                        if (before == null) before = seqMap.firstEntry();
                        if (after == null) after = seqMap.lastEntry();

                        // before가 여전히 null이면 seqMap이 비어있는 예외적인 경우이므로 건너뜀
                        if (before == null) continue;

                        for (int ch = 0; ch < numChannels; ch++) {
                            double valBefore = getNumberAsDouble(before.getValue().get(sensor + "." + axes[ch]));
                            double valAfter = getNumberAsDouble(after.getValue().get(sensor + "." + axes[ch]));
                            long seqBefore = before.getKey();
                            long seqAfter = after.getKey();

                            if (seqAfter == seqBefore) {
                                finalDfs.get(sensor)[windowIndex][seq][ch] = valBefore;
                            } else {
                                double ratio = (double) (seq - seqBefore) / (seqAfter - seqBefore);
                                double interpolatedValue = valBefore + (valAfter - valBefore) * ratio;
                                finalDfs.get(sensor)[windowIndex][seq][ch] = interpolatedValue;
                            }
                        }
                    }
                }
            }
            windowIndex++;
        }
        return finalDfs;
    }

    // --- 나머지 유틸리티 및 헬퍼 메서드는 이전과 동일 (생략) ---
    private static Map<Long, List<Map<String, Object>>> groupByTimestamp(List<Map<String, Object>> imuData) {
        Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
        for (Map<String, Object> entry : imuData) {
            long timestamp = getNumberAsLong(entry.get("timestamp"));
            grouped.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    public static Map<String, double[][]> replaceNaNWithZero(Map<String, double[][]> featureMap) {
        if (featureMap == null) return new HashMap<>();
        Map<String, double[][]> newMap = new HashMap<>();
        for (Map.Entry<String, double[][]> entry : featureMap.entrySet()) {
            String key = entry.getKey();
            double[][] values = entry.getValue();
            if (values == null) continue;
            double[][] newValues = new double[values.length][];
            for (int i = 0; i < values.length; i++) {
                if(values[i] == null) continue;
                newValues[i] = new double[values[i].length];
                for (int j = 0; j < values[i].length; j++) {
                    newValues[i][j] = Double.isNaN(values[i][j]) ? 0.0 : values[i][j];
                }
            }
            newMap.put(key, newValues);
        }
        return newMap;
    }

    private static List<Map<String, Object>> formatToFinalList(Map<String, Object> dataMap) {
        if (dataMap.isEmpty() || !dataMap.containsKey("timestamp")) return Collections.emptyList();
        int numRows = ((long[][]) dataMap.get("timestamp")).length;
        List<Map<String, Object>> resultList = new ArrayList<>(numRows);

        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String header : predefinedHeaders) {
                Object data = dataMap.get(header);
                if (data instanceof double[][] && rowIndex < ((double[][]) data).length && ((double[][]) data)[rowIndex].length > 0) {
                    entry.put(header, ((double[][]) data)[rowIndex][0]);
                } else if (data instanceof long[][] && rowIndex < ((long[][]) data).length && ((long[][]) data)[rowIndex].length > 0) {
                    entry.put(header, ((long[][]) data)[rowIndex][0]);
                }
            }
            resultList.add(entry);
        }
        return resultList;
    }

    private static long[][] createTimestampArray(Map<Long, List<Map<String, Object>>> groupedData) {
        long[][] timestampArray = new long[groupedData.size()][1];
        int index = 0;
        for (Long time : new TreeMap<>(groupedData).keySet()) {
            timestampArray[index++][0] = time;
        }
        return timestampArray;
    }

    private static double getNumberAsDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }

    private static long getNumberAsLong(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }

    private static Map<String, Integer> getSensorChannelCounts() {
        Map<String, Integer> channels = new HashMap<>();
        channels.put("gyro", 3);
        channels.put("accel", 3);
        channels.put("mag", 3);
        channels.put("gravity", 3);
        channels.put("linear_accel", 3);
        channels.put("rot", 4);
        channels.put("pressure", 1);
        return channels;
    }

    private static String[] getAxesForSensor(String sensor) {
        if ("rot".equals(sensor)) {
            return new String[]{"x", "y", "z", "w"};
        }
        return new String[]{"x", "y", "z"};
    }

    // predefinedHeaders 리스트는 기존과 동일
    private static final List<String> predefinedHeaders = Arrays.asList(
            "timestamp",
            "accelM_mean", "accelM_std", "accelM_max", "accelM_min", "accelM_mad", "accelM_iqr",
            "accelM_max.corr", "accelM_idx.max.corr", "accelM_zcr", "accelM_fzc",
            "accelX_mean", "accelX_std", "accelX_max", "accelX_min", "accelX_mad", "accelX_iqr",
            "accelX_max.corr", "accelX_idx.max.corr", "accelX_zcr", "accelX_fzc",
            "accelY_mean", "accelY_std", "accelY_max", "accelY_min", "accelY_mad", "accelY_iqr",
            "accelY_max.corr", "accelY_idx.max.corr", "accelY_zcr", "accelY_fzc",
            "accelZ_mean", "accelZ_std", "accelZ_max", "accelZ_min", "accelZ_mad", "accelZ_iqr",
            "accelZ_max.corr", "accelZ_idx.max.corr", "accelZ_zcr", "accelZ_fzc",
            "accelM_max.psd", "accelM_entropy", "accelM_fc", "accelM_kurt", "accelM_skew",
            "accelX_max.psd", "accelX_entropy", "accelX_fc", "accelX_kurt", "accelX_skew",
            "accelY_max.psd", "accelY_entropy", "accelY_fc", "accelY_kurt", "accelY_skew",
            "accelZ_max.psd", "accelZ_entropy", "accelZ_fc", "accelZ_kurt", "accelZ_skew",
            "accel_hM_mean", "accel_hM_std", "accel_hM_max", "accel_hM_min", "accel_hM_mad", "accel_hM_iqr",
            "accel_hM_max.corr", "accel_hM_idx.max.corr", "accel_hM_zcr", "accel_hM_fzc",
            "accel_hM_max.psd", "accel_hM_entropy", "accel_hM_fc", "accel_hM_kurt", "accel_hM_skew",
            "accel_vM_mean", "accel_vM_std", "accel_vM_max", "accel_vM_min", "accel_vM_mad", "accel_vM_iqr",
            "accel_vM_max.corr", "accel_vM_idx.max.corr", "accel_vM_zcr", "accel_vM_fzc",
            "accel_vM_max.psd", "accel_vM_entropy", "accel_vM_fc", "accel_vM_kurt", "accel_vM_skew",
            "gravityM_mean", "gravityM_std", "gravityM_max", "gravityM_min", "gravityM_mad", "gravityM_iqr",
            "gravityM_max.corr", "gravityM_idx.max.corr", "gravityM_zcr", "gravityM_fzc",
            "gravityX_mean", "gravityX_std", "gravityX_max", "gravityX_min", "gravityX_mad", "gravityX_iqr",
            "gravityX_max.corr", "gravityX_idx.max.corr", "gravityX_zcr", "gravityX_fzc",
            "gravityY_mean", "gravityY_std", "gravityY_max", "gravityY_min", "gravityY_mad", "gravityY_iqr",
            "gravityY_max.corr", "gravityY_idx.max.corr", "gravityY_zcr", "gravityY_fzc",
            "gravityZ_mean", "gravityZ_std", "gravityZ_max", "gravityZ_min", "gravityZ_mad", "gravityZ_iqr",
            "gravityZ_max.corr", "gravityZ_idx.max.corr", "gravityZ_zcr", "gravityZ_fzc",
            "gravityM_max.psd", "gravityM_entropy", "gravityM_fc", "gravityM_kurt", "gravityM_skew",
            "gravityX_max.psd", "gravityX_entropy", "gravityX_fc", "gravityX_kurt", "gravityX_skew",
            "gravityY_max.psd", "gravityY_entropy", "gravityY_fc", "gravityY_kurt", "gravityY_skew",
            "gravityZ_max.psd", "gravityZ_entropy", "gravityZ_fc", "gravityZ_kurt", "gravityZ_skew",
            "gyroM_mean", "gyroM_std", "gyroM_max", "gyroM_min", "gyroM_mad", "gyroM_iqr",
            "gyroM_max.corr", "gyroM_idx.max.corr", "gyroM_zcr", "gyroM_fzc",
            "gyroX_mean", "gyroX_std", "gyroX_max", "gyroX_min", "gyroX_mad", "gyroX_iqr",
            "gyroX_max.corr", "gyroX_idx.max.corr", "gyroX_zcr", "gyroX_fzc",
            "gyroY_mean", "gyroY_std", "gyroY_max", "gyroY_min", "gyroY_mad", "gyroY_iqr",
            "gyroY_max.corr", "gyroY_idx.max.corr", "gyroY_zcr", "gyroY_fzc",
            "gyroZ_mean", "gyroZ_std", "gyroZ_max", "gyroZ_min", "gyroZ_mad", "gyroZ_iqr",
            "gyroZ_max.corr", "gyroZ_idx.max.corr", "gyroZ_zcr", "gyroZ_fzc",
            "gyroM_max.psd", "gyroM_entropy", "gyroM_fc", "gyroM_kurt", "gyroM_skew",
            "gyroX_max.psd", "gyroX_entropy", "gyroX_fc", "gyroX_kurt", "gyroX_skew",
            "gyroY_max.psd", "gyroY_entropy", "gyroY_fc", "gyroY_kurt", "gyroY_skew",
            "gyroZ_max.psd", "gyroZ_entropy", "gyroZ_fc", "gyroZ_kurt", "gyroZ_skew",
            "jerk_hM_mean", "jerk_hM_std", "jerk_hM_max", "jerk_hM_min", "jerk_hM_mad", "jerk_hM_iqr",
            "jerk_hM_max.corr", "jerk_hM_idx.max.corr", "jerk_hM_zcr", "jerk_hM_fzc",
            "jerk_hM_max.psd", "jerk_hM_entropy", "jerk_hM_fc", "jerk_hM_kurt", "jerk_hM_skew",
            "jerk_vM_mean", "jerk_vM_std", "jerk_vM_max", "jerk_vM_min", "jerk_vM_mad", "jerk_vM_iqr",
            "jerk_vM_max.corr", "jerk_vM_idx.max.corr", "jerk_vM_zcr", "jerk_vM_fzc",
            "jerk_vM_max.psd", "jerk_vM_entropy", "jerk_vM_fc", "jerk_vM_kurt", "jerk_vM_skew",
            "linear_accelM_mean", "linear_accelM_std", "linear_accelM_max", "linear_accelM_min", "linear_accelM_mad", "linear_accelM_iqr",
            "linear_accelM_max.corr", "linear_accelM_idx.max.corr", "linear_accelM_zcr", "linear_accelM_fzc",
            "linear_accelM_max.psd", "linear_accelM_entropy", "linear_accelM_fc", "linear_accelM_kurt", "linear_accelM_skew",
            "magM_mean", "magM_std", "magM_max", "magM_min", "magM_mad", "magM_iqr",
            "magM_max.corr", "magM_idx.max.corr", "magM_zcr", "magM_fzc",
            "magX_mean", "magX_std", "magX_max", "magX_min", "magX_mad", "magX_iqr",
            "magX_max.corr", "magX_idx.max.corr", "magX_zcr", "magX_fzc",
            "magY_mean", "magY_std", "magY_max", "magY_min", "magY_mad", "magY_iqr",
            "magY_max.corr", "magY_idx.max.corr", "magY_zcr", "magY_fzc",
            "magZ_mean", "magZ_std", "magZ_max", "magZ_min", "magZ_mad", "magZ_iqr",
            "magZ_max.corr", "magZ_idx.max.corr", "magZ_zcr", "magZ_fzc",
            "magM_max.psd", "magM_entropy", "magM_fc", "magM_kurt", "magM_skew",
            "magX_max.psd", "magX_entropy", "magX_fc", "magX_kurt", "magX_skew",
            "magY_max.psd", "magY_entropy", "magY_fc", "magY_kurt", "magY_skew",
            "magZ_max.psd", "magZ_entropy", "magZ_fc", "magZ_kurt", "magZ_skew",
            "pressureM_mean", "pressureM_std", "pressureM_max", "pressureM_min", "pressureM_mad", "pressureM_iqr",
            "pressureM_max.corr", "pressureM_idx.max.corr", "pressureM_zcr", "pressureM_fzc",
            "pressureM_max.psd", "pressureM_entropy", "pressureM_fc", "pressureM_kurt", "pressureM_skew"
    );
}
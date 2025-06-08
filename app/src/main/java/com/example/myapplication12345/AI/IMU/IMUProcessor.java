package com.example.myapplication12345.AI.IMU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class IMUProcessor {

    public static List<Map<String, Object>> preImu(List<Map<String, Object>> imu) {
        if (imu == null || imu.isEmpty()) {
            throw new IllegalArgumentException("⚠ IMU 데이터가 비어 있습니다! CSV 파일을 확인하세요.");
        }

        List<String> sensors = Arrays.asList("gyro", "accel", "mag", "rot", "pressure", "gravity", "linear_accel");
        List<String> enabledSensors = Arrays.asList("gyro", "accel", "linear_accel", "accel_h", "accel_v", "jerk_h",
                "jerk_v", "mag", "gravity", "pressure");

        Map<String, Integer> channels = new HashMap<>(sensors.size());
        for (String sensor : sensors) {
            channels.put(sensor, getSensorChannelCount(sensor));
        }

        // 타임스탬프별로 그룹화된 데이터 준비
        Map<Long, List<Map<String, Object>>> groupedByTimestamp = groupByTimestamp(imu);

        Map<String, double[][][]> dfs = new HashMap<>(sensors.size());
        for (String sensor : sensors) {
            dfs.put(sensor, null);
        }

        for (String sensor : sensors) {
            String usingSensorData = IMUConfig.getUsingSensorData(sensor);
            if (usingSensorData == null) {
                usingSensorData = sensor;
            }
            double[][] cutData = cutImu(usingSensorData, channels.get(usingSensorData), imu);
            double[][][] reshapedData = reshapeDataByTimestamp(cutData, groupedByTimestamp);
            dfs.put(sensor, reshapedData);
        }

        Map<String, Object> calcDfs = new HashMap<>(enabledSensors.size() + 1);
        calcDfs.putAll(getUniqueTimestamps(imu));

        for (String sensor : enabledSensors) {
            int numChannels = IMUConfig.getSensorChannels(sensor);
            boolean processEachAxis = IMUConfig.isProcessEachAxis(sensor);
            boolean calculateJerk = IMUConfig.isCalculateJerkEnabled(sensor);
            String process = IMUConfig.getProcessType(sensor);

            Map<String, double[][]> processed = IMUProcessing.processingImu(
                    dfs.get(IMUConfig.getUsingSensorData(sensor)),
                    numChannels,
                    process,
                    processEachAxis,
                    calculateJerk,
                    dfs.get("rot"),
                    dfs.get("gravity"),
                    sensor
            );

            calcDfs.putAll(replaceNaNWithZero(processed));
        }

        return processData(concatenateAll(calcDfs));
    }

    /**
     * Map<String, double[][]> 내의 모든 NaN 값을 0.0으로 변경한 새로운 Map을 반환합니다.
     * 각 double[][]은 (1, 1) 형태의 스칼라 피처 값을 담고 있다고 가정합니다.
     *
     * @param featureMap 원본 피처 맵
     * @return NaN이 0.0으로 대체된 새로운 피처 맵
     */
    public static Map<String, double[][]> replaceNaNWithZero(Map<String, double[][]> featureMap) {
        if (featureMap == null) {
            return null; // 또는 new HashMap<>();
        }

        Map<String, double[][]> newFeatureMap = new HashMap<>();

        for (Map.Entry<String, double[][]> entry : featureMap.entrySet()) {
            String key = entry.getKey();
            double[][] originalValues = entry.getValue();
            double[][] newValues = null;

            if (originalValues != null) {
                newValues = new double[originalValues.length][];
                for (int i = 0; i < originalValues.length; i++) {
                    if (originalValues[i] != null) {
                        newValues[i] = new double[originalValues[i].length];
                        for (int j = 0; j < originalValues[i].length; j++) {
                            if (Double.isNaN(originalValues[i][j])) {
                                newValues[i][j] = 0.0;
                            } else {
                                newValues[i][j] = originalValues[i][j];
                            }
                        }
                    } else {
                        newValues[i] = null; // 내부 배열이 null이면 그대로 유지
                    }
                }
            }
            newFeatureMap.put(key, newValues);
        }
        return newFeatureMap;
    }

    /**
     * 타임스탬프 기준으로 IMU 데이터 그룹화
     */
    private static Map<Long, List<Map<String, Object>>> groupByTimestamp(List<Map<String, Object>> imuData) {
        Map<Long, List<Map<String, Object>>> groupedByTimestamp = new HashMap<>(imuData.size() / 10);
        for (Map<String, Object> entry : imuData) {
            long timestamp = getFirstValueAsLong(entry.get("timestamp"));
            groupedByTimestamp.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(entry);
        }
        return groupedByTimestamp;
    }

    /**
     * 데이터를 타임스탬프별로 3D 배열로 변환
     */
    private static double[][][] reshapeDataByTimestamp(double[][] data, Map<Long, List<Map<String, Object>>> groupedByTimestamp) {
        int numTimestamps = groupedByTimestamp.size();
        int maxSize = groupedByTimestamp.values().stream().mapToInt(List::size).max().orElse(0);
        int cols = data[0].length;

        double[][][] reshaped = new double[numTimestamps][maxSize][cols];
        int timestampIdx = 0;
        int dataIdx = 0;

        for (List<Map<String, Object>> group : groupedByTimestamp.values()) {
            for (int i = 0; i < group.size() && dataIdx < data.length; i++) {
                System.arraycopy(data[dataIdx++], 0, reshaped[timestampIdx][i], 0, cols);
            }
            timestampIdx++;
        }
        return reshaped;
    }

    /**
     * 병합된 데이터를 리스트 형태로 변환
     */
    private static List<Map<String, Object>> processData(Map<String, Object> dataMap) {
        if (dataMap.isEmpty()) return Collections.emptyList();
        int numRows = ((double[][]) dataMap.values().iterator().next()).length;
        List<Map<String, Object>> resultList = new ArrayList<>(numRows);

        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String header : predefinedHeaders) {
                Object data = dataMap.get(header);
                if (data instanceof double[][] && rowIndex < ((double[][]) data).length && ((double[][]) data)[rowIndex].length == 1) {
                    entry.put(header, ((double[][]) data)[rowIndex][0]);
                } else if (data instanceof long[][] && rowIndex < ((long[][]) data).length && ((long[][]) data)[rowIndex].length == 1) {
                    entry.put(header, ((long[][]) data)[rowIndex][0]);
                }
            }
            resultList.add(entry);
        }
        return resultList;
    }

    /**
     * 유니크 타임스탬프 추출
     */
    private static Map<String, long[][]> getUniqueTimestamps(List<Map<String, Object>> imu) {
        Set<Long> timestamps = new TreeSet<>();
        for (Map<String, Object> imuEntry : imu) {
            Object ts = imuEntry.get("timestamp");
            if (ts != null) timestamps.add(getFirstValueAsLong(ts));
        }

        long[][] timestampArray = new long[timestamps.size()][1];
        int index = 0;
        for (Long time : timestamps) {
            timestampArray[index++][0] = time;
        }

        Map<String, long[][]> result = new HashMap<>(1);
        result.put("timestamp", timestampArray);
        return result;
    }

    /**
     * 객체에서 Long 값 추출 - Java 11 호환
     */
    private static long getFirstValueAsLong(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Number) {
                    return ((Number) first).longValue();
                }
            }
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }

    /**
     * 센서 데이터 잘라서 반환
     */
    private static double[][] cutImu(String sensor, int numChannels, List<Map<String, Object>> imu) {
        double[][] data = new double[imu.size()][numChannels];
        int row = 0;

        for (Map<String, Object> imuEntry : imu) {
            double[] currentRow = data[row++];
            int col = 0;
            if (imuEntry.containsKey(sensor + ".x")) currentRow[col++] = getFirstValue(imuEntry.get(sensor + ".x"));
            if (numChannels > 1 && imuEntry.containsKey(sensor + ".y")) currentRow[col++] = getFirstValue(imuEntry.get(sensor + ".y"));
            if (numChannels > 2 && imuEntry.containsKey(sensor + ".z")) currentRow[col++] = getFirstValue(imuEntry.get(sensor + ".z"));
            if (numChannels > 3 && imuEntry.containsKey(sensor + ".w")) currentRow[col] = getFirstValue(imuEntry.get(sensor + ".w"));
        }
        return data;
    }

    /**
     * 객체에서 double 값 추출 - Java 11 호환
     */
    private static double getFirstValue(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Number) {
                    return ((Number) first).floatValue();
                }
            }
        } else if (obj instanceof Number) {
            return ((Number) obj).floatValue();
        }
        return 0.0f;
    }

    /**
     * 모든 데이터를 병합하여 하나의 맵으로 반환
     */
    private static Map<String, Object> concatenateAll(Map<String, Object> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            throw new IllegalArgumentException("⚠ 데이터 맵이 비어 있습니다.");
        }

        Map<String, Object> sensorDataMap = new HashMap<>(dataMap.size());
        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            Object data = entry.getValue();
            if (data instanceof double[][] && ((double[][]) data).length > 0) {
                sensorDataMap.put(entry.getKey(), ((double[][]) data).clone());
            } else if (data instanceof long[][] && ((long[][]) data).length > 0) {
                sensorDataMap.put(entry.getKey(), ((long[][]) data).clone());
            }
        }
        return sensorDataMap;
    }

    private static int getSensorChannelCount(String sensor) {
        if ("gyro".equals(sensor) || "accel".equals(sensor) || "mag".equals(sensor) ||
                "gravity".equals(sensor) || "linear_accel".equals(sensor)) {
            return 3;
        } else if ("rot".equals(sensor)) {
            return 4;
        } else if ("pressure".equals(sensor)) {
            return 1;
        }
        return 0;
    }

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
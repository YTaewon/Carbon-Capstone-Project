package com.example.myapplication12345.AI.IMU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class IMUProcessor {
    public static List<Map<String, Object>> preImu(List<Map<String, Object>> imu) {
        if (imu == null || imu.isEmpty()) {
            throw new IllegalArgumentException("⚠ IMU 데이터가 비어 있습니다! CSV 파일을 확인하세요.");
        }

        // ✅ 센서 목록 정렬
        List<String> sensors = Arrays.asList("gyro", "accel", "mag", "rot", "pressure", "gravity", "linear_accel");

        List<String> enabledSensors = Arrays.asList("gyro", "accel", "linear_accel", "accel_h", "accel_v", "jerk_h",
                "jerk_v", "mag", "gravity", "pressure");

        Map<String, Integer> channels = new HashMap<>();

        for (String sensor : sensors) {
            channels.put(sensor, getSensorChannelCount(sensor));
        }
        imu = extendIMUDataByTimestamp(imu);
        Collections.sort(sensors);

        // ✅ 센서 데이터 초기화
        Map<String, double[][][]> dfs = new HashMap<>();
        for (String sensor : sensors) {
            dfs.put(sensor, null);
        }

        // ✅ 센서별 데이터 분할 및 변환
        for (String sensor : sensors) {
            String usingSensorData = IMUConfig.getUsingSensorData(sensor);
            if (usingSensorData == null) {
                usingSensorData = sensor; // 기본적으로 자신을 사용
            }
            double[][] cutData = cutImu(usingSensorData, channels.get(usingSensorData), imu);
            int cols = cutData[0].length;
            //System.out.println("Debug: " + sensor + " to "+ usingSensorData +" has " + cols + " columns");

            // 3D 배열 변환 (-1, 100, cols)

            double[][][] reshapedData = reshapeData(cutData, cols);
            dfs.put(sensor, reshapedData);
        }

        // ✅ 최종 데이터 병합 준비 (타임스탬프 추가)
        Map<String, Object> calcDfs = new HashMap<>();
        calcDfs.putAll(getUniqueTimestamps(imu));

        // ✅ `processingImu()` 호출하여 데이터 전처리 수행
        for (String sensor : enabledSensors) {
            int numChannels = IMUConfig.getSensorChannels(sensor);
            boolean configStatFeatures = IMUConfig.isStatFeaturesEnabled(sensor);  // ✅ 변수명 변경
            boolean configSpectralFeatures = IMUConfig.isSpectralFeaturesEnabled(sensor);
            boolean configProcessEachAxis = IMUConfig.isProcessEachAxis(sensor);
            boolean configCalculateJerk = IMUConfig.isCalculateJerkEnabled(sensor);
            String configProcess = IMUConfig.getProcessType(sensor);

            Map<String, double[][]> processed = IMUProcessoing.processingImu(
                    dfs.get(IMUConfig.getUsingSensorData(sensor)),
                    numChannels,
                    configStatFeatures,
                    configSpectralFeatures,
                    configProcess,
                    configProcessEachAxis,
                    configCalculateJerk,
                    dfs.get("rot"),
                    dfs.get("gravity"),
                    sensor
            );
            calcDfs.putAll(processed);
        }

        // ✅ 최종 데이터 병합 후 반환
        return processData(concatenateAll(calcDfs));
    }

    public static List<Map<String, Object>> extendIMUDataByTimestamp(List<Map<String, Object>> imuData) {
        // 그룹화된 데이터 저장
        Map<Long, List<Map<String, Object>>> groupedByTimestamp = new HashMap<>();

        // 각 행을 timestamp 기준으로 그룹화
        for (Map<String, Object> entry : imuData) {
            long timestamp = getFirstValueAsLong(entry.get("timestamp"));
            groupedByTimestamp.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(entry);
        }

        List<Map<String, Object>> extendedData = new ArrayList<>();

        // 각 그룹에 대해 100개가 될 때까지 복제
        for (Map.Entry<Long, List<Map<String, Object>>> entry : groupedByTimestamp.entrySet()) {
            List<Map<String, Object>> group = entry.getValue();
            while (group.size() < 100) {
                // 랜덤하게 복제
                int randomIndex = new Random().nextInt(group.size());
                Map<String, Object> clonedEntry = new HashMap<>(group.get(randomIndex));
                group.add(clonedEntry);
            }
            extendedData.addAll(group);
        }

        return extendedData;
    }

    public static List<Map<String, Object>> processData(Map<String, Object> dataMap) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        int numRows = dataMap.isEmpty() ? 0 : ((double[][]) dataMap.values().iterator().next()).length;

        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            Map<String, Object> entry = new LinkedHashMap<>();

            for (String header : predefinedHeaders) {
                if (dataMap.containsKey(header)) {
                    Object data = dataMap.get(header);

                    if (data instanceof double[][]) {
                        double[][] doubleData = (double[][]) data;
                        if (rowIndex < doubleData.length && doubleData[rowIndex].length == 1) {
                            entry.put(header, doubleData[rowIndex][0]);
                        }
                    } else if (data instanceof long[][]) {
                        long[][] longData = (long[][]) data;
                        if (rowIndex < longData.length && longData[rowIndex].length == 1) {
                            entry.put(header, longData[rowIndex][0]);
                        }
                    }
                }
            }

            resultList.add(entry);
        }

        return resultList;
    }


    /**
     * 센서 데이터를 3D 배열로 변환 (-1, 100, cols)
     */
    private static double[][][] reshapeData(double[][] data, int cols) {
        int numSegments = data.length / 100;
        double[][][] reshaped = new double[numSegments][100][cols];

        for (int i = 0; i < numSegments; i++) {
            for (int j = 0; j < 100; j++) {
                System.arraycopy(data[i * 100 + j], 0, reshaped[i][j], 0, cols);
            }
        }
        return reshaped;
    }
    /**
     * 유니크 타임스탬프 데이터 추출
     */
    private static Map<String, long[][]> getUniqueTimestamps(List<Map<String, Object>> imu) {
        Set<Long> timestamps = new TreeSet<>();

        // imu 리스트를 순회하면서 "timestamp" 데이터를 추출
        for (Map<String, Object> imuEntry : imu) {
            if (imuEntry.containsKey("timestamp")) {
                long time = getFirstValueAsLong(imuEntry.get("timestamp"));
                timestamps.add(time);
            }
        }

        // TreeSet을 2D 배열로 변환
        long[][] timestampArray = new long[timestamps.size()][1];
        int index = 0;
        for (Long time : timestamps) {
            timestampArray[index++][0] = time;
        }

        // 고유한 타임스탬프를 결과 맵에 저장
        Map<String, long[][]> result = new HashMap<>();
        result.put("timestamp", timestampArray);

        return result;
    }

    // getFirstValueAsLong method to handle Long values
    private static long getFirstValueAsLong(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (!list.isEmpty() && list.get(0) instanceof Number) {
                return ((Number) list.get(0)).longValue();
            }
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return 0L;
    }


    /**
     * 센서 데이터를 잘라서 반환 (Python의 cut_imu() 변환)
     */
    private static double[][] cutImu(String sensor, int numChannels, List<Map<String, Object>> imu) {
        // 데이터를 저장할 리스트
        List<double[]> dataList = new ArrayList<>();

        // imu 리스트를 순회하며 데이터를 추출
        for (Map<String, Object> imuEntry : imu) {
            double[] row = new double[numChannels];
            int col = 0;

            // X축 데이터
            if (imuEntry.containsKey(sensor + ".x")) {
                row[col++] = getFirstValue(imuEntry.get(sensor + ".x"));
            }

            // Y축 데이터 (필요한 경우)
            if (numChannels > 1 && imuEntry.containsKey(sensor + ".y")) {
                row[col++] = getFirstValue(imuEntry.get(sensor + ".y"));
            }

            // Z축 데이터 (필요한 경우)
            if (numChannels > 2 && imuEntry.containsKey(sensor + ".z")) {
                row[col++] = getFirstValue(imuEntry.get(sensor + ".z"));
            }

            // W축 데이터 (필요한 경우)
            if (numChannels > 3 && imuEntry.containsKey(sensor + ".w")) {
                row[col] = getFirstValue(imuEntry.get(sensor + ".w"));
            }

            // 리스트에 추가
            dataList.add(row);
        }

        // 리스트를 2D 배열로 변환
        return dataList.toArray(new double[0][]);
    }

    private static double getFirstValue(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (!list.isEmpty() && list.get(0) instanceof Number) {
                return ((Number) list.get(0)).doubleValue();
            }
        } else if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }



    /**
     * ✅ IMU 데이터를 병합하여 하나의 2D 배열로 반환 (row 크기 자동 조정)
     */
    private static Map<String, Object> concatenateAll(Map<String, Object> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            throw new IllegalArgumentException("⚠ Error: 데이터 맵이 비어 있습니다.");
        }

        Map<String, Object> sensorDataMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            Object data = entry.getValue();
            if (data instanceof double[][]) {
                double[][] doubleData = (double[][]) data;
                if (doubleData.length == 0) continue;

                int rows = doubleData.length;
                int cols = doubleData[0].length;

                double[][] newData = new double[rows][cols];

                for (int i = 0; i < rows; i++) {
                    System.arraycopy(doubleData[i], 0, newData[i], 0, cols);
                }

                sensorDataMap.put(entry.getKey(), newData);
            } else if (data instanceof long[][]) {
                long[][] longData = (long[][]) data;
                if (longData.length == 0) continue;

                int rows = longData.length;
                int cols = longData[0].length;

                long[][] newData = new long[rows][cols];

                for (int i = 0; i < rows; i++) {
                    System.arraycopy(longData[i], 0, newData[i], 0, cols);
                }

                sensorDataMap.put(entry.getKey(), newData);
            }
        }

        return sensorDataMap;
    }





    /**
     * ✅ 센서별 채널 개수를 반환
     */
    private static int getSensorChannelCount(String sensor) {
        switch (sensor) {
            case "gyro":
            case "accel":
            case "mag":
            case "gravity":
            case "linear_accel":
                return 3;
            case "rot":
                return 4;
            case "pressure":
                return 1;
            default:
                return 0;
        }
    }

    private static final List<String> predefinedHeaders = Arrays.asList(
            "timestamp",
            // ✅ 가속도계 (Accelerometer) 기본 통계
            "accelM_mean", "accelM_std", "accelM_max", "accelM_min", "accelM_mad", "accelM_iqr",
            "accelM_max.corr", "accelM_idx.max.corr", "accelM_zcr", "accelM_fzc",
            "accelX_mean", "accelX_std", "accelX_max", "accelX_min", "accelX_mad", "accelX_iqr",
            "accelX_max.corr", "accelX_idx.max.corr", "accelX_zcr", "accelX_fzc",
            "accelY_mean", "accelY_std", "accelY_max", "accelY_min", "accelY_mad", "accelY_iqr",
            "accelY_max.corr", "accelY_idx.max.corr", "accelY_zcr", "accelY_fzc",
            "accelZ_mean", "accelZ_std", "accelZ_max", "accelZ_min", "accelZ_mad", "accelZ_iqr",
            "accelZ_max.corr", "accelZ_idx.max.corr", "accelZ_zcr", "accelZ_fzc",
            "accelM_max.psd", "accelM_entropy", "accelM_fc", "accelM_kurt", "accelM_skew",
            // ✅ 가속도계 (Accelerometer) per-axis PSD 및 통계
            "accelX_max.psd", "accelX_entropy", "accelX_fc", "accelX_kurt", "accelX_skew",
            "accelY_max.psd", "accelY_entropy", "accelY_fc", "accelY_kurt", "accelY_skew",
            "accelZ_max.psd", "accelZ_entropy", "accelZ_fc", "accelZ_kurt", "accelZ_skew",
            // ✅ 가속도계 수평/수직 (Horizontal/Vertical)
            "accel_hM_mean", "accel_hM_std", "accel_hM_max", "accel_hM_min", "accel_hM_mad", "accel_hM_iqr",
            "accel_hM_max.corr", "accel_hM_idx.max.corr", "accel_hM_zcr", "accel_hM_fzc",
            "accel_hM_max.psd", "accel_hM_entropy", "accel_hM_fc", "accel_hM_kurt", "accel_hM_skew",
            "accel_vM_mean", "accel_vM_std", "accel_vM_max", "accel_vM_min", "accel_vM_mad", "accel_vM_iqr",
            "accel_vM_max.corr", "accel_vM_idx.max.corr", "accel_vM_zcr", "accel_vM_fzc",
            "accel_vM_max.psd", "accel_vM_entropy", "accel_vM_fc", "accel_vM_kurt", "accel_vM_skew",
            // ✅ 중력 센서 (Gravity)
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
            // ✅ 자이로스코프 (Gyroscope)
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
            // ✅ 저크 센서 (Jerk) - 수평 및 수직
            "jerk_hM_mean", "jerk_hM_std", "jerk_hM_max", "jerk_hM_min", "jerk_hM_mad", "jerk_hM_iqr",
            "jerk_hM_max.corr", "jerk_hM_idx.max.corr", "jerk_hM_zcr", "jerk_hM_fzc",
            "jerk_hM_max.psd", "jerk_hM_entropy", "jerk_hM_fc", "jerk_hM_kurt", "jerk_hM_skew",
            "jerk_vM_mean", "jerk_vM_std", "jerk_vM_max", "jerk_vM_min", "jerk_vM_mad", "jerk_vM_iqr",
            "jerk_vM_max.corr", "jerk_vM_idx.max.corr", "jerk_vM_zcr", "jerk_vM_fzc",
            "jerk_vM_max.psd", "jerk_vM_entropy", "jerk_vM_fc", "jerk_vM_kurt", "jerk_vM_skew",
            // ✅ 선형 가속도 센서 (Linear Acceleration)
            "linear_accelM_mean", "linear_accelM_std", "linear_accelM_max", "linear_accelM_min", "linear_accelM_mad", "linear_accelM_iqr",
            "linear_accelM_max.corr", "linear_accelM_idx.max.corr", "linear_accelM_zcr", "linear_accelM_fzc",
            "linear_accelM_max.psd", "linear_accelM_entropy", "linear_accelM_fc", "linear_accelM_kurt", "linear_accelM_skew",
            // ✅ 자기장 센서 (Magnetometer)
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
            // ✅ 압력 센서 (Pressure)
            "pressureM_mean", "pressureM_std", "pressureM_max", "pressureM_min", "pressureM_mad", "pressureM_iqr",
            "pressureM_max.corr", "pressureM_idx.max.corr", "pressureM_zcr", "pressureM_fzc",
            "pressureM_max.psd", "pressureM_entropy", "pressureM_fc", "pressureM_kurt", "pressureM_skew"
    );
}

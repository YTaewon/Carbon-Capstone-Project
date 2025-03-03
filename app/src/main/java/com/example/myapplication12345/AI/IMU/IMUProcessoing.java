package com.example.myapplication12345.AI.IMU;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IMUProcessoing {

    /**
     * IMU 데이터 처리
     */
    public static Map<String, float[][]> processingImu(
            float[][][] sensor,
            int numChannels,
            boolean statFeatures,
            boolean spectralFeatures,
            String process,
            boolean processEachAxis,
            boolean calculateJerk,
            float[][][] rotation,
            float[][][] gravity,
            String prefix) {
        if (sensor == null || sensor.length == 0) {
            return new HashMap<>();
        }

        int rows = sensor.length;
        int cols = sensor[0].length;

        if (cols == 0) {
            return new HashMap<>();
        }

        float[][] x = new float[rows][cols];
        float[][] y = new float[rows][cols];
        float[][] z = new float[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                x[i][j] = sensor[i][j][0];
                if (numChannels >= 3) {
                    y[i][j] = sensor[i][j][1];
                    z[i][j] = sensor[i][j][2];
                }
            }
        }

        float[][] magnitude;
        Map<String, float[][]> jerk = null;

        // process가 null일 경우 기본값 설정
        String safeProcess = process != null ? process : "default";

        // 처리 유형에 따라 데이터 변환
        switch (safeProcess) {
            case "rotate":
                float[][][] rotated = com.example.myapplication12345.AI.IMU.IMUUtils.rotateAxis(x, y, z, rotation);
                x = rotated[0];
                y = rotated[1];
                z = rotated[2];
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                break;
            case "horizontal":
                float[][] thetaH = com.example.myapplication12345.AI.IMU.IMUUtils.calculateAngle(x, y, z, gravity);
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        magnitude[i][j] *= (float) Math.cos(thetaH[i][j]);
                    }
                }
                if (calculateJerk) {
                    jerk = com.example.myapplication12345.AI.IMU.IMUUtils.diff(magnitude);
                }
                break;
            case "vertical":
                float[][] thetaV = com.example.myapplication12345.AI.IMU.IMUUtils.calculateAngle(x, y, z, gravity);
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        magnitude[i][j] *= (float) Math.sin(thetaV[i][j]);
                    }
                }
                if (calculateJerk) {
                    jerk = com.example.myapplication12345.AI.IMU.IMUUtils.diff(magnitude);
                }
                break;
            default:
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                break;
        }

        Map<String, float[][]> features;
        Map<String, float[][]> statFeaturesData = null;
        Map<String, float[][]> spectralFeaturesData = null;

        // 통계 특징 계산
        if (statFeatures) {
            statFeaturesData = IMUFeatureExtractor.calculateStatFeatures(magnitude, prefix + "M");
            if (processEachAxis && numChannels > 1) {
                Map<String, float[][]> statFeaturesX = IMUFeatureExtractor.calculateStatFeatures(x, prefix + "X");
                Map<String, float[][]> statFeaturesY = IMUFeatureExtractor.calculateStatFeatures(y, prefix + "Y");
                Map<String, float[][]> statFeaturesZ = IMUFeatureExtractor.calculateStatFeatures(z, prefix + "Z");
                statFeaturesData = concatenateArrays(statFeaturesData, statFeaturesX, statFeaturesY, statFeaturesZ);
            }
        }

        // 주파수 특징 계산
        if (spectralFeatures) {
            spectralFeaturesData = IMUFeatureExtractor.calculateSpectralFeatures(magnitude, prefix + "M");
            if (processEachAxis && numChannels > 1) {
                Map<String, float[][]> spectralFeaturesX = IMUFeatureExtractor.calculateSpectralFeatures(x, prefix + "X");
                Map<String, float[][]> spectralFeaturesY = IMUFeatureExtractor.calculateSpectralFeatures(y, prefix + "Y");
                Map<String, float[][]> spectralFeaturesZ = IMUFeatureExtractor.calculateSpectralFeatures(z, prefix + "Z");
                spectralFeaturesData = concatenateArrays(spectralFeaturesData, spectralFeaturesX, spectralFeaturesY, spectralFeaturesZ);
            }
        }

        // 결과 병합
        if (statFeatures && spectralFeatures) {
            features = concatenateArrays(statFeaturesData, spectralFeaturesData);
        } else if (statFeatures) {
            features = statFeaturesData;
        } else if (spectralFeatures) {
            features = spectralFeaturesData;
        } else {
            features = new HashMap<>(); // 특징이 없으면 빈 맵 반환
        }

        // Jerk 데이터 추가
        if (calculateJerk && jerk != null) {
            features.putAll(jerk);
        }
        return features;
    }
    /**
     * 여러 맵의 2D 배열을 키별로 병합
     */
    @SafeVarargs
    private static Map<String, float[][]> concatenateArrays(Map<String, float[][]>... maps) {
        Map<String, List<float[][]>> combinedMap = new HashMap<>();

        // 각 맵에서 배열 추출 및 결합
        for (Map<String, float[][]> mapData : maps) {
            for (Map.Entry<String, float[][]> entry : mapData.entrySet()) {
                String key = entry.getKey();
                float[][] array = entry.getValue();

                if (array != null && array.length > 0 && array[0].length > 0) {
                    combinedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(array);
                }
            }
        }

        // 병합된 배열 생성
        Map<String, float[][]> resultMap = new HashMap<>();
        for (Map.Entry<String, List<float[][]>> entry : combinedMap.entrySet()) {
            String key = entry.getKey();
            List<float[][]> arrays = entry.getValue();

            int rows = arrays.get(0).length;
            int totalCols = arrays.stream().mapToInt(a -> a[0].length).sum();

            float[][] concatenatedArray = new float[rows][totalCols];
            int colOffset = 0;
            for (float[][] array : arrays) {
                for (int i = 0; i < rows; i++) {
                    System.arraycopy(array[i], 0, concatenatedArray[i], colOffset, array[i].length);
                }
                colOffset += array[0].length;
            }

            resultMap.put(key, concatenatedArray);
        }

        return resultMap;
    }
}
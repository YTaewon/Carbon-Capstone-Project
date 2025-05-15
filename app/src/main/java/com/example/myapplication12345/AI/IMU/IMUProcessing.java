package com.example.myapplication12345.AI.IMU;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IMUProcessing {

    /**
     * IMU 데이터 처리
     */
    public static Map<String, double[][]> processingImu(
            double[][][] sensor,
            int numChannels,
            boolean statFeatures,
            boolean spectralFeatures,
            String process,
            boolean processEachAxis,
            boolean calculateJerk,
            double[][][] rotation,
            double[][][] gravity,
            String prefix) {
        if (sensor == null || sensor.length == 0) {
            return new HashMap<>();
        }

        int rows = sensor.length;
        int cols = sensor[0].length;

        if (cols == 0) {
            return new HashMap<>();
        }

        double[][] x = new double[rows][cols];
        double[][] y = new double[rows][cols];
        double[][] z = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                x[i][j] = sensor[i][j][0];
                if (numChannels >= 3) {
                    y[i][j] = sensor[i][j][1];
                    z[i][j] = sensor[i][j][2];
                }
            }
        }

        double[][] magnitude;
        Map<String, double[][]> jerk = null;

        // process가 null일 경우 기본값 설정
        String safeProcess = process != null ? process : "default";

        // 처리 유형에 따라 데이터 변환
        switch (safeProcess) {
            case "rotate":
                double[][][] rotated = com.example.myapplication12345.AI.IMU.IMUUtils.rotateAxis(x, y, z, rotation);
                x = rotated[0];
                y = rotated[1];
                z = rotated[2];
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                break;
            case "horizontal":
                double[][] thetaH = com.example.myapplication12345.AI.IMU.IMUUtils.calculateAngle(x, y, z, gravity);
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        magnitude[i][j] *= (double) Math.cos(thetaH[i][j]);
                    }
                }
                if (calculateJerk) {
                    jerk = com.example.myapplication12345.AI.IMU.IMUUtils.diff(magnitude);
                }
                break;
            case "vertical":
                double[][] thetaV = com.example.myapplication12345.AI.IMU.IMUUtils.calculateAngle(x, y, z, gravity);
                magnitude = IMUFeatureExtractor.magnitude(x, y, z);
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        magnitude[i][j] *= (double) Math.sin(thetaV[i][j]);
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

        Map<String, double[][]> features;
        Map<String, double[][]> statFeaturesData = null;
        Map<String, double[][]> spectralFeaturesData = null;

        // 통계 특징 계산
        if (statFeatures) {
            statFeaturesData = IMUFeatureExtractor.calculateStatFeatures(magnitude, prefix + "M");
            if (processEachAxis && numChannels > 1) {
                Map<String, double[][]> statFeaturesX = IMUFeatureExtractor.calculateStatFeatures(x, prefix + "X");
                Map<String, double[][]> statFeaturesY = IMUFeatureExtractor.calculateStatFeatures(y, prefix + "Y");
                Map<String, double[][]> statFeaturesZ = IMUFeatureExtractor.calculateStatFeatures(z, prefix + "Z");
                statFeaturesData = concatenateArrays(statFeaturesData, statFeaturesX, statFeaturesY, statFeaturesZ);
            }
        }

        // 주파수 특징 계산
        if (spectralFeatures) {
            spectralFeaturesData = IMUFeatureExtractor.calculateSpectralFeatures(magnitude, prefix + "M");
            if (processEachAxis && numChannels > 1) {
                Map<String, double[][]> spectralFeaturesX = IMUFeatureExtractor.calculateSpectralFeatures(x, prefix + "X");
                Map<String, double[][]> spectralFeaturesY = IMUFeatureExtractor.calculateSpectralFeatures(y, prefix + "Y");
                Map<String, double[][]> spectralFeaturesZ = IMUFeatureExtractor.calculateSpectralFeatures(z, prefix + "Z");
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
    private static Map<String, double[][]> concatenateArrays(Map<String, double[][]>... maps) {
        Map<String, List<double[][]>> combinedMap = new HashMap<>();

        // 각 맵에서 배열 추출 및 결합
        for (Map<String, double[][]> mapData : maps) {
            for (Map.Entry<String, double[][]> entry : mapData.entrySet()) {
                String key = entry.getKey();
                double[][] array = entry.getValue();

                if (array != null && array.length > 0 && array[0].length > 0) {
                    combinedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(array);
                }
            }
        }

        // 병합된 배열 생성
        Map<String, double[][]> resultMap = new HashMap<>();
        for (Map.Entry<String, List<double[][]>> entry : combinedMap.entrySet()) {
            String key = entry.getKey();
            List<double[][]> arrays = entry.getValue();

            int rows = arrays.get(0).length;
            int totalCols = arrays.stream().mapToInt(a -> a[0].length).sum();

            double[][] concatenatedArray = new double[rows][totalCols];
            int colOffset = 0;
            for (double[][] array : arrays) {
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
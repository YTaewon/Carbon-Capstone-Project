package com.example.myapplication12345.AI.IMU;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IMUProcessoing {

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
            String prefix) 
        {
        //System.out.println("Starting :" + prefix);
        if (sensor == null || sensor.length == 0) {
            System.err.println("⚠ Warning: " + prefix + " 센서 데이터가 비어 있습니다.");
            return new HashMap<>();  // ✅ 빈 배열 반환
        }

        int rows = sensor.length;
        int cols = sensor[0].length;

        if (cols == 0) {
            System.err.println("⚠ Warning: " + prefix + " 데이터 크기가 잘못되었습니다.");
            return new HashMap<>();  // ✅ 빈 배열 반환
        }
        
        double[][] x = new double[rows][cols];
        double[][] y = new double[rows][cols];
        double[][] z = new double[rows][cols];

        //System.out.println("Debug: processingImu() called with input size: " + rows + "x" + cols);

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

        //System.out.println("Debug: Processing type - " + process);

        if ("rotate".equals(process)) {
            //System.out.println("Debug: Applying rotation transformation...");
            double[][][] rotated = IMUUtils.rotateAxis(x, y, z, rotation);
            x = rotated[0];
            y = rotated[1];
            z = rotated[2];
            magnitude = IMUFeatureExtractor.magnitude(x, y, z);
        } else if ("horizontal".equals(process)) {
            //System.out.println("Debug: Calculating horizontal component...");
            double[][] theta = IMUUtils.calculateAngle(x, y, z, gravity);
            magnitude = IMUFeatureExtractor.magnitude(x, y, z);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    magnitude[i][j] *= Math.cos(theta[i][j]);
                }
            }
            if(calculateJerk){
                //System.out.println("Debug: Calculating Jerk...");
                jerk = IMUUtils.diff(magnitude);
            }
        } else if ("vertical".equals(process)) {
            //System.out.println("Debug: Calculating vertical component...");
            double[][] theta = IMUUtils.calculateAngle(x, y, z, gravity);
            magnitude = IMUFeatureExtractor.magnitude(x, y, z);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    magnitude[i][j] *= Math.sin(theta[i][j]);
                }
            }
            if(calculateJerk){
                //System.out.println("Debug: Calculating Jerk...");
                jerk = IMUUtils.diff(magnitude);
            }
        } else {
            //System.out.println("Debug: No processing applied, using raw magnitude...");
            magnitude = IMUFeatureExtractor.magnitude(x, y, z);
        }

        //System.out.println("Debug: Magnitude computation completed.");

        Map<String, double[][]> features ;
        Map<String, double[][]> statFeaturesData = null;
        Map<String, double[][]> spectralFeaturesData = null;

        if (statFeatures) {
            System.out.println("Debug: Extracting statistical features...");
            statFeaturesData = IMUFeatureExtractor.calculateStatFeatures(magnitude, prefix + "M");
            if (processEachAxis && numChannels > 1) {
                Map<String, double[][]> statFeaturesX = IMUFeatureExtractor.calculateStatFeatures(x, prefix + "X");
                Map<String, double[][]> statFeaturesY = IMUFeatureExtractor.calculateStatFeatures(y, prefix + "Y");
                Map<String, double[][]> statFeaturesZ = IMUFeatureExtractor.calculateStatFeatures(z, prefix + "Z");
                statFeaturesData = concatenateArrays(statFeaturesData, statFeaturesX, statFeaturesY, statFeaturesZ);
            }
        }

        if (spectralFeatures) {
            //System.out.println("Debug: Extracting spectral features...");
            spectralFeaturesData = IMUFeatureExtractor.calculateSpectralFeatures(magnitude, prefix + "M");
            if (processEachAxis && numChannels > 1) {
                Map<String, double[][]> spectralFeaturesX = IMUFeatureExtractor.calculateSpectralFeatures(x, prefix + "X");
                Map<String, double[][]> spectralFeaturesY = IMUFeatureExtractor.calculateSpectralFeatures(y, prefix + "Y");
                Map<String, double[][]> spectralFeaturesZ = IMUFeatureExtractor.calculateSpectralFeatures(z, prefix + "Z");
                spectralFeaturesData = concatenateArrays(spectralFeaturesData, spectralFeaturesX, spectralFeaturesY, spectralFeaturesZ);
            }
        }

        if (statFeatures && spectralFeatures) {
            features = concatenateArrays(statFeaturesData, spectralFeaturesData);
            //System.out.println("Debug: Statistical and Spectral Features Returned");
        } else if (statFeatures) {
            features = statFeaturesData;
            //System.out.println("Debug: Statistical Features Returned");
        } else if (spectralFeatures) {
            features = spectralFeaturesData;
            //System.out.println("Debug: Spectral Features Returned");
        } else {
            features = new HashMap<>();  // ✅ 모든 데이터가 없을 경우 빈 배열 반환
        }
        
        // ✅ Jerk 데이터를 최종 피처셋에 추가
        if (calculateJerk) {
            features = concatenateArrays(features, jerk);
           // System.out.println("Debug: Jerk Features Added");
        }
        return features;
    }

    /**
     * 2D 배열을 가로 방향으로 병합
     */
    @SafeVarargs
    private static Map<String, double[][]> concatenateArrays(Map<String, double[][]>... maps) {
        Map<String, List<double[][]>> combinedMap = new HashMap<>();

        // Extract and combine arrays from each map by key
        for (Map<String, double[][]> mapData : maps) {
            for (Map.Entry<String, double[][]> entry : mapData.entrySet()) {
                String key = entry.getKey();
                double[][] array = entry.getValue();

                if (array != null && array.length > 0 && array[0].length > 0) {
                    combinedMap
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(array);
                }
            }
        }

        // Create the result map with concatenated arrays
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

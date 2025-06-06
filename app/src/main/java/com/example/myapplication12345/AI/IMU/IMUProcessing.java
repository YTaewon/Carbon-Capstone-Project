package com.example.myapplication12345.AI.IMU;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

public class IMUProcessing {
    private static final String TAG = "SensorDataProcessing";
    public static Map<String, double[][]> processingImu(
            double[][][] sensorInput3D,            // 대상 센서 데이터 [num_windows][window_size][num_channels]
            int numChannelsForSensor,              // sensorInput3D의 채널 수
            String processTypeFromConfig,          // config.ini "process" (예: "rotate", "horizontal")
            boolean processEachAxisConfig,         // config.ini "process_each_axis" 값
            boolean calculateJerkConfig,           // config.ini "calculate_jerk" 값
            double[][][] rotationQuaternionData3D, // "rot" 센서 데이터 [num_windows][window_size][4]
            double[][][] gravityVectorData3D,      // "gravity" 센서 데이터 [num_windows][window_size][3]
            String featureSetPrefix) {             // 예: "accel", "jerk_h", "gravity"

        if (sensorInput3D == null || sensorInput3D.length == 0) {
            System.err.printf(Locale.US, "PROC_IMU WARN [%s]: Input sensor data is null or empty.%n", featureSetPrefix);
            return new HashMap<>();
        }

        int numWindows = sensorInput3D.length;
        int windowSize = sensorInput3D[0] != null ? sensorInput3D[0].length : 0;

        if (windowSize == 0) {
            System.err.printf(Locale.US, "PROC_IMU WARN [%s]: Input sensor data has zero window size.%n", featureSetPrefix);
            return new HashMap<>();
        }
        // 입력 차원 확인
        // System.out.printf(Locale.US, "PROC_IMU INFO [%s]: Processing %d windows, windowSize=%d, numChannels=%d, process=%s, jerk=%b%n",
        //        featureSetPrefix, numWindows, windowSize, numChannelsForSensor, processTypeFromConfig, calculateJerkConfig);


        // 피처 계산에 사용될 1D 데이터 묶음
        // 형태: [numWindows][currentWindowSamples] (저크의 경우 currentWindowSamples = windowSize - 1)
        double[][] magnitudeSourceForFeatures = new double[numWindows][];

        // 각 축별 피처 계산에 사용될 데이터
        // 형태: [numWindows][windowSize]
        double[][] xProcessedForAxisFeatures = new double[numWindows][windowSize];
        double[][] yProcessedForAxisFeatures = (numChannelsForSensor >= 2) ? new double[numWindows][windowSize] : null;
        double[][] zProcessedForAxisFeatures = (numChannelsForSensor >= 3) ? new double[numWindows][windowSize] : null;

        String safeProcessType = (processTypeFromConfig != null) ? processTypeFromConfig : "default";

        // 각 윈도우에 대해 반복 하며 magnitudeSource 및 처리된 축 데이터 계산 ---
        for (int i = 0; i < numWindows; i++) {
            if (sensorInput3D[i] == null || sensorInput3D[i].length != windowSize) {
                System.err.printf(Locale.US, "PROC_IMU ERROR [%s] Win %d: Inconsistent window size or null window in sensorInput3D.%n", featureSetPrefix, i);
                int len = calculateJerkConfig ? Math.max(0, windowSize - 1) : windowSize;
                magnitudeSourceForFeatures[i] = new double[len];
                Arrays.fill(magnitudeSourceForFeatures[i], Double.NaN);
                Arrays.fill(xProcessedForAxisFeatures[i], Double.NaN);
                if (yProcessedForAxisFeatures != null) Arrays.fill(yProcessedForAxisFeatures[i], Double.NaN);
                if (zProcessedForAxisFeatures != null) Arrays.fill(zProcessedForAxisFeatures[i], Double.NaN);
                continue;
            }

            // 현재 윈도우의 x, y, z 데이터를 2D 배치 형태로 추출 ([1][window_size])
            // 이는 유틸리티 함수들이 [num_windows][window_size] 형태를 기대하기 때문
            double[][] x_batch = new double[1][windowSize];
            double[][] y_batch = (numChannelsForSensor >= 2) ? new double[1][windowSize] : null;
            double[][] z_batch = (numChannelsForSensor >= 3) ? new double[1][windowSize] : null;

            for (int j = 0; j < windowSize; j++) {
                if (sensorInput3D[i][j] == null || sensorInput3D[i][j].length < numChannelsForSensor) {
                    System.err.printf(Locale.US, "PROC_IMU ERROR [%s] Win %d Sample %d: Insufficient channels or null sample.%n", featureSetPrefix, i, j);
                    // 이 윈도우 전체를 NaN으로 처리하고 다음 윈도우로
                    int len = calculateJerkConfig ? Math.max(0, windowSize - 1) : windowSize;
                    magnitudeSourceForFeatures[i] = new double[len]; Arrays.fill(magnitudeSourceForFeatures[i], Double.NaN);
                    Arrays.fill(xProcessedForAxisFeatures[i], Double.NaN);
                    if (yProcessedForAxisFeatures != null) Arrays.fill(yProcessedForAxisFeatures[i], Double.NaN);
                    if (zProcessedForAxisFeatures != null) Arrays.fill(zProcessedForAxisFeatures[i], Double.NaN);
                    x_batch = null; // 플래그 역할
                    break;
                }
                x_batch[0][j] = sensorInput3D[i][j][0];
                if (y_batch != null) y_batch[0][j] = sensorInput3D[i][j][1];
                if (z_batch != null) z_batch[0][j] = sensorInput3D[i][j][2];
            }
            if (x_batch == null) continue; // 현재 윈도우 처리 중단

            // 현재 윈도우의 회전 및 중력 데이터 (3D 배치 형태 [1][window_size][channels])
            double[][][] currentRotation3DBatch = null;
            if (rotationQuaternionData3D != null && i < rotationQuaternionData3D.length && rotationQuaternionData3D[i] != null) {
                if (rotationQuaternionData3D[i].length != windowSize || rotationQuaternionData3D[i][0].length != 4) {
                    System.err.printf(Locale.US, "PROC_IMU WARN [%s] Win %d: Rotation data dim mismatch. Expected [%d][4]. Got [%d][%d]. Using null.%n",
                            featureSetPrefix, i, windowSize, rotationQuaternionData3D[i].length, (rotationQuaternionData3D[i].length > 0 ? rotationQuaternionData3D[i][0].length : -1));
                } else { currentRotation3DBatch = new double[][][]{rotationQuaternionData3D[i]}; }
            }
            double[][][] currentGravity3DBatch = null;
            if (gravityVectorData3D != null && i < gravityVectorData3D.length && gravityVectorData3D[i] != null) {
                if (gravityVectorData3D[i].length != windowSize || gravityVectorData3D[i][0].length != 3) {
                    System.err.printf(Locale.US, "PROC_IMU WARN [%s] Win %d: Gravity data dim mismatch. Expected [%d][3]. Got [%d][%d]. Using null.%n",
                            featureSetPrefix, i, windowSize, gravityVectorData3D[i].length, (gravityVectorData3D[i].length > 0 ? gravityVectorData3D[i][0].length : -1));
                } else { currentGravity3DBatch = new double[][][]{gravityVectorData3D[i]}; }
            }

            double[][] currentMagnitudeSource2D; // 현재 윈도우의 [1][length] magnitudeSource

            // 축별 피처 계산을 위한 x,y,z (처리 후, 현재 윈도우에 대해 1D)
            // 이 값들은 xProcessedForAxisFeatures, yProcessedForAxisFeatures 등에 저장됨
            double[] x_processed_1d = x_batch[0];
            double[] y_processed_1d = (y_batch != null) ? y_batch[0] : null;
            double[] z_processed_1d = (z_batch != null) ? z_batch[0] : null;

            try {
                if (numChannelsForSensor == 1) {
                    currentMagnitudeSource2D = x_batch;
                } else if (numChannelsForSensor >= 3) {
                    if ("rotate".equals(safeProcessType)) {
                        if (currentRotation3DBatch == null) throw new IllegalArgumentException("Rotation data missing for 'rotate' for window " + i);
                        double[][][] rotated = IMUUtils.rotateAxis(
                                x_batch, y_batch, z_batch, currentRotation3DBatch
                        );
                        x_processed_1d = rotated[0][0]; // 회전된 x (1D)
                        y_processed_1d = rotated[1][0]; // 회전된 y (1D)
                        z_processed_1d = rotated[2][0]; // 회전된 z (1D)
                        currentMagnitudeSource2D = IMUFeatureExtractor.magnitude(
                                new double[][]{x_processed_1d}, // 2D로 다시 래핑
                                new double[][]{y_processed_1d},
                                new double[][]{z_processed_1d}
                        );
                    } else if ("horizontal".equals(safeProcessType) || "vertical".equals(safeProcessType)) {
                        if (currentGravity3DBatch == null) throw new IllegalArgumentException("Gravity data missing for 'horizontal'/'vertical' for window " + i);
                        double[][] theta_2d = IMUUtils.calculateAngle(x_batch, y_batch, z_batch, currentGravity3DBatch);
                        double[][] baseMag_2d = IMUFeatureExtractor.magnitude(x_batch, y_batch, z_batch);

                        currentMagnitudeSource2D = new double[1][windowSize];
                        for (int j = 0; j < windowSize; j++) {
                            currentMagnitudeSource2D[0][j] = baseMag_2d[0][j] * ("horizontal".equals(safeProcessType) ? Math.cos(theta_2d[0][j]) : Math.sin(theta_2d[0][j]));
                        }
                    } else { // process is null or other
                        currentMagnitudeSource2D = IMUFeatureExtractor.magnitude(x_batch, y_batch, z_batch);
                    }
                } else {
                    currentMagnitudeSource2D = x_batch;
                }

                if (calculateJerkConfig) {
                    if (currentMagnitudeSource2D.length == 0 || currentMagnitudeSource2D[0].length < 2) {
                        currentMagnitudeSource2D = new double[1][0]; // 빈 배열 [1][0]
                    } else {
                        Map<String, double[][]> jerkResult = IMUUtils.diff(currentMagnitudeSource2D);
                        currentMagnitudeSource2D = jerkResult.containsKey("difference") && jerkResult.get("difference") != null ?
                                jerkResult.get("difference") : new double[1][0];
                    }
                }
                magnitudeSourceForFeatures[i] = (currentMagnitudeSource2D != null && currentMagnitudeSource2D.length > 0) ? currentMagnitudeSource2D[0] : new double[0];


            } catch (Exception e) {
                System.err.printf(Locale.US, "PROC_IMU ERROR [%s] Win %d: Exception during magnitudeSource/jerk: %s. Filling with NaN.%n",
                        featureSetPrefix, i, e.getMessage());
                int len = calculateJerkConfig ? Math.max(0, windowSize - 1) : windowSize;
                magnitudeSourceForFeatures[i] = new double[len]; Arrays.fill(magnitudeSourceForFeatures[i], Double.NaN);
                Arrays.fill(x_processed_1d, Double.NaN);
                if(y_processed_1d != null) Arrays.fill(y_processed_1d, Double.NaN);
                if(z_processed_1d != null) Arrays.fill(z_processed_1d, Double.NaN);
            }

            // 처리된 축 데이터 저장
            System.arraycopy(x_processed_1d, 0, xProcessedForAxisFeatures[i], 0, Math.min(x_processed_1d.length, windowSize));
            if (y_processed_1d != null && yProcessedForAxisFeatures != null && yProcessedForAxisFeatures[i] != null) {
                System.arraycopy(y_processed_1d, 0, yProcessedForAxisFeatures[i], 0, Math.min(y_processed_1d.length, windowSize));
            }
            if (z_processed_1d != null && zProcessedForAxisFeatures != null && zProcessedForAxisFeatures[i] != null) {
                System.arraycopy(z_processed_1d, 0, zProcessedForAxisFeatures[i], 0, Math.min(z_processed_1d.length, windowSize));
            }

            // NaN 전파 최종 확인 (디버깅용)
            if (i > 0 && magnitudeSourceForFeatures[i] != null) { // 두 번째 윈도우부터
                boolean hasNaNInCurrentMag = false;
                for(double val : magnitudeSourceForFeatures[i]) if(Double.isNaN(val)) {hasNaNInCurrentMag = true; break;}
                if(hasNaNInCurrentMag){
                    Timber.tag(TAG).e("PROC_IMU NaN_FINAL_MAG_SOURCE [%s] Win %d: NaN detected in final magnitudeSourceForFeatures[%d]. Length: %d, First 5: %s%n",
                            featureSetPrefix, i, i, magnitudeSourceForFeatures[i].length, Arrays.toString(Arrays.copyOfRange(magnitudeSourceForFeatures[i], 0, Math.min(5, magnitudeSourceForFeatures[i].length))));
                }
            }
        }

        // 피처 추출
        final int constFs = 100;

        Map<String, double[][]> statM = IMUFeatureExtractor.calculateStatFeatures(magnitudeSourceForFeatures, featureSetPrefix + "M");
        Map<String, double[][]> combinedFeatures = new HashMap<>(statM);

        if (processEachAxisConfig && numChannelsForSensor > 1 && !calculateJerkConfig) {
            Map<String, double[][]> statX = IMUFeatureExtractor.calculateStatFeatures(xProcessedForAxisFeatures, featureSetPrefix + "X");
            combinedFeatures.putAll(statX);
            if (yProcessedForAxisFeatures != null){
                Map<String, double[][]> statY = IMUFeatureExtractor.calculateStatFeatures(yProcessedForAxisFeatures, featureSetPrefix + "Y");
                combinedFeatures.putAll(statY);
            }
            if (zProcessedForAxisFeatures != null){
                Map<String, double[][]> statZ = IMUFeatureExtractor.calculateStatFeatures(zProcessedForAxisFeatures, featureSetPrefix + "Z");
                combinedFeatures.putAll(statZ);
            }
        }

        String detrendTypeForWelch = IMUConfig.getDetrendTypeForWelch(featureSetPrefix);
        // System.out.println("PROC_IMU DEBUG [" + featureSetPrefix + "]: Using detrendType='" + detrendTypeForWelch + "' for Welch PSD.");

        Map<String, double[][]> spectralM = IMUFeatureExtractor.calculateSpectralFeatures(
                magnitudeSourceForFeatures,
                featureSetPrefix + "M",
                constFs,
                detrendTypeForWelch
        );
        combinedFeatures.putAll(spectralM);

        if (processEachAxisConfig && numChannelsForSensor > 1 && !calculateJerkConfig) {
            Map<String, double[][]> spectralX = IMUFeatureExtractor.calculateSpectralFeatures(xProcessedForAxisFeatures, featureSetPrefix + "X", constFs, detrendTypeForWelch);
            combinedFeatures.putAll(spectralX);
            if (yProcessedForAxisFeatures != null){
                Map<String, double[][]> spectralY = IMUFeatureExtractor.calculateSpectralFeatures(yProcessedForAxisFeatures, featureSetPrefix + "Y", constFs, detrendTypeForWelch);
                combinedFeatures.putAll(spectralY);
            }
            if (zProcessedForAxisFeatures != null){
                Map<String, double[][]> spectralZ = IMUFeatureExtractor.calculateSpectralFeatures(zProcessedForAxisFeatures, featureSetPrefix + "Z", constFs, detrendTypeForWelch);
                combinedFeatures.putAll(spectralZ);
            }
        }
        return combinedFeatures;
    }
}
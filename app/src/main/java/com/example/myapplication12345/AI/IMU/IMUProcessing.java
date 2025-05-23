package com.example.myapplication12345.AI.IMU;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class IMUProcessing {

    /**
     * IMU 센서 데이터를 처리하여 통계 및 스펙트럼 피처를 추출합니다.
     * 이 함수는 입력된 3D 센서 데이터의 모든 윈도우에 대해 피처를 계산합니다.
     */
    public static Map<String, double[][]> processingImu(
            double[][][] sensorInput3D,            // 대상 센서 데이터 [num_windows][window_size][num_channels]
            int numChannelsForSensor,              // sensorInput3D의 채널 수
            boolean statFeaturesEnabled,           // config에서 가져온 값
            boolean spectralFeaturesEnabled,       // config에서 가져온 값
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
        int windowSize = (numWindows > 0 && sensorInput3D[0] != null) ? sensorInput3D[0].length : 0;

        if (windowSize == 0) {
            System.err.printf(Locale.US, "PROC_IMU WARN [%s]: Input sensor data has zero window size.%n", featureSetPrefix);
            return new HashMap<>();
        }
        // 로깅 추가 (입력 차원 확인)
        // System.out.printf(Locale.US, "PROC_IMU INFO [%s]: Processing %d windows, windowSize=%d, numChannels=%d, process=%s, jerk=%b%n",
        //        featureSetPrefix, numWindows, windowSize, numChannelsForSensor, processTypeFromConfig, calculateJerkConfig);


        // 최종적으로 피처 계산에 사용될 1D 데이터들의 묶음 (각 윈도우에 대한 1D 데이터)
        // 형태: [numWindows][currentWindowSamples] (저크의 경우 currentWindowSamples = windowSize - 1)
        double[][] magnitudeSourceForFeatures = new double[numWindows][];

        // 각 축별 피처 계산에 사용될 (처리 후) 데이터
        // 형태: [numWindows][windowSize]
        double[][] xProcessedForAxisFeatures = new double[numWindows][windowSize];
        double[][] yProcessedForAxisFeatures = (numChannelsForSensor >= 2) ? new double[numWindows][windowSize] : null;
        double[][] zProcessedForAxisFeatures = (numChannelsForSensor >= 3) ? new double[numWindows][windowSize] : null;

        String safeProcessType = (processTypeFromConfig != null) ? processTypeFromConfig : "default";

        // --- 1. 각 윈도우에 대해 반복하며 magnitudeSource 및 처리된 축 데이터 계산 ---
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
            double[][] x_orig_win_batch = new double[1][windowSize];
            double[][] y_orig_win_batch = (numChannelsForSensor >= 2) ? new double[1][windowSize] : null;
            double[][] z_orig_win_batch = (numChannelsForSensor >= 3) ? new double[1][windowSize] : null;

            for (int j = 0; j < windowSize; j++) {
                if (sensorInput3D[i][j] == null || sensorInput3D[i][j].length < numChannelsForSensor) {
                    System.err.printf(Locale.US, "PROC_IMU ERROR [%s] Win %d Sample %d: Insufficient channels or null sample.%n", featureSetPrefix, i, j);
                    // 이 윈도우 전체를 NaN으로 처리하고 다음 윈도우로
                    int len = calculateJerkConfig ? Math.max(0, windowSize - 1) : windowSize;
                    magnitudeSourceForFeatures[i] = new double[len]; Arrays.fill(magnitudeSourceForFeatures[i], Double.NaN);
                    Arrays.fill(xProcessedForAxisFeatures[i], Double.NaN);
                    if (yProcessedForAxisFeatures != null) Arrays.fill(yProcessedForAxisFeatures[i], Double.NaN);
                    if (zProcessedForAxisFeatures != null) Arrays.fill(zProcessedForAxisFeatures[i], Double.NaN);
                    x_orig_win_batch = null; // 플래그 역할
                    break;
                }
                x_orig_win_batch[0][j] = sensorInput3D[i][j][0];
                if (y_orig_win_batch != null) y_orig_win_batch[0][j] = sensorInput3D[i][j][1];
                if (z_orig_win_batch != null) z_orig_win_batch[0][j] = sensorInput3D[i][j][2];
            }
            if (x_orig_win_batch == null) continue; // 현재 윈도우 처리 중단

            // 현재 윈도우의 회전 및 중력 데이터 (3D 배치 형태 [1][window_size][channels])
            double[][][] currentRotation3DBatch = null;
            if (rotationQuaternionData3D != null && i < rotationQuaternionData3D.length && rotationQuaternionData3D[i] != null) {
                if (rotationQuaternionData3D[i].length != windowSize || (windowSize > 0 && rotationQuaternionData3D[i][0].length != 4)) {
                    System.err.printf(Locale.US, "PROC_IMU WARN [%s] Win %d: Rotation data dim mismatch. Expected [%d][4]. Got [%d][%d]. Using null.%n",
                            featureSetPrefix, i, windowSize, rotationQuaternionData3D[i].length, (rotationQuaternionData3D[i].length > 0 ? rotationQuaternionData3D[i][0].length : -1));
                } else { currentRotation3DBatch = new double[][][]{rotationQuaternionData3D[i]}; }
            }
            double[][][] currentGravity3DBatch = null;
            if (gravityVectorData3D != null && i < gravityVectorData3D.length && gravityVectorData3D[i] != null) {
                if (gravityVectorData3D[i].length != windowSize || (windowSize > 0 && gravityVectorData3D[i][0].length != 3)) {
                    System.err.printf(Locale.US, "PROC_IMU WARN [%s] Win %d: Gravity data dim mismatch. Expected [%d][3]. Got [%d][%d]. Using null.%n",
                            featureSetPrefix, i, windowSize, gravityVectorData3D[i].length, (gravityVectorData3D[i].length > 0 ? gravityVectorData3D[i][0].length : -1));
                } else { currentGravity3DBatch = new double[][][]{gravityVectorData3D[i]}; }
            }

            double[][] currentMagnitudeSource2D; // 현재 윈도우의 [1][length] magnitudeSource

            // 축별 피처 계산을 위한 x,y,z (처리 후, 현재 윈도우에 대해 1D)
            // 이 값들은 xProcessedForAxisFeatures, yProcessedForAxisFeatures 등에 저장됨
            double[] x_processed_win_1d_temp = x_orig_win_batch[0];
            double[] y_processed_win_1d_temp = (y_orig_win_batch != null) ? y_orig_win_batch[0] : null;
            double[] z_processed_win_1d_temp = (z_orig_win_batch != null) ? z_orig_win_batch[0] : null;

            try {
                if (numChannelsForSensor == 1) {
                    currentMagnitudeSource2D = x_orig_win_batch;
                } else if (numChannelsForSensor >= 3) {
                    if ("rotate".equals(safeProcessType)) {
                        if (currentRotation3DBatch == null) throw new IllegalArgumentException("Rotation data missing for 'rotate' for window " + i);
                        double[][][] rotated = IMUUtils.rotateAxis(
                                x_orig_win_batch, y_orig_win_batch, z_orig_win_batch, currentRotation3DBatch
                        );
                        x_processed_win_1d_temp = rotated[0][0]; // 회전된 x (1D)
                        y_processed_win_1d_temp = rotated[1][0]; // 회전된 y (1D)
                        z_processed_win_1d_temp = rotated[2][0]; // 회전된 z (1D)
                        currentMagnitudeSource2D = IMUFeatureExtractor.magnitude(
                                new double[][]{x_processed_win_1d_temp}, // 2D로 다시 래핑
                                new double[][]{y_processed_win_1d_temp},
                                new double[][]{z_processed_win_1d_temp}
                        );
                    } else if ("horizontal".equals(safeProcessType) || "vertical".equals(safeProcessType)) {
                        if (currentGravity3DBatch == null) throw new IllegalArgumentException("Gravity data missing for 'horizontal'/'vertical' for window " + i);
                        double[][] theta_2d = IMUUtils.calculateAngle(x_orig_win_batch, y_orig_win_batch, z_orig_win_batch, currentGravity3DBatch);
                        double[][] baseMag_2d = IMUFeatureExtractor.magnitude(x_orig_win_batch, y_orig_win_batch, z_orig_win_batch);

                        currentMagnitudeSource2D = new double[1][windowSize];
                        for (int j = 0; j < windowSize; j++) {
                            currentMagnitudeSource2D[0][j] = baseMag_2d[0][j] * ("horizontal".equals(safeProcessType) ? Math.cos(theta_2d[0][j]) : Math.sin(theta_2d[0][j]));
                        }
                        // x_processed_win_1d_temp 등은 원본(회전 안 된) x_orig_win_1d 유지
                    } else { // process is null or other
                        currentMagnitudeSource2D = IMUFeatureExtractor.magnitude(x_orig_win_batch, y_orig_win_batch, z_orig_win_batch);
                        // x_processed_win_1d_temp 등은 원본 x_orig_win_1d 유지
                    }
                } else { // numChannelsForSensor == 2 등
                    currentMagnitudeSource2D = x_orig_win_batch;
                }

                if (calculateJerkConfig) {
                    if (currentMagnitudeSource2D == null || currentMagnitudeSource2D.length == 0 || currentMagnitudeSource2D[0].length < 2) {
                        currentMagnitudeSource2D = new double[1][0]; // 빈 배열 [1][0]
                    } else {
                        Map<String, double[][]> jerkResult = IMUUtils.diff(currentMagnitudeSource2D);
                        currentMagnitudeSource2D = (jerkResult != null && jerkResult.containsKey("difference") && jerkResult.get("difference") != null) ?
                                jerkResult.get("difference") : new double[1][0];
                    }
                }
                magnitudeSourceForFeatures[i] = (currentMagnitudeSource2D != null && currentMagnitudeSource2D.length > 0) ? currentMagnitudeSource2D[0] : new double[0];


            } catch (Exception e) {
                System.err.printf(Locale.US, "PROC_IMU ERROR [%s] Win %d: Exception during magnitudeSource/jerk: %s. Filling with NaN.%n",
                        featureSetPrefix, i, e.getMessage());
                e.printStackTrace(); // 상세 오류 확인
                int len = calculateJerkConfig ? Math.max(0, windowSize - 1) : windowSize;
                magnitudeSourceForFeatures[i] = new double[len]; Arrays.fill(magnitudeSourceForFeatures[i], Double.NaN);
                Arrays.fill(x_processed_win_1d_temp, Double.NaN);
                if(y_processed_win_1d_temp != null) Arrays.fill(y_processed_win_1d_temp, Double.NaN);
                if(z_processed_win_1d_temp != null) Arrays.fill(z_processed_win_1d_temp, Double.NaN);
            }

            // 처리된 축 데이터 저장
            System.arraycopy(x_processed_win_1d_temp, 0, xProcessedForAxisFeatures[i], 0, Math.min(x_processed_win_1d_temp.length, windowSize));
            if (y_processed_win_1d_temp != null && yProcessedForAxisFeatures != null && yProcessedForAxisFeatures[i] != null) {
                System.arraycopy(y_processed_win_1d_temp, 0, yProcessedForAxisFeatures[i], 0, Math.min(y_processed_win_1d_temp.length, windowSize));
            }
            if (z_processed_win_1d_temp != null && zProcessedForAxisFeatures != null && zProcessedForAxisFeatures[i] != null) {
                System.arraycopy(z_processed_win_1d_temp, 0, zProcessedForAxisFeatures[i], 0, Math.min(z_processed_win_1d_temp.length, windowSize));
            }

            // NaN 전파 최종 확인 (디버깅용)
            if (i > 0 && magnitudeSourceForFeatures[i] != null) { // 두 번째 윈도우부터
                boolean hasNaNInCurrentMag = false;
                for(double val : magnitudeSourceForFeatures[i]) if(Double.isNaN(val)) {hasNaNInCurrentMag = true; break;}
                if(hasNaNInCurrentMag){
                    System.err.printf(Locale.US, "PROC_IMU NaN_FINAL_MAG_SOURCE [%s] Win %d: NaN detected in final magnitudeSourceForFeatures[%d]. Length: %d, First 5: %s%n",
                            featureSetPrefix, i, i, magnitudeSourceForFeatures[i].length, Arrays.toString(Arrays.copyOfRange(magnitudeSourceForFeatures[i], 0, Math.min(5, magnitudeSourceForFeatures[i].length))));
                }
            }
        } // End of window loop

        // --- 2. 피처 추출 (모든 윈도우에 대해 일괄 처리) ---
        Map<String, double[][]> combinedFeatures = new HashMap<>();
        final int constFs = IMUConfig.SAMPLING_FREQUENCY;

        if (statFeaturesEnabled) {
            // calculateStatFeatures는 double[numWindows][samplesPerWindow]를 받음
            Map<String, double[][]> statM = IMUFeatureExtractor.calculateStatFeatures(magnitudeSourceForFeatures, featureSetPrefix + "M");
            if (statM != null) combinedFeatures.putAll(statM);

            if (processEachAxisConfig && numChannelsForSensor > 1 && !calculateJerkConfig) {
                Map<String, double[][]> statX = IMUFeatureExtractor.calculateStatFeatures(xProcessedForAxisFeatures, featureSetPrefix + "X");
                if (statX != null) combinedFeatures.putAll(statX);
                if (yProcessedForAxisFeatures != null){
                    Map<String, double[][]> statY = IMUFeatureExtractor.calculateStatFeatures(yProcessedForAxisFeatures, featureSetPrefix + "Y");
                    if (statY != null) combinedFeatures.putAll(statY);
                }
                if (zProcessedForAxisFeatures != null){
                    Map<String, double[][]> statZ = IMUFeatureExtractor.calculateStatFeatures(zProcessedForAxisFeatures, featureSetPrefix + "Z");
                    if (statZ != null) combinedFeatures.putAll(statZ);
                }
            }
        }

        if (spectralFeaturesEnabled) {
            String detrendTypeForWelch = IMUConfig.getDetrendTypeForWelch(featureSetPrefix);
            // System.out.println("PROC_IMU DEBUG [" + featureSetPrefix + "]: Using detrendType='" + detrendTypeForWelch + "' for Welch PSD.");

            Map<String, double[][]> spectralM = IMUFeatureExtractor.calculateSpectralFeatures(
                    magnitudeSourceForFeatures, // [numWindows][samples_in_window]
                    featureSetPrefix + "M",
                    constFs,
                    detrendTypeForWelch
            );
            if (spectralM != null) combinedFeatures.putAll(spectralM);

            if (processEachAxisConfig && numChannelsForSensor > 1 && !calculateJerkConfig) {
                Map<String, double[][]> spectralX = IMUFeatureExtractor.calculateSpectralFeatures(xProcessedForAxisFeatures, featureSetPrefix + "X", constFs, detrendTypeForWelch);
                if (spectralX != null) combinedFeatures.putAll(spectralX);
                if (yProcessedForAxisFeatures != null){
                    Map<String, double[][]> spectralY = IMUFeatureExtractor.calculateSpectralFeatures(yProcessedForAxisFeatures, featureSetPrefix + "Y", constFs, detrendTypeForWelch);
                    if (spectralY != null) combinedFeatures.putAll(spectralY);
                }
                if (zProcessedForAxisFeatures != null){
                    Map<String, double[][]> spectralZ = IMUFeatureExtractor.calculateSpectralFeatures(zProcessedForAxisFeatures, featureSetPrefix + "Z", constFs, detrendTypeForWelch);
                    if (spectralZ != null) combinedFeatures.putAll(spectralZ);
                }
            }
        }
        return combinedFeatures;
    }

    public static double[] getMagnitudeSourceForTesting(
            double[][] sensorDataWin, // 단일 윈도우 원본 센서 데이터 (WINDOW_SIZE x numChannels)
            int numChannels,
            String processType,
            boolean calculateJerk,
            double[][] rotationDataWin, // 단일 윈도우 회전 데이터 (WINDOW_SIZE x 4)
            double[][] gravityDataWin   // 단일 윈도우 중력 데이터 (WINDOW_SIZE x 3)
    ) {
        if (sensorDataWin == null || sensorDataWin.length == 0) {
            System.err.println("Error: sensorDataWin is null or empty for getMagnitudeSourceForTesting.");
            return new double[0];
        }
        final int currentWindowSize = sensorDataWin.length;

        // 입력 2D 윈도우 데이터를 -> 2D 배치 형태 ([1][window_size])로 변환
        double[][] x_orig_batch = new double[1][currentWindowSize];
        double[][] y_orig_batch = (numChannels >= 2) ? new double[1][currentWindowSize] : null;
        double[][] z_orig_batch = (numChannels >= 3) ? new double[1][currentWindowSize] : null;

        for (int j = 0; j < currentWindowSize; j++) {
            if (sensorDataWin[j] == null || sensorDataWin[j].length < numChannels) {
                throw new IllegalArgumentException("Insufficient channels or null sample in sensorDataWin at sample " + j);
            }
            x_orig_batch[0][j] = sensorDataWin[j][0];
            if (y_orig_batch != null) y_orig_batch[0][j] = sensorDataWin[j][1];
            if (z_orig_batch != null) z_orig_batch[0][j] = sensorDataWin[j][2];
        }

        // 회전 및 중력 데이터도 3D 배치 형태로 ([1][window_size][channels])
        double[][][] rotation3D_batch = (rotationDataWin != null) ? new double[][][]{rotationDataWin} : null;
        if (rotationDataWin != null && (rotation3D_batch == null || rotationDataWin[0].length != currentWindowSize || rotationDataWin[0].length != 4)){
            throw new IllegalArgumentException("Rotation data has incorrect dimensions. Expected: [1]["+currentWindowSize+"][4]");
        }
        double[][][] gravity3D_batch = (gravityDataWin != null) ? new double[][][]{gravityDataWin} : null;
        if (gravityDataWin != null && (gravity3D_batch == null || gravityDataWin[0].length != currentWindowSize || gravityDataWin[0].length != 3)){
            throw new IllegalArgumentException("Gravity data has incorrect dimensions. Expected: [1]["+currentWindowSize+"][3]");
        }

        double[][] magnitudeSource_2d; // 최종적으로 [1][length] 형태
        String safeProcessType = processType != null ? processType : "default";

        if (numChannels == 1) {
            magnitudeSource_2d = x_orig_batch;
        } else if (numChannels >= 3) {
            double[][] x_processed_batch = x_orig_batch;
            double[][] y_processed_batch = y_orig_batch;
            double[][] z_processed_batch = z_orig_batch;

            if ("rotate".equals(safeProcessType)) {
                if (rotation3D_batch == null) throw new IllegalArgumentException("Rotation data required for 'rotate'");
                double[][][] rotatedAxesResult3D = IMUUtils.rotateAxis(
                        x_orig_batch, y_orig_batch, z_orig_batch, rotation3D_batch
                );
                x_processed_batch = rotatedAxesResult3D[0]; // [1][currentWindowSize]
                y_processed_batch = rotatedAxesResult3D[1];
                z_processed_batch = rotatedAxesResult3D[2];
                magnitudeSource_2d = IMUFeatureExtractor.magnitude(x_processed_batch, y_processed_batch, z_processed_batch);
            } else if ("horizontal".equals(safeProcessType) || "vertical".equals(safeProcessType)) {
                if (gravity3D_batch == null) throw new IllegalArgumentException("Gravity data required");
                double[][] theta_2d = IMUUtils.calculateAngle(x_orig_batch, y_orig_batch, z_orig_batch, gravity3D_batch);
                double[][] baseMag_2d = IMUFeatureExtractor.magnitude(x_orig_batch, y_orig_batch, z_orig_batch);
                magnitudeSource_2d = new double[1][currentWindowSize];
                for (int j = 0; j < currentWindowSize; j++) {
                    magnitudeSource_2d[0][j] = baseMag_2d[0][j] * ("horizontal".equals(safeProcessType) ? Math.cos(theta_2d[0][j]) : Math.sin(theta_2d[0][j]));
                }
            } else { // process is null or other
                magnitudeSource_2d = IMUFeatureExtractor.magnitude(x_orig_batch, y_orig_batch, z_orig_batch);
            }
        } else {
            magnitudeSource_2d = x_orig_batch;
        }

        if (calculateJerk) {
            if (magnitudeSource_2d == null || magnitudeSource_2d.length == 0 || magnitudeSource_2d[0].length < 2) return new double[0];
            Map<String, double[][]> jerkResult = IMUUtils.diff(magnitudeSource_2d);
            if (jerkResult != null && jerkResult.containsKey("difference") && jerkResult.get("difference") != null && jerkResult.get("difference").length > 0) {
                magnitudeSource_2d = jerkResult.get("difference");
            } else return new double[0];
        }
        return (magnitudeSource_2d != null && magnitudeSource_2d.length > 0 && magnitudeSource_2d[0] != null) ? magnitudeSource_2d[0] : new double[0];
    }
}
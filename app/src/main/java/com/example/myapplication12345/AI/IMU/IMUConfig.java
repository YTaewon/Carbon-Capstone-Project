package com.example.myapplication12345.AI.IMU;

import java.util.HashMap;
import java.util.Map;

public class IMUConfig {
    private static final Map<String, Integer> SENSOR_CHANNELS = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_PROCESS_TYPE = new HashMap<>();
    private static final Map<String, Boolean> FEATURE_SET_PROCESS_EACH_AXIS = new HashMap<>();
    // statFeatures와 spectralFeatures는 config.ini에서 항상 True로 설정되므로 Map에서 제거하고 getter에서 직접 true 반환
    private static final Map<String, Boolean> FEATURE_SET_CALCULATE_JERK = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_USING_SENSOR_DATA = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_DETREND_TYPE_FOR_WELCH = new HashMap<>();

    public static final int SAMPLING_FREQUENCY = 100; // 단위: Hz

    // Welch Detrend 타입 문자열: "linear", "mean" (또는 "constant"), "none" (또는 null)
    // Python scipy.signal.welch's default detrend is 'constant' (mean removal)
    // when nperseg is the full segment length, as used in the Python script.
    static {
        // [SENSORS]
        SENSOR_CHANNELS.put("gyro", 3);
        SENSOR_CHANNELS.put("accel", 3);
        SENSOR_CHANNELS.put("mag", 3);
        SENSOR_CHANNELS.put("gravity", 3);
        SENSOR_CHANNELS.put("linear_accel", 3);
        SENSOR_CHANNELS.put("rot", 4);
        SENSOR_CHANNELS.put("pressure", 1);

        // [IMU] 피처셋 설정
        // setFeatureSetConfig(featureSetPrefix, usingSensorData, processEachAxis, processType, calculateJerk, detrendTypeWelch)

        // CORRECTED: All detrend types for Welch should be "mean" to match Python's scipy.signal.welch default
        // when nperseg is the full segment length and no specific detrend is passed to welch.
        setFeatureSetConfig("accel", "accel", true, "rotate", false, "mean");
        setFeatureSetConfig("linear_accel", "linear_accel", true, "rotate", false, "mean");
        setFeatureSetConfig("gyro", "gyro", true, null, false, "mean");
        setFeatureSetConfig("mag", "mag", true, null, false, "mean");

        setFeatureSetConfig("accel_h", "linear_accel", true, "horizontal", false, "mean");
        setFeatureSetConfig("accel_v", "linear_accel", true, "vertical", false, "mean");

        setFeatureSetConfig("jerk_h", "linear_accel", true, "horizontal", true, "mean");
        setFeatureSetConfig("jerk_v", "linear_accel", true, "vertical", true, "mean");

        setFeatureSetConfig("gravity", "gravity", true, null, false, "mean");
        setFeatureSetConfig("pressure", "pressure", false, null, false, "mean"); // Pressure also uses Welch default
    }

    private static void setFeatureSetConfig(String featureSetPrefix, String usingSensorData,
                                            boolean processEachAxis, String processType, boolean calculateJerk,
                                            String detrendTypeWelch) {
        FEATURE_SET_USING_SENSOR_DATA.put(featureSetPrefix, usingSensorData);
        FEATURE_SET_PROCESS_EACH_AXIS.put(featureSetPrefix, processEachAxis);
        FEATURE_SET_PROCESS_TYPE.put(featureSetPrefix, processType);
        FEATURE_SET_CALCULATE_JERK.put(featureSetPrefix, calculateJerk);
        FEATURE_SET_DETREND_TYPE_FOR_WELCH.put(featureSetPrefix, detrendTypeWelch);
    }

    public static int getSensorChannels(String sensorName) {
        return SENSOR_CHANNELS.getOrDefault(sensorName, 0);
    }

    public static boolean isProcessEachAxis(String featureSetPrefix) {
        return FEATURE_SET_PROCESS_EACH_AXIS.getOrDefault(featureSetPrefix, false);
    }

    public static boolean isStatFeaturesEnabled(String featureSetPrefix) {
        return true;
    }

    public static boolean isSpectralFeaturesEnabled(String featureSetPrefix) {
        return true;
    }

    public static boolean isCalculateJerkEnabled(String featureSetPrefix) {
        return FEATURE_SET_CALCULATE_JERK.getOrDefault(featureSetPrefix, false);
    }

    public static String getProcessType(String featureSetPrefix) {
        return FEATURE_SET_PROCESS_TYPE.get(featureSetPrefix);
    }

    public static String getUsingSensorData(String featureSetPrefix) {
        return FEATURE_SET_USING_SENSOR_DATA.get(featureSetPrefix);
    }

    public static String getDetrendTypeForWelch(String featureSetPrefix) {
        // Default to "mean" as per Scipy's Welch default for the Python script's usage pattern
        return FEATURE_SET_DETREND_TYPE_FOR_WELCH.getOrDefault(featureSetPrefix, "mean");
    }
}
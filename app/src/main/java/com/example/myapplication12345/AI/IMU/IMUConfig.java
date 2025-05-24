package com.example.myapplication12345.AI.IMU;

import java.util.HashMap;
import java.util.Map;

public class IMUConfig {
    private static final Map<String, Integer> SENSOR_CHANNELS = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_PROCESS_TYPE = new HashMap<>();
    private static final Map<String, Boolean> FEATURE_SET_PROCESS_EACH_AXIS = new HashMap<>();
    private static final Map<String, Boolean> FEATURE_SET_CALCULATE_JERK = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_USING_SENSOR_DATA = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_DETREND_TYPE_FOR_WELCH = new HashMap<>();

    public static final int SAMPLING_FREQUENCY = 100; // 단위: Hz

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
        setFeatureSetConfig("accel", "accel", true, "rotate", false);
        setFeatureSetConfig("linear_accel", "linear_accel", true, "rotate", false);
        setFeatureSetConfig("gyro", "gyro", true, null, false);
        setFeatureSetConfig("mag", "mag", true, null, false);

        setFeatureSetConfig("accel_h", "linear_accel", true, "horizontal", false);
        setFeatureSetConfig("accel_v", "linear_accel", true, "vertical", false);

        setFeatureSetConfig("jerk_h", "linear_accel", true, "horizontal", true);
        setFeatureSetConfig("jerk_v", "linear_accel", true, "vertical", true);

        setFeatureSetConfig("gravity", "gravity", true, null, false);
        setFeatureSetConfig("pressure", "pressure", false, null, false); // Pressure also uses Welch default
    }

    private static void setFeatureSetConfig(String featureSetPrefix, String usingSensorData,
                                            boolean processEachAxis, String processType, boolean calculateJerk) {
        FEATURE_SET_USING_SENSOR_DATA.put(featureSetPrefix, usingSensorData);
        FEATURE_SET_PROCESS_EACH_AXIS.put(featureSetPrefix, processEachAxis);
        FEATURE_SET_PROCESS_TYPE.put(featureSetPrefix, processType);
        FEATURE_SET_CALCULATE_JERK.put(featureSetPrefix, calculateJerk);
        FEATURE_SET_DETREND_TYPE_FOR_WELCH.put(featureSetPrefix, "mean");
    }

    public static int getSensorChannels(String sensorName) {
        return SENSOR_CHANNELS.getOrDefault(sensorName, 0);
    }

    public static boolean isProcessEachAxis(String featureSetPrefix) {
        return Boolean.TRUE.equals(FEATURE_SET_PROCESS_EACH_AXIS.getOrDefault(featureSetPrefix, false));
    }

    public static boolean isStatFeaturesEnabled(String featureSetPrefix) {
        return true;
    }

    public static boolean isSpectralFeaturesEnabled(String featureSetPrefix) {
        return true;
    }

    public static boolean isCalculateJerkEnabled(String featureSetPrefix) {
        return Boolean.TRUE.equals(FEATURE_SET_CALCULATE_JERK.getOrDefault(featureSetPrefix, false));
    }

    public static String getProcessType(String featureSetPrefix) {
        return FEATURE_SET_PROCESS_TYPE.get(featureSetPrefix);
    }

    public static String getUsingSensorData(String featureSetPrefix) {
        return FEATURE_SET_USING_SENSOR_DATA.get(featureSetPrefix);
    }

    public static String getDetrendTypeForWelch(String featureSetPrefix) {
        return FEATURE_SET_DETREND_TYPE_FOR_WELCH.getOrDefault(featureSetPrefix, "mean");
    }
}
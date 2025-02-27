package com.example.myapplication12345.AI.IMU;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IMUConfig {
    private static final Map<String, Integer> SENSOR_CHANNELS = new HashMap<>();
    private static final Map<String, Boolean> SENSOR_ENABLED = new HashMap<>();
    private static final Map<String, String> SENSOR_PROCESS_TYPE = new HashMap<>();
    private static final Map<String, Boolean> SENSOR_PROCESS_EACH_AXIS = new HashMap<>();
    private static final Map<String, Boolean> SENSOR_STAT_FEATURES = new HashMap<>();
    private static final Map<String, Boolean> SENSOR_SPECTRAL_FEATURES = new HashMap<>();
    private static final Map<String, Boolean> SENSOR_CALCULATE_JERK = new HashMap<>();
    private static final Map<String, String> SENSOR_USING_DATA = new HashMap<>(); 

    static {
        // ✅ 센서 채널 개수 설정
        SENSOR_CHANNELS.put("gyro", 3);
        SENSOR_CHANNELS.put("accel", 3);
        SENSOR_CHANNELS.put("mag", 3);
        SENSOR_CHANNELS.put("gravity", 3);
        SENSOR_CHANNELS.put("linear_accel", 3);
        SENSOR_CHANNELS.put("rot", 4);
        SENSOR_CHANNELS.put("pressure", 1);

        // ✅ IMU 센서 활성화 설정
        List<String> enabledSensors = Arrays.asList("gyro", "accel", "linear_accel", "accel_h", "accel_v", "jerk_h",
                "jerk_v", "mag", "gravity", "pressure");
        for (String sensor : enabledSensors) {
            SENSOR_ENABLED.put(sensor, true);
        }

        // ✅ 센서별 특징 추출 및 처리 방식 설정
        setSensorConfig(
            "gyro",
            "gyro", 
            true,
                null);
        setSensorConfig(
            "accel",
            "accel",
            true,
                "rotate");
        setSensorConfig(
            "linear_accel",
            "linear_accel", 
            false,
                "rotate");
        setSensorConfig(
            "accel_h",
            "linear_accel",
            false,
                "horizontal");
        setSensorConfig(
            "accel_v",
            "linear_accel", 
            false,
                "vertical");
        setSensorConfig(
            "jerk_h",
            "linear_accel", 
            false,
                "horizontal",
            true);
        setSensorConfig(
            "jerk_v",
            "linear_accel", 
            false,
                "vertical",
            true);
        setSensorConfig(
            "mag",
            "mag", 
            true,
                null);
        setSensorConfig(
            "gravity",
            "gravity", 
            true,
                null);
        setSensorConfig(
            "pressure",
            "pressure", 
            false,
                null);
    }

    private static void setSensorConfig(String sensor, String usingsensordata, boolean processEachAxis, String processType) {
        setSensorConfig(sensor,usingsensordata, processEachAxis, processType, false);
    }

    private static void setSensorConfig(String sensor, String usingsensordata, boolean processEachAxis, String processType, boolean calculateJerk) {
        SENSOR_USING_DATA.put(sensor, usingsensordata);
        SENSOR_PROCESS_EACH_AXIS.put(sensor, processEachAxis);
        SENSOR_STAT_FEATURES.put(sensor, true);
        SENSOR_SPECTRAL_FEATURES.put(sensor, true);
        SENSOR_PROCESS_TYPE.put(sensor, processType);
        SENSOR_CALCULATE_JERK.put(sensor, calculateJerk);
    }

    public static int getSensorChannels(String sensor) {
        return SENSOR_CHANNELS.getOrDefault(sensor, 0);
    }

    public static boolean isProcessEachAxis(String sensor) {
        return SENSOR_PROCESS_EACH_AXIS.getOrDefault(sensor, false);
    }

    public static boolean isStatFeaturesEnabled(String sensor) {
        return SENSOR_STAT_FEATURES.getOrDefault(sensor, false);
    }

    public static boolean isSpectralFeaturesEnabled(String sensor) {
        return SENSOR_SPECTRAL_FEATURES.getOrDefault(sensor, false);
    }

    public static boolean isCalculateJerkEnabled(String sensor) {
        return SENSOR_CALCULATE_JERK.getOrDefault(sensor, false);
    }

    public static String getProcessType(String sensor) {
        return SENSOR_PROCESS_TYPE.getOrDefault(sensor, null);
    }

    public static String getUsingSensorData(String sensor) {
        return SENSOR_USING_DATA.getOrDefault(sensor, null);
    }
}

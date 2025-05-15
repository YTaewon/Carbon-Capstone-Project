package com.example.myapplication12345.AI.IMU;

import java.util.HashMap;
import java.util.Map;

public class IMUConfig {
    // SENSOR_CHANNELS는 config.ini의 [SENSORS] 섹션과 동일하게 유지
    private static final Map<String, Integer> SENSOR_CHANNELS = new HashMap<>();

    // 각 "최종 피처셋 프리픽스"에 대한 설정을 저장
    private static final Map<String, String> FEATURE_SET_PROCESS_TYPE = new HashMap<>();
    private static final Map<String, Boolean> FEATURE_SET_PROCESS_EACH_AXIS = new HashMap<>();
    private static final Map<String, Boolean> FEATURE_SET_STAT_FEATURES = new HashMap<>(); // 기본적으로 True
    private static final Map<String, Boolean> FEATURE_SET_SPECTRAL_FEATURES = new HashMap<>(); // 기본적으로 True
    private static final Map<String, Boolean> FEATURE_SET_CALCULATE_JERK = new HashMap<>();
    private static final Map<String, String> FEATURE_SET_USING_SENSOR_DATA = new HashMap<>();

    static {
        // [SENSORS] 섹션 - 센서별 채널 수
        SENSOR_CHANNELS.put("gyro", 3);
        SENSOR_CHANNELS.put("accel", 3);
        SENSOR_CHANNELS.put("mag", 3);
        SENSOR_CHANNELS.put("gravity", 3);
        SENSOR_CHANNELS.put("linear_accel", 3);
        SENSOR_CHANNELS.put("rot", 4);
        SENSOR_CHANNELS.put("pressure", 1);

        // [IMU] 하위 섹션들 (최종 피처셋 설정)
        // setFeatureSetConfig(피처셋_프리픽스, 사용할_원본센서_이름, 각축별계산여부, 처리타입, 저크계산여부)

        setFeatureSetConfig("gyro", "gyro", true, null, false);
        setFeatureSetConfig("accel", "accel", true, "rotate", false);
        setFeatureSetConfig("linear_accel", "linear_accel", true, "rotate", false); // config.ini 오타 수정 가정 (rocessEachAxis -> process_each_axis)
        setFeatureSetConfig("accel_h", "linear_accel", true, "horizontal", false); // config.ini 오타 수정 가정
        setFeatureSetConfig("accel_v", "linear_accel", true, "vertical", false);   // config.ini 오타 수정 가정
        setFeatureSetConfig("jerk_h", "linear_accel", true, "horizontal", true);  // config.ini 오타 수정 가정
        setFeatureSetConfig("jerk_v", "linear_accel", true, "vertical", true);    // config.ini 오타 수정 가정
        setFeatureSetConfig("mag", "mag", true, null, false); // process가 None이면 null 전달
        setFeatureSetConfig("gravity", "gravity", true, null, false);
        setFeatureSetConfig("pressure", "pressure", false, null, false); // process_each_axis = False
    }

    private static void setFeatureSetConfig(String featureSetPrefix, String usingSensorData,
                                            boolean processEachAxis, String processType, boolean calculateJerk) {
        FEATURE_SET_USING_SENSOR_DATA.put(featureSetPrefix, usingSensorData);
        FEATURE_SET_PROCESS_EACH_AXIS.put(featureSetPrefix, processEachAxis);
        FEATURE_SET_STAT_FEATURES.put(featureSetPrefix, true); // 모든 피처셋에 대해 True로 고정 (config.ini에 따름)
        FEATURE_SET_SPECTRAL_FEATURES.put(featureSetPrefix, true); // 모든 피처셋에 대해 True로 고정
        FEATURE_SET_PROCESS_TYPE.put(featureSetPrefix, processType);
        FEATURE_SET_CALCULATE_JERK.put(featureSetPrefix, calculateJerk);
    }

    // 원본 센서의 채널 수를 가져옴
    public static int getSensorChannels(String SensorName) {
        return SENSOR_CHANNELS.getOrDefault(SensorName, 0);
    }

    // 특정 피처셋을 계산할 때 각 축별로도 피처를 계산할지 여부
    public static boolean isProcessEachAxis(String featureSetPrefix) {
        return FEATURE_SET_PROCESS_EACH_AXIS.getOrDefault(featureSetPrefix, false); // 기본값 false
    }

    // 특정 피처셋에 대해 통계 피처를 계산할지 여부
    public static boolean isStatFeaturesEnabled(String featureSetPrefix) {
        return FEATURE_SET_STAT_FEATURES.getOrDefault(featureSetPrefix, false); // 기본값 false
    }

    // 특정 피처셋에 대해 스펙트럼 피처를 계산할지 여부
    public static boolean isSpectralFeaturesEnabled(String featureSetPrefix) {
        return FEATURE_SET_SPECTRAL_FEATURES.getOrDefault(featureSetPrefix, false); // 기본값 false
    }

    // 특정 피처셋에 대해 저크를 계산할지 여부
    public static boolean isCalculateJerkEnabled(String featureSetPrefix) {
        return FEATURE_SET_CALCULATE_JERK.getOrDefault(featureSetPrefix, false); // 기본값 false
    }

    // 특정 피처셋 계산 시 어떤 처리 방식을 사용할지
    public static String getProcessType(String featureSetPrefix) {
        return FEATURE_SET_PROCESS_TYPE.get(featureSetPrefix); // 키가 없으면 null 반환
    }

    // 특정 피처셋 계산 시 사용할 원본 센서 데이터의 이름
    public static String getUsingSensorData(String featureSetPrefix) {
        return FEATURE_SET_USING_SENSOR_DATA.get(featureSetPrefix); // 키가 없으면 null 반환
    }
}
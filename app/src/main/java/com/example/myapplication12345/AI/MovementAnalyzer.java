package com.example.myapplication12345.AI;

import java.util.List;
import java.util.Map;
import timber.log.Timber;

public class MovementAnalyzer {
    private final List<Map<String, Object>> gpsData;
    private final List<Map<String, Object>> imuData;

    private float finalDistance;

    // 칼만 필터를 위한 변수 (IMU 보정용)
    private float velocityEstimate = 0.0f;
    private float velocityVariance = 1.0f;
    private static final float PROCESS_NOISE = 0.1f;
    private static final float MEASUREMENT_NOISE = 0.5f;

    // 비정상 거리 기준 상수
    private static final float MAX_REALISTIC_SPEED = 50.0f; // 50 m/s ≈ 180 km/h
    private static final float MIN_DISTANCE_THRESHOLD = 1.0f; // 최소 거리 임계값 (1m)

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData;
        this.imuData = imuData;
        this.finalDistance = 0.0f;
    }

    // Haversine 공식으로 GPS 거리 계산
    private float haversine(float lat1, float lon1, float lat2, float lon2) {
        final float R = 6371000f; // 지구 반지름 (미터)
        float dLat = (float) Math.toRadians(lat2 - lat1);
        float dLon = (float) Math.toRadians(lon2 - lon1);

        float a = (float) (Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2));

        float c = (float) (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
        return R * c;
    }

    // GPS 데이터 기반 거리 계산
    public float calculateGPSDistance() {
        float totalDistance = 0.0f;
        if (gpsData == null || gpsData.size() < 2) return totalDistance;

        for (int i = 1; i < gpsData.size(); i++) {
            Map<String, Object> prev = gpsData.get(i - 1);
            Map<String, Object> curr = gpsData.get(i);

            Number lat1Num = (Number) prev.get("latitude");
            Number lon1Num = (Number) prev.get("longitude");
            Number lat2Num = (Number) curr.get("latitude");
            Number lon2Num = (Number) curr.get("longitude");
            Number accNum = (Number) curr.get("accuracy");

            float lat1 = lat1Num != null ? lat1Num.floatValue() : 0.0f;
            float lon1 = lon1Num != null ? lon1Num.floatValue() : 0.0f;
            float lat2 = lat2Num != null ? lat2Num.floatValue() : 0.0f;
            float lon2 = lon2Num != null ? lon2Num.floatValue() : 0.0f;
            float accuracy = accNum != null ? accNum.floatValue() : Float.MAX_VALUE;

            float distance = haversine(lat1, lon1, lat2, lon2);
            if (accuracy < 20.0f && distance > MIN_DISTANCE_THRESHOLD) {
                totalDistance += distance;
            }
        }
        return totalDistance;
    }

    // IMU 데이터를 활용한 거리 추정 (칼만 필터 적용)
    public float calculateIMUDistance() {
        float totalDistance = 0.0f;
        long prevTime = 0;

        if (imuData == null || imuData.isEmpty()) return totalDistance;

        for (int i = 0; i < imuData.size(); i++) {
            Map<String, Object> curr = imuData.get(i);
            long timestamp = (long) curr.get("timestamp");

            Number accXNum = (Number) curr.get("accel.x");
            Number accYNum = (Number) curr.get("accel.y");
            Number accZNum = (Number) curr.get("accel.z");

            float accX = accXNum != null ? accXNum.floatValue() : 0.0f;
            float accY = accYNum != null ? accYNum.floatValue() : 0.0f;
            float accZ = accZNum != null ? accZNum.floatValue() : 0.0f;

            float rawAcceleration = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ) - 9.81f;
            if (i > 0) {
                float deltaTime = (timestamp - prevTime) / 1000.0f;

                velocityEstimate += rawAcceleration * deltaTime;
                velocityVariance += PROCESS_NOISE;

                float kalmanGain = velocityVariance / (velocityVariance + MEASUREMENT_NOISE);
                velocityEstimate = velocityEstimate + kalmanGain * (0.0f - velocityEstimate);
                velocityVariance = (1 - kalmanGain) * velocityVariance;

                if (velocityEstimate < 0) velocityEstimate = 0;
                totalDistance += velocityEstimate * deltaTime;
            }
            prevTime = timestamp;
        }
        return totalDistance;
    }

    // 분석 메서드 (GPS와 IMU 융합으로 거리만 계산)
    public void analyze() {
        float gpsDistance = calculateGPSDistance();
        float imuDistance = calculateIMUDistance();

        // GPS와 IMU 데이터를 가중치로 융합
        float gpsWeight = gpsDistance > 0 ? 0.8f : 0.0f;
        float imuWeight = 1.0f - gpsWeight;
        this.finalDistance = gpsDistance * gpsWeight + imuDistance * imuWeight;

        // 시간 계산 (비정상 거리 검증용)
        long startTime = gpsData.isEmpty() ? (imuData.isEmpty() ? 0 : (long) imuData.get(0).get("timestamp"))
                : (long) gpsData.get(0).get("timestamp");
        long endTime = gpsData.isEmpty() ? (imuData.isEmpty() ? 0 : (long) imuData.get(imuData.size() - 1).get("timestamp"))
                : (long) gpsData.get(gpsData.size() - 1).get("timestamp");
        float totalTime = (endTime - startTime) / 1000.0f;

        // 비정상 거리 검증
        if (this.finalDistance < 0) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 음수 거리 %s", this.finalDistance);
            this.finalDistance = 0.0f;
        } else if (this.finalDistance < MIN_DISTANCE_THRESHOLD && totalTime > 0) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 너무 작은 거리 " + this.finalDistance + " (시간: " + totalTime + "s)");
            this.finalDistance = 0.0f;
        } else if (totalTime > 0 && (this.finalDistance / totalTime) > MAX_REALISTIC_SPEED) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 비현실적인 속도 " + (this.finalDistance / totalTime) + " m/s");
            this.finalDistance = 0.0f;
        }

        Timber.tag("MovementAnalyzer").d("GPS Distance: " + gpsDistance + " meters");
        Timber.tag("MovementAnalyzer").d("IMU Distance: " + imuDistance + " meters");
        Timber.tag("MovementAnalyzer").d("Final Distance: " + finalDistance + " meters");
    }

    // Getter 메서드
    public float getDistance() {
        return this.finalDistance;
    }
}
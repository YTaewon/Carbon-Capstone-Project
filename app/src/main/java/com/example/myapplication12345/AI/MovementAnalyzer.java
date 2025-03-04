package com.example.myapplication12345.AI;

import android.util.Log;

import java.util.List;
import java.util.Map;

public class MovementAnalyzer {
    private List<Map<String, Object>> gpsData;
    private List<Map<String, Object>> imuData;

    private float finalDistance;
    private String transportMode;

    // 칼만 필터를 위한 간단한 변수 (IMU 보정용)
    private float velocityEstimate = 0.0f;
    private float velocityVariance = 1.0f;
    private static final float PROCESS_NOISE = 0.1f;
    private static final float MEASUREMENT_NOISE = 0.5f;

    // 비정상 거리 기준 상수
    private static final float MAX_REALISTIC_SPEED = 50.0f; // 50 m/s ≈ 180 km/h (지하철 최대 속도 기준)
    private static final float MIN_DISTANCE_THRESHOLD = 1.0f; // 최소 거리 임계값 (1m)

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData;
        this.imuData = imuData;
        this.finalDistance = 0.0f;
        this.transportMode = "UNKNOWN";
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

    // GPS 데이터 기반 거리 계산 (노이즈 필터링 추가)
    public float calculateGPSDistance() {
        float totalDistance = 0.0f;
        if (gpsData.size() < 2) return totalDistance;

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
            if (accuracy < 20.0f && distance > 1.0f) {
                totalDistance += distance;
            }
        }
        return totalDistance;
    }

    // IMU 데이터를 활용한 거리 추정 (간단한 칼만 필터 적용)
    public float calculateIMUDistance() {
        float totalDistance = 0.0f;
        long prevTime = 0;

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

    // 가속도 패턴 분석 (진동 주기성으로 걷기/차량 구분)
    private float calculateAccelerationVariance() {
        if (imuData.size() < 2) return 0.0f;
        float sum = 0.0f;
        float mean = 0.0f;

        for (Map<String, Object> data : imuData) {
            Number accXNum = (Number) data.get("accel.x");
            float accX = accXNum != null ? accXNum.floatValue() : 0.0f;
            sum += accX;
        }
        mean = sum / imuData.size();

        float variance = 0.0f;
        for (Map<String, Object> data : imuData) {
            Number accXNum = (Number) data.get("accel.x");
            float accX = accXNum != null ? accXNum.floatValue() : 0.0f;
            variance += (accX - mean) * (accX - mean);
        }
        return variance / imuData.size();
    }

    // 이동 수단 판별 (속도 + 가속도 패턴)
    public String determineTransportMode(float distance, float time) {
        float speed = time > 0 ? distance / time : 0.0f; // m/s
        float accVariance = calculateAccelerationVariance();

        if (speed < 1.5f && accVariance > 0.5f) return "WALK";
        else if (speed < 5f && accVariance > 0.3f) return "BIKE";
        else if (speed < 15f && accVariance < 0.2f) return "BUS";
        else if (speed < 30f && accVariance < 0.2f) return "CAR";
        else if (speed < 50f && accVariance < 0.1f) return "SUBWAY";
        else return "None";
    }

    // 분석 메서드 (GPS와 IMU 융합)
    public void analyze() {
        float gpsDistance = calculateGPSDistance();
        float imuDistance = calculateIMUDistance();

        float gpsWeight = gpsDistance > 0 ? 0.8f : 0.0f;
        float imuWeight = 1.0f - gpsWeight;
        this.finalDistance = gpsDistance * gpsWeight + imuDistance * imuWeight;

        long startTime = gpsData.isEmpty() ? (imuData.isEmpty() ? 0 : (long) imuData.get(0).get("timestamp"))
                : (long) gpsData.get(0).get("timestamp");
        long endTime = gpsData.isEmpty() ? (imuData.isEmpty() ? 0 : (long) imuData.get(imuData.size() - 1).get("timestamp"))
                : (long) gpsData.get(gpsData.size() - 1).get("timestamp");
        float totalTime = (endTime - startTime) / 1000.0f;

        // 거리 비정상 검증
        boolean isDistanceAbnormal = false;
        if (this.finalDistance < 0) {
            isDistanceAbnormal = true;
            Log.w("MovementAnalyzer", "비정상 거리 감지: 음수 거리 " + this.finalDistance);
        } else if (this.finalDistance < MIN_DISTANCE_THRESHOLD && totalTime > 0) {
            isDistanceAbnormal = true;
            Log.w("MovementAnalyzer", "비정상 거리 감지: 너무 작은 거리 " + this.finalDistance + " (시간: " + totalTime + "s)");
        } else if (totalTime > 0 && (this.finalDistance / totalTime) > MAX_REALISTIC_SPEED) {
            isDistanceAbnormal = true;
            Log.w("MovementAnalyzer", "비정상 거리 감지: 비현실적인 속도 " + (this.finalDistance / totalTime) + " m/s");
        }

        // 거리가 비정상적이거나 유효하지 않으면 None으로 설정
        if (isDistanceAbnormal || totalTime <= 0) {
            this.transportMode = "None";
        } else {
            this.transportMode = determineTransportMode(finalDistance, totalTime);
        }

        Log.d("MovementAnalyzer", "GPS Distance: " + gpsDistance + " meters");
        Log.d("MovementAnalyzer", "IMU Distance: " + imuDistance + " meters");
        Log.d("MovementAnalyzer", "Final Distance: " + finalDistance + " meters");
        Log.d("MovementAnalyzer", "Transport Mode: " + transportMode);
    }

    // Getter 메서드
    public float getDistance() {
        return this.finalDistance;
    }

    public String getTransportMode() {
        return this.transportMode;
    }
}
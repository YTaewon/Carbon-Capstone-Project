package com.example.myapplication12345.AI;

import android.util.Log;
import java.util.List;
import java.util.Map;

public class MovementAnalyzer {
    private List<Map<String, Object>> gpsData;
    private List<Map<String, Object>> imuData;

    private float finalDistance;
    private String transportMode;

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData;
        this.imuData = imuData;
        this.finalDistance = 0.0f; // float 리터럴 사용
        this.transportMode = "UNKNOWN";
    }

    // Haversine 공식으로 GPS 거리 계산 (float 기반)
    private float haversine(float lat1, float lon1, float lat2, float lon2) {
        final float R = 6371000f; // 지구 반지름 (미터), float로 변경
        float dLat = (float) Math.toRadians(lat2 - lat1);
        float dLon = (float) Math.toRadians(lon2 - lon1);

        float a = (float) (Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2));

        float c = (float) (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
        return R * c; // 결과값 (미터)
    }

    // GPS 데이터 기반 거리 계산
    public float calculateGPSDistance() {
        float totalDistance = 0.0f;
        if (gpsData.size() < 2) {
            return totalDistance; // 최소 2개 필요
        }

        for (int i = 1; i < gpsData.size(); i++) {
            Map<String, Object> prev = gpsData.get(i - 1);
            Map<String, Object> curr = gpsData.get(i);

            // Number 타입으로 안전하게 가져와 float로 변환
            Number lat1Num = (Number) prev.get("latitude");
            Number lon1Num = (Number) prev.get("longitude");
            Number lat2Num = (Number) curr.get("latitude");
            Number lon2Num = (Number) curr.get("longitude");

            float lat1 = lat1Num != null ? lat1Num.floatValue() : 0.0f;
            float lon1 = lon1Num != null ? lon1Num.floatValue() : 0.0f;
            float lat2 = lat2Num != null ? lat2Num.floatValue() : 0.0f;
            float lon2 = lon2Num != null ? lon2Num.floatValue() : 0.0f;

            totalDistance += haversine(lat1, lon1, lat2, lon2);
        }
        return totalDistance;
    }

    // IMU 데이터를 활용한 이동 거리 추정 (적분 방식)
    public float calculateIMUDistance() {
        float totalDistance = 0.0f;
        float velocity = 0.0f;
        long prevTime = 0;

        for (int i = 0; i < imuData.size(); i++) { // i=1 대신 i=0부터 시작하여 첫 데이터 활용
            Map<String, Object> curr = imuData.get(i);
            long timestamp = (long) curr.get("timestamp");

            // Number 타입으로 안전하게 가져와 float로 변환
            Number accXNum = (Number) curr.get("accel.x");
            Number accYNum = (Number) curr.get("accel.y");
            Number accZNum = (Number) curr.get("accel.z");

            float accX = accXNum != null ? accXNum.floatValue() : 0.0f;
            float accY = accYNum != null ? accYNum.floatValue() : 0.0f;
            float accZ = accZNum != null ? accZNum.floatValue() : 0.0f;

            float acceleration = (float) (Math.sqrt(accX * accX + accY * accY + accZ * accZ) - 9.81f);
            if (i > 0) { // 첫 번째 데이터는 이전 시간이 없으므로 제외
                float deltaTime = (timestamp - prevTime) / 1000.0f;
                velocity += acceleration * deltaTime;
                totalDistance += velocity * deltaTime;
            }
            prevTime = timestamp;
        }
        return totalDistance;
    }

    // 이동 수단 판별 (간단한 기준 적용)
    public String determineTransportMode(float distance, float time) {
        float speed = time > 0 ? distance / time : 0.0f; // m/s, 0 나누기 방지
        if (speed < 1.5f) return "WALK";
        else if (speed < 5f) return "BIKE";
        else if (speed < 15f) return "BUS";
        else if (speed < 30f) return "CAR";
        else if (speed < 50f) return "SUBWAY";
        else return "ETC";
    }

    // 분석 메서드
    public void analyze() {
        float gpsDistance = calculateGPSDistance();
        float imuDistance = calculateIMUDistance();
        this.finalDistance = (gpsDistance > 0) ? gpsDistance : imuDistance;

        // 시간 계산 (초 단위)
        long startTime = gpsData.isEmpty() ? 0 : (long) gpsData.get(0).get("timestamp");
        long endTime = gpsData.isEmpty() ? 0 : (long) gpsData.get(gpsData.size() - 1).get("timestamp");
        float totalTime = (endTime - startTime) / 1000.0f;

        this.transportMode = determineTransportMode(finalDistance, totalTime);

        Log.d("MovementAnalyzer", "Total Distance: " + finalDistance + " meters");
        Log.d("MovementAnalyzer", "Transport Mode: " + transportMode);
    }

    // 이동 거리 얻기
    public float getDistance() {
        return this.finalDistance;
    }

    // 이동 수단 얻기
    public String getTransportMode() {
        return this.transportMode;
    }
}
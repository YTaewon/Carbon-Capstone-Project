package com.example.myapplication12345.AI;

import java.util.List;
import java.util.Map;
import timber.log.Timber;

public class MovementAnalyzer {
    private final List<Map<String, Object>> gpsData; // 보조 데이터 (선택적)
    private final List<Map<String, Object>> imuData; // 주 데이터

    private float finalDistance;

    // 칼만 필터 변수 (속도와 위치 추정용)
    private float velocityEstimate = 0.0f; // 속도 추정치 (m/s)
    private float positionEstimate = 0.0f; // 위치 추정치 (m)
    private float velocityVariance = 1.0f; // 속도 분산
    private float positionVariance = 1.0f; // 위치 분산
    private static final float PROCESS_NOISE_V = 0.05f; // 속도 프로세스 노이즈
    private static final float PROCESS_NOISE_P = 0.01f; // 위치 프로세스 노이즈
    private static final float MEASUREMENT_NOISE_V = 0.5f; // 속도 측정 노이즈
    private static final float MEASUREMENT_NOISE_P = 1.0f; // 위치 측정 노이즈 (GPS 사용 시)

    // 중력 제거를 위한 저역통과 필터 변수
    private float[] gravity = {0.0f, 0.0f, 9.81f}; // 초기 중력 벡터 (m/s^2)
    private static final float ALPHA = 0.8f; // 저역통과 필터 상수

    // 비정상 거리 검증 상수
    private static final float MAX_REALISTIC_SPEED = 50.0f; // 50 m/s (180 km/h)
    private static final float MIN_DISTANCE_THRESHOLD = 0.1f; // 최소 거리 임계값 (0.1m)

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData; // 선택적으로 사용
        this.imuData = imuData;
        this.finalDistance = 0.0f;
    }

    // IMU 데이터를 활용한 거리 계산 (칼만 필터 적용)
    public float calculateIMUDistance() {
        float totalDistance = 0.0f;
        long prevTime = 0;

        if (imuData == null || imuData.isEmpty()) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터 없음");
            return totalDistance;
        }

        for (int i = 0; i < imuData.size(); i++) {
            Map<String, Object> curr = imuData.get(i);
            long timestamp = (long) curr.get("timestamp");

            Number accXNum = (Number) curr.get("accel.x");
            Number accYNum = (Number) curr.get("accel.y");
            Number accZNum = (Number) curr.get("accel.z");

            float accX = accXNum != null ? accXNum.floatValue() : 0.0f;
            float accY = accYNum != null ? accYNum.floatValue() : 0.0f;
            float accZ = accZNum != null ? accZNum.floatValue() : 0.0f;

            if (i == 0) {
                prevTime = timestamp;
                continue; // 첫 데이터는 초기화 용도로만 사용
            }

            // 시간 간격 계산 (ms → 초)
            float deltaTime = (timestamp - prevTime) / 1000.0f;
            prevTime = timestamp;

            // 저역통과 필터로 중력 성분 제거
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * accX;
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * accY;
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * accZ;

            // 선형 가속도 계산
            float linearAccX = accX - gravity[0];
            float linearAccY = accY - gravity[1];
            float linearAccZ = accZ - gravity[2];
            float linearAcc = (float) Math.sqrt(linearAccX * linearAccX + linearAccY * linearAccY + linearAccZ * linearAccZ);

            // 칼만 필터 예측 단계 (Predict)
            velocityEstimate += linearAcc * deltaTime; // 속도 예측
            positionEstimate += velocityEstimate * deltaTime; // 위치 예측
            velocityVariance += PROCESS_NOISE_V;
            positionVariance += PROCESS_NOISE_P + velocityVariance * deltaTime * deltaTime;

            // 칼만 필터 갱신 단계 (Update) - 속도 기준으로 보정
            float kalmanGainV = velocityVariance / (velocityVariance + MEASUREMENT_NOISE_V);
            velocityEstimate += kalmanGainV * (0.0f - velocityEstimate); // 속도 0으로 보정 (정지 가정)
            velocityVariance = (1 - kalmanGainV) * velocityVariance;

            // 위치 보정 (GPS 데이터가 있으면 사용, 없으면 생략)
            if (gpsData != null && !gpsData.isEmpty() && i % 100 == 0) { // 100번마다 GPS 보정 (약 1초 간격)
                float gpsDistance = estimateGPSDistanceAtTime(timestamp);
                if (gpsDistance >= 0) {
                    float kalmanGainP = positionVariance / (positionVariance + MEASUREMENT_NOISE_P);
                    positionEstimate += kalmanGainP * (gpsDistance - positionEstimate);
                    positionVariance = (1 - kalmanGainP) * positionVariance;
                }
            }

            // 속도 및 위치 음수 방지
            if (velocityEstimate < 0) velocityEstimate = 0;
            if (positionEstimate < 0) positionEstimate = 0;

            totalDistance = positionEstimate; // 누적 거리 업데이트
        }

        return totalDistance;
    }

    // Haversine 공식으로 GPS 거리 계산 (보조용)
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

    // 특정 시점에서의 GPS 기반 거리 추정 (IMU 보정용)
    private float estimateGPSDistanceAtTime(long targetTime) {
        if (gpsData == null || gpsData.size() < 2) return -1.0f;

        float totalDistance = 0.0f;
        long startTime = (long) gpsData.get(0).get("timestamp");

        for (int i = 1; i < gpsData.size() && (long) gpsData.get(i).get("timestamp") <= targetTime; i++) {
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

    // 분석 메서드 (IMU 위주로 거리 계산)
    public void analyze() {
        float imuDistance = calculateIMUDistance();
        this.finalDistance = imuDistance;

        // 시간 계산 (비정상 거리 검증용)
        long startTime = imuData.isEmpty() ? 0 : (long) imuData.get(0).get("timestamp");
        long endTime = imuData.isEmpty() ? 0 : (long) imuData.get(imuData.size() - 1).get("timestamp");
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

        Timber.tag("MovementAnalyzer").d("IMU Distance: " + imuDistance + " meters");
        Timber.tag("MovementAnalyzer").d("Final Distance: " + finalDistance + " meters");
    }

    // Getter 메서드
    public float getDistance() {
        return this.finalDistance;
    }
}
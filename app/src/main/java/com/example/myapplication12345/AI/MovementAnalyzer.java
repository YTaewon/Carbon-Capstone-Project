package com.example.myapplication12345.AI;

import java.util.List;
import java.util.Map;
import timber.log.Timber;

/**
 * GPS-IMU 센서 융합을 통해 사용자의 움직임을 분석하고 이동 경로와 거리를 추정하는 클래스.
 * 칼만 필터를 사용하여 IMU 데이터(단기적 정밀도)와 GPS 데이터(장기적 정확성)를 결합합니다.
 *
 * 핵심 기술:
 * 1. PDR (Pedestrian Dead-Reckoning): IMU 센서로 걸음을 추정.
 * 2. ZVU (Zero-Velocity Update): 사용자가 정지했을 때 속도 오차를 보정.
 * 3. Sensor Fusion (Kalman Filter): 주기적으로 GPS 위치를 사용하여 누적된 IMU 위치/방향 오차를 보정.
 * 4. Coordinate Transformation: GPS(위도/경도)를 지역 XY 평면 좌표계로 변환하여 사용.
 */
public class MovementAnalyzer {
    private static final String TAG = "MovementAnalyzer";

    // 입력 데이터
    private final List<Map<String, Object>> gpsData;
    private final List<Map<String, Object>> imuData;

    // 최종 계산 결과
    private float estimatedPathDistance;

    // --- 칼만 필터 상태 변수 (지역 XY 좌표계 기준) ---
    // 각 축(X: 동쪽, Y: 북쪽)에 대한 필터 상태를 별도로 관리
    private float positionEstimateX = 0.0f; // X축 위치 추정치 (m)
    private float velocityEstimateX = 0.0f; // X축 속도 추정치 (m/s)
    private float positionEstimateY = 0.0f; // Y축 위치 추정치 (m)
    private float velocityEstimateY = 0.0f; // Y축 속도 추정치 (m/s)

    private float positionVarianceX = 1.0f; // 위치 추정 오차의 분산 (X축)
    private float velocityVarianceX = 1.0f; // 속도 추정 오차의 분산 (X축)
    private float positionVarianceY = 1.0f; // 위치 추정 오차의 분산 (Y축)
    private float velocityVarianceY = 1.0f; // 속도 추정 오차의 분산 (Y축)

    // --- 칼만 필터 및 알고리즘 튜닝 파라미터 ---
    // 프로세스 노이즈: 시스템 모델 자체의 불확실성 (클수록 IMU의 예측을 덜 믿음)
    private static final float PROCESS_NOISE_POS = 0.01f;
    private static final float PROCESS_NOISE_VEL = 0.05f;

    // 측정 노이즈: 측정 센서의 불확실성 (클수록 측정값을 덜 믿음)
    private static final float MEASUREMENT_NOISE_ZVU = 0.1f;  // ZVU 측정(속도=0)의 노이즈
    private static final float MEASUREMENT_NOISE_GPS = 5.0f;  // GPS 위치 측정의 노이즈 (m)

    // ZVU (정지 감지) 임계값 (튜닝 필요)
    private static final float STATIC_LINEAR_ACCEL_THRESHOLD = 0.4f; // m/s^2
    private static final float STATIC_GYRO_THRESHOLD = 0.2f;         // rad/s

    // GPS 융합 관련 설정
    private static final int GPS_UPDATE_INTERVAL_MS = 5000; // 5초마다 GPS로 보정 시도
    private static final float GPS_ACCURACY_THRESHOLD = 20.0f; // 정확도 20m 이내의 GPS만 사용

    // 좌표계 변환 상수
    private static final float EARTH_RADIUS = 6371000f; // 지구 반지름 (m)

    // 지역 좌표계 원점
    private double originLat = 0.0;
    private double originLon = 0.0;
    private boolean isOriginSet = false;
    private long lastGpsUpdateTime = 0;

    // 중력 제거 폴백용 저역통과 필터
    private float[] gravity = {0.0f, 0.0f, 9.81f};
    private static final float ALPHA = 0.8f;

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData;
        this.imuData = imuData;
        this.estimatedPathDistance = 0.0f;
    }

    /**
     * 분석을 실행하고 최종 이동 거리를 계산.
     */
    public void analyze() {
        this.estimatedPathDistance = analyzeMovementAndEstimatePath();
        Timber.tag(TAG).d("분석 완료. 최종 추정 거리: %.2f meters", this.estimatedPathDistance);
    }

    /**
     * 추정된 총 이동 거리를 반환.
     * @return 이동 거리 (meters)
     */
    public float getDistance() {
        return this.estimatedPathDistance;
    }


    /**
     * IMU와 GPS 데이터를 융합하여 이동 경로를 추정하고 총 이동 거리를 계산하는 핵심 메소드.
     * @return 총 이동 경로 길이 (m)
     */
    private float analyzeMovementAndEstimatePath() {
        // 매 분석마다 상태 초기화
        initializeState();

        if (imuData == null || imuData.isEmpty()) {
            Timber.tag(TAG).w("IMU 데이터가 없어 분석을 중단합니다.");
            return 0.0f;
        }

        float totalPathDistance = 0.0f;
        long prevTime = (long) imuData.get(0).get("timestamp");
        float prevPositionX = 0.0f;
        float prevPositionY = 0.0f;

        for (Map<String, Object> currentImu : imuData) {
            long currentTime = (long) currentImu.get("timestamp");
            float deltaTime = (currentTime - prevTime) / 1000.0f;
            if (deltaTime <= 0) {
                prevTime = currentTime;
                continue;
            }
            prevTime = currentTime;

            // --- 1. IMU 데이터로 상태 예측 (Predict) ---
            float[] globalLinearAcc = getGlobalLinearAcceleration(currentImu);
            kalmanPredict(globalLinearAcc[0], globalLinearAcc[1], deltaTime);

            // --- 2. 측정값으로 상태 갱신 (Update) ---
            // 2-1. ZVU (정지 상태)로 속도 보정
            boolean isStationary = isDeviceStationary(currentImu);
            if (isStationary) {
                kalmanUpdateWithZVU();
            }

            // 2-2. 주기적으로 GPS 위치로 위치 보정
            if (currentTime - lastGpsUpdateTime > GPS_UPDATE_INTERVAL_MS) {
                kalmanUpdateWithGPS(currentTime);
                lastGpsUpdateTime = currentTime;
            }

            // --- 3. 경로 길이 누적 ---
            float deltaPos = (float) Math.sqrt(
                    Math.pow(positionEstimateX - prevPositionX, 2) +
                            Math.pow(positionEstimateY - prevPositionY, 2)
            );
            totalPathDistance += deltaPos;

            prevPositionX = positionEstimateX;
            prevPositionY = positionEstimateY;
        }

        return totalPathDistance;
    }

    /**
     * 칼만 필터 및 상태 변수 초기화
     */
    private void initializeState() {
        positionEstimateX = 0.0f;
        velocityEstimateX = 0.0f;
        positionEstimateY = 0.0f;
        velocityEstimateY = 0.0f;
        positionVarianceX = 1.0f;
        velocityVarianceX = 1.0f;
        positionVarianceY = 1.0f;
        velocityVarianceY = 1.0f;
        gravity = new float[]{0.0f, 0.0f, 9.81f};
        isOriginSet = false;
        originLat = 0.0;
        originLon = 0.0;
        lastGpsUpdateTime = 0;
        estimatedPathDistance = 0.0f;
    }

    /**
     * IMU 데이터를 사용하여 다음 상태(위치, 속도)를 예측.
     */
    private void kalmanPredict(float accelX, float accelY, float deltaTime) {
        // 속도와 위치를 물리 법칙에 따라 예측
        positionEstimateX += velocityEstimateX * deltaTime + 0.5f * accelX * deltaTime * deltaTime;
        velocityEstimateX += accelX * deltaTime;
        positionEstimateY += velocityEstimateY * deltaTime + 0.5f * accelY * deltaTime * deltaTime;
        velocityEstimateY += accelY * deltaTime;

        // 예측에 따른 오차(분산) 증가
        positionVarianceX += PROCESS_NOISE_POS;
        velocityVarianceX += PROCESS_NOISE_VEL;
        positionVarianceY += PROCESS_NOISE_POS;
        velocityVarianceY += PROCESS_NOISE_VEL;
    }

    /**
     * ZVU(영속도) 측정값을 사용하여 속도를 0으로 보정.
     */
    private void kalmanUpdateWithZVU() {
        // X축 속도 보정
        float kalmanGainVX = velocityVarianceX / (velocityVarianceX + MEASUREMENT_NOISE_ZVU);
        velocityEstimateX += kalmanGainVX * (0.0f - velocityEstimateX); // 측정값 0으로 보정
        velocityVarianceX = (1 - kalmanGainVX) * velocityVarianceX;

        // Y축 속도 보정
        float kalmanGainVY = velocityVarianceY / (velocityVarianceY + MEASUREMENT_NOISE_ZVU);
        velocityEstimateY += kalmanGainVY * (0.0f - velocityEstimateY);
        velocityVarianceY = (1 - kalmanGainVY) * velocityVarianceY;
    }

    /**
     * GPS 위치 측정값을 사용하여 위치를 보정.
     */
    private void kalmanUpdateWithGPS(long currentTime) {
        Map<String, Object> gpsPoint = findLatestGpsPointBefore(currentTime);
        if (gpsPoint == null) {
            Timber.tag(TAG).d("시점 %d 이전에 유효한 GPS 데이터가 없어 보정을 건너뜁니다.", currentTime);
            return;
        }

        double lat = ((Number) gpsPoint.get("latitude")).doubleValue();
        double lon = ((Number) gpsPoint.get("longitude")).doubleValue();

        if (!isOriginSet) {
            // 첫 유효 GPS를 지역 좌표계의 원점으로 설정
            originLat = lat;
            originLon = lon;
            isOriginSet = true;
            lastGpsUpdateTime = currentTime; // 첫 GPS 시간을 기준 시간으로 설정
            // 원점이 설정되었으므로 현재 위치 추정치를 0으로 강제 설정
            positionEstimateX = 0.0f;
            positionEstimateY = 0.0f;
            Timber.tag(TAG).i("GPS 원점 설정: (%.6f, %.6f)", originLat, originLon);
            return;
        }

        // 현재 GPS 위치를 지역 XY 좌표로 변환
        float[] measurementXY = gpsToLocalXY(lat, lon);

        // X축 위치 보정
        float kalmanGainPX = positionVarianceX / (positionVarianceX + MEASUREMENT_NOISE_GPS);
        positionEstimateX += kalmanGainPX * (measurementXY[0] - positionEstimateX);
        positionVarianceX = (1 - kalmanGainPX) * positionVarianceX;

        // Y축 위치 보정
        float kalmanGainPY = positionVarianceY / (positionVarianceY + MEASUREMENT_NOISE_GPS);
        positionEstimateY += kalmanGainPY * (measurementXY[1] - positionEstimateY);
        positionVarianceY = (1 - kalmanGainPY) * positionVarianceY;

        Timber.tag(TAG).d("GPS 보정 적용. IMU 추정치 (%.2f, %.2f) -> GPS 보정 후 (%.2f, %.2f)",
                positionEstimateX, positionEstimateY, measurementXY[0], measurementXY[1]);
    }


    // --- Helper Methods ---

    /**
     * IMU 데이터에서 중력이 제거된 지구 좌표계 기준의 선형 가속도를 추출.
     * @param imuDataPoint 현재 IMU 데이터
     * @return float[]{accelX, accelY, accelZ}
     */
    private float[] getGlobalLinearAcceleration(Map<String, Object> imuDataPoint) {
        float[] accToRotate = new float[3];
        // 1. 중력 제거된 선형 가속도 얻기 (Fallback 로직 포함)
        if (!getFloatArrayFromMap(imuDataPoint, "linear_accel.x", "linear_accel.y", "linear_accel.z", accToRotate)) {
            float[] rawAcc = new float[3];
            if (getFloatArrayFromMap(imuDataPoint, "accel.x", "accel.y", "accel.z", rawAcc)) {
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * rawAcc[0];
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * rawAcc[1];
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * rawAcc[2];
                accToRotate[0] = rawAcc[0] - gravity[0];
                accToRotate[1] = rawAcc[1] - gravity[1];
                accToRotate[2] = rawAcc[2] - gravity[2];
            }
        }

        // 2. 쿼터니언으로 지구 좌표계로 회전
        float[] quat = new float[4];
        float[] globalLinearAcc = new float[3];
        if (getFloatArrayFromMap(imuDataPoint, "rot.w", "rot.x", "rot.y", "rot.z", quat)) {
            rotateVectorByQuaternion(accToRotate, quat, globalLinearAcc);
        }
        return globalLinearAcc;
    }

    /**
     * 기기가 정지 상태인지 판단.
     */
    private boolean isDeviceStationary(Map<String, Object> imuDataPoint) {
        float[] linearAcc = new float[3];
        float[] gyro = new float[3];

        getFloatArrayFromMap(imuDataPoint, "linear_accel.x", "linear_accel.y", "linear_accel.z", linearAcc);
        getFloatArrayFromMap(imuDataPoint, "gyro.x", "gyro.y", "gyro.z", gyro);

        float linearAccMagnitude = (float) Math.sqrt(linearAcc[0] * linearAcc[0] + linearAcc[1] * linearAcc[1] + linearAcc[2] * linearAcc[2]);
        float gyroMagnitude = (float) Math.sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2]);

        return linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD && gyroMagnitude < STATIC_GYRO_THRESHOLD;
    }

    /**
     * 특정 시점 이전에 수신된 가장 최신의 유효한(정확도 높은) GPS 포인트를 찾음.
     */
    private Map<String, Object> findLatestGpsPointBefore(long timestamp) {
        if (gpsData == null || gpsData.isEmpty()) return null;

        Map<String, Object> latestValidGps = null;
        for (int i = gpsData.size() - 1; i >= 0; i--) {
            Map<String, Object> point = gpsData.get(i);
            long gpsTime = (long) point.get("timestamp");

            if (gpsTime <= timestamp) {
                float accuracy = ((Number) point.get("accuracy")).floatValue();
                if (accuracy < GPS_ACCURACY_THRESHOLD) {
                    latestValidGps = point;
                    break;
                }
            }
        }
        return latestValidGps;
    }

    /**
     * 위도/경도를 원점 기준의 지역 XY 평면 좌표(미터)로 변환 (Equirectangular projection).
     */
    private float[] gpsToLocalXY(double lat, double lon) {
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double originLatRad = Math.toRadians(originLat);
        double originLonRad = Math.toRadians(originLon);

        float x = (float) ((lonRad - originLonRad) * Math.cos((latRad + originLatRad) / 2) * EARTH_RADIUS);
        float y = (float) ((latRad - originLatRad) * EARTH_RADIUS);

        return new float[]{x, y};
    }

    /**
     * 쿼터니언으로 벡터를 회전.
     */
    private void rotateVectorByQuaternion(float[] vector, float[] quaternion, float[] out) {
        float qx = quaternion[1], qy = quaternion[2], qz = quaternion[3], qw = quaternion[0];
        float vx = vector[0], vy = vector[1], vz = vector[2];

        // q * v
        float tx = qw * vx + qy * vz - qz * vy;
        float ty = qw * vy - qx * vz + qz * vx;
        float tz = qw * vz + qx * vy - qy * vx;
        float tw = -qx * vx - qy * vy - qz * vz;

        // (q*v) * q_conjugate
        out[0] = tx * qw - tw * qx - ty * qz + tz * qy;
        out[1] = ty * qw - tw * qy + tx * qz - tz * qx;
        out[2] = tz * qw - tw * qz - tx * qy + ty * qx;
    }

    /**
     * Map에서 float 배열 값들을 안전하게 가져오는 헬퍼 메서드.
     */
    private boolean getFloatArrayFromMap(Map<String, Object> map, String k1, String k2, String k3, float[] out) {
        Number n1 = (Number) map.get(k1);
        Number n2 = (Number) map.get(k2);
        Number n3 = (Number) map.get(k3);
        if (n1 != null && n2 != null && n3 != null) {
            out[0] = n1.floatValue();
            out[1] = n2.floatValue();
            out[2] = n3.floatValue();
            return true;
        }
        return false;
    }

    private boolean getFloatArrayFromMap(Map<String, Object> map, String k0, String k1, String k2, String k3, float[] out) {
        Number n0 = (Number) map.get(k0);
        Number n1 = (Number) map.get(k1);
        Number n2 = (Number) map.get(k2);
        Number n3 = (Number) map.get(k3);
        if (n0 != null && n1 != null && n2 != null && n3 != null) {
            out[0] = n0.floatValue();
            out[1] = n1.floatValue();
            out[2] = n2.floatValue();
            out[3] = n3.floatValue();
            return true;
        }
        return false;
    }
}
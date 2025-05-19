package com.example.myapplication12345.AI;

import java.util.List;
import java.util.Map;
import timber.log.Timber;

public class MovementAnalyzer {
    private final List<Map<String, Object>> gpsData; // 보조 데이터 (선택적)
    private final List<Map<String, Object>> imuData; // 주 데이터

    private float finalDistance;

    // 칼만 필터 변수 (속도와 위치 추정용)
    // 이 변수들은 calculateIMUDistance 또는 analyze 호출 시 사용됩니다.
    // 새로운 메서드에서는 로컬 변수를 사용하거나, 이 변수들을 재설정해야 합니다.
    private float velocityEstimate = 0.0f; // 속도 추정치 (m/s)
    private float positionEstimate = 0.0f; // 위치 추정치 (m)
    private float velocityVariance = 1.0f; // 속도 분산
    private float positionVariance = 1.0f; // 위치 분산
    private static final float PROCESS_NOISE_V = 0.05f; // 속도 프로세스 노이즈
    private static final float PROCESS_NOISE_P = 0.01f; // 위치 프로세스 노이즈
    private static final float MEASUREMENT_NOISE_V = 0.5f; // 속도 측정 노이즈
    private static final float MEASUREMENT_NOISE_P = 1.0f; // 위치 측정 노이즈 (GPS 사용 시)

    // 중력 제거를 위한 저역통과 필터 변수
    // 이 변수도 calculateIMUDistance 또는 analyze 호출 시 사용됩니다.
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
        // 클래스 멤버 변수를 사용하므로, 호출 시마다 상태가 누적될 수 있음을 인지해야 합니다.
        // 만약 독립적인 계산을 원한다면, 이 메서드 내에서 칼만 필터 변수들을 초기화해야 합니다.
        // 여기서는 기존 로직을 유지합니다.
        this.velocityEstimate = 0.0f;
        this.positionEstimate = 0.0f;
        this.velocityVariance = 1.0f;
        this.positionVariance = 1.0f;
        this.gravity = new float[]{0.0f, 0.0f, 9.81f};


        float totalDistance = 0.0f;
        long prevTime = 0;

        if (imuData == null || imuData.isEmpty()) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터 없음 (calculateIMUDistance)");
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

            float deltaTime = (timestamp - prevTime) / 1000.0f;
            if (deltaTime <= 0) { // 시간 간격이 없거나 잘못된 경우 건너뛰기
                prevTime = timestamp;
                continue;
            }
            prevTime = timestamp;

            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * accX;
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * accY;
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * accZ;

            float linearAccX = accX - gravity[0];
            float linearAccY = accY - gravity[1];
            float linearAccZ = accZ - gravity[2];
            float linearAcc = (float) Math.sqrt(linearAccX * linearAccX + linearAccY * linearAccY + linearAccZ * linearAccZ);

            velocityEstimate += linearAcc * deltaTime;
            positionEstimate += velocityEstimate * deltaTime;
            velocityVariance += PROCESS_NOISE_V;
            positionVariance += PROCESS_NOISE_P + velocityVariance * deltaTime * deltaTime;

            float kalmanGainV = velocityVariance / (velocityVariance + MEASUREMENT_NOISE_V);
            velocityEstimate += kalmanGainV * (0.0f - velocityEstimate);
            velocityVariance = (1 - kalmanGainV) * velocityVariance;

            if (gpsData != null && !gpsData.isEmpty() && i % 100 == 0) {
                float gpsDistance = estimateGPSDistanceAtTime(timestamp);
                if (gpsDistance >= 0) {
                    float kalmanGainP = positionVariance / (positionVariance + MEASUREMENT_NOISE_P);
                    positionEstimate += kalmanGainP * (gpsDistance - positionEstimate);
                    positionVariance = (1 - kalmanGainP) * positionVariance;
                }
            }

            if (velocityEstimate < 0) velocityEstimate = 0;
            if (positionEstimate < 0) positionEstimate = 0;

            totalDistance = positionEstimate;
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
        // long startTime = (long) gpsData.get(0).get("timestamp"); // 사용되지 않음

        for (int i = 1; i < gpsData.size(); i++) {
            Map<String, Object> prevGps = gpsData.get(i - 1);
            Map<String, Object> currGps = gpsData.get(i);

            long currGpsTimestamp = (long) currGps.get("timestamp");
            if (currGpsTimestamp > targetTime && i > 1) { // targetTime 이전까지의 데이터만 사용
                // 만약 targetTime과 가장 가까운 GPS 지점까지의 거리를 추정하려면 보간법 필요
                // 여기서는 targetTime 이전의 마지막 GPS 지점까지의 누적 거리를 반환
                break;
            }


            Number lat1Num = (Number) prevGps.get("latitude");
            Number lon1Num = (Number) prevGps.get("longitude");
            Number lat2Num = (Number) currGps.get("latitude");
            Number lon2Num = (Number) currGps.get("longitude");
            Number accNum = (Number) currGps.get("accuracy");

            float lat1 = lat1Num != null ? lat1Num.floatValue() : 0.0f;
            float lon1 = lon1Num != null ? lon1Num.floatValue() : 0.0f;
            float lat2 = lat2Num != null ? lat2Num.floatValue() : 0.0f;
            float lon2 = lon2Num != null ? lon2Num.floatValue() : 0.0f;
            float accuracy = accNum != null ? accNum.floatValue() : Float.MAX_VALUE;

            float distance = haversine(lat1, lon1, lat2, lon2);
            if (accuracy < 20.0f && distance > MIN_DISTANCE_THRESHOLD) { // 정확도 20m 미만, 최소 이동거리 0.1m 이상
                totalDistance += distance;
            }

            if (currGpsTimestamp >= targetTime) break; // targetTime에 도달했거나 넘어섰으면 중단
        }
        return totalDistance > 0 ? totalDistance : -1.0f; // 거리가 계산된 경우에만 반환
    }

    // 분석 메서드 (IMU 위주로 거리 계산)
    public void analyze() {
        // analyze 호출 시 필터 변수들 초기화
        this.velocityEstimate = 0.0f;
        this.positionEstimate = 0.0f;
        this.velocityVariance = 1.0f;
        this.positionVariance = 1.0f;
        this.gravity = new float[]{0.0f, 0.0f, 9.81f};

        float imuDistance = calculateIMUDistance(); // 내부적으로 필터 변수들 사용 및 업데이트
        this.finalDistance = imuDistance;

        if (imuData == null || imuData.isEmpty()) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터가 없어 분석 불가");
            this.finalDistance = 0.0f;
            return;
        }

        long startTime = (long) imuData.get(0).get("timestamp");
        long endTime = (long) imuData.get(imuData.size() - 1).get("timestamp");
        float totalTime = (endTime - startTime) / 1000.0f;

        if (this.finalDistance < 0) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 음수 거리 %s", this.finalDistance);
            this.finalDistance = 0.0f;
        } else if (totalTime > 1.0f && this.finalDistance < MIN_DISTANCE_THRESHOLD ) { // 1초 이상 움직였는데 거리가 매우 작으면 0으로 처리
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 너무 작은 거리 " + this.finalDistance + " (시간: " + totalTime + "s)");
            this.finalDistance = 0.0f;
        } else if (totalTime > 0 && (this.finalDistance / totalTime) > MAX_REALISTIC_SPEED) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 비현실적인 속도 " + (this.finalDistance / totalTime) + " m/s");
            // 비현실적인 속도의 경우, GPS 데이터가 있다면 GPS 기반으로 대체하거나, 0으로 처리할 수 있음.
            // 여기서는 우선 0으로 처리.
            this.finalDistance = 0.0f;
        }

        Timber.tag("MovementAnalyzer").d("IMU Distance (analyze): " + imuDistance + " meters");
        Timber.tag("MovementAnalyzer").d("Final Distance (analyze): " + this.finalDistance + " meters");
    }

    // Getter 메서드
    public float getDistance() {
        return this.finalDistance;
    }

    /**
     * IMU 데이터만을 사용하여 평균 속도를 계산합니다.
     * 이 메서드는 클래스 멤버 변수(필터 상태)를 변경하지 않고, 로컬 변수만을 사용합니다.
     * @param imuDataToProcess 사용할 IMU 데이터 리스트
     * @return 계산된 평균 속도 (m/s). 데이터가 부족하거나 시간이 0이면 0.0f 반환.
     */
    public float calculateAverageSpeedFromIMU(List<Map<String, Object>> imuDataToProcess) {
        if (imuDataToProcess == null || imuDataToProcess.size() < 2) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터가 부족하여 평균 속도를 계산할 수 없습니다.");
            return 0.0f;
        }

        // 이 메서드 내에서 사용할 로컬 칼만 필터 변수 및 중력 변수
        float currentVelocityEstimate = 0.0f;
        float currentPositionEstimate = 0.0f;
        float currentVelocityVariance = 1.0f;
        float currentPositionVariance = 1.0f; // 위치 분산은 이 메서드에서 직접 사용되진 않지만 일관성을 위해 포함
        float[] currentGravity = {0.0f, 0.0f, 9.81f};

        long firstTimestamp = -1;
        long lastTimestamp = -1;
        long prevTime = 0;

        for (int i = 0; i < imuDataToProcess.size(); i++) {
            Map<String, Object> curr = imuDataToProcess.get(i);
            long timestamp = (long) curr.get("timestamp");

            if (i == 0) {
                firstTimestamp = timestamp;
                prevTime = timestamp;
                continue;
            }
            lastTimestamp = timestamp; // 마지막 유효한 타임스탬프 업데이트

            Number accXNum = (Number) curr.get("accel.x");
            Number accYNum = (Number) curr.get("accel.y");
            Number accZNum = (Number) curr.get("accel.z");

            float accX = accXNum != null ? accXNum.floatValue() : 0.0f;
            float accY = accYNum != null ? accYNum.floatValue() : 0.0f;
            float accZ = accZNum != null ? accZNum.floatValue() : 0.0f;

            float deltaTime = (timestamp - prevTime) / 1000.0f;
            if (deltaTime <= 0) { // 시간 간격이 없거나 음수면 건너뛰기
                prevTime = timestamp; // prevTime은 업데이트하여 다음 루프에서 올바른 deltaTime 계산
                if(i == imuDataToProcess.size() -1 && firstTimestamp == timestamp) {
                    // 모든 데이터의 타임스탬프가 같을 경우 totalTime이 0이 됨
                    firstTimestamp = -1; // 유효하지 않음을 표시
                }
                continue;
            }
            prevTime = timestamp;

            // 저역통과 필터로 중력 성분 제거
            currentGravity[0] = ALPHA * currentGravity[0] + (1 - ALPHA) * accX;
            currentGravity[1] = ALPHA * currentGravity[1] + (1 - ALPHA) * accY;
            currentGravity[2] = ALPHA * currentGravity[2] + (1 - ALPHA) * accZ;

            // 선형 가속도 계산
            float linearAccX = accX - currentGravity[0];
            float linearAccY = accY - currentGravity[1];
            float linearAccZ = accZ - currentGravity[2];
            float linearAcc = (float) Math.sqrt(linearAccX * linearAccX + linearAccY * linearAccY + linearAccZ * linearAccZ);

            // 칼만 필터 예측 단계 (Predict)
            currentVelocityEstimate += linearAcc * deltaTime; // 속도 예측
            currentPositionEstimate += currentVelocityEstimate * deltaTime; // 위치 예측 (이동 거리 누적)

            currentVelocityVariance += PROCESS_NOISE_V;
            // currentPositionVariance += PROCESS_NOISE_P + currentVelocityVariance * deltaTime * deltaTime; // 위치 분산은 사용 안함

            // 칼만 필터 갱신 단계 (Update) - 속도 기준으로 보정 (정지 가정)
            float kalmanGainV = currentVelocityVariance / (currentVelocityVariance + MEASUREMENT_NOISE_V);
            currentVelocityEstimate += kalmanGainV * (0.0f - currentVelocityEstimate);
            currentVelocityVariance = (1 - kalmanGainV) * currentVelocityVariance;

            // 속도 및 위치 음수 방지
            if (currentVelocityEstimate < 0) currentVelocityEstimate = 0;
            if (currentPositionEstimate < 0) currentPositionEstimate = 0;
        }

        if (firstTimestamp == -1 || lastTimestamp == -1 || firstTimestamp == lastTimestamp) {
            Timber.tag("MovementAnalyzer").w("유효한 시간 간격을 계산할 수 없어 평균 속도를 0으로 반환합니다.");
            return 0.0f;
        }

        float totalDistance = currentPositionEstimate; // 누적된 총 이동 거리
        float totalTimeSeconds = (lastTimestamp - firstTimestamp) / 1000.0f;

        if (totalTimeSeconds <= 0) {
            Timber.tag("MovementAnalyzer").w("총 이동 시간이 0 또는 음수이므로 평균 속도를 계산할 수 없습니다.");
            return 0.0f;
        }

        float averageSpeed = totalDistance / totalTimeSeconds;

        // 비정상 속도 검증 (옵션)
        if (averageSpeed > MAX_REALISTIC_SPEED) {
            Timber.tag("MovementAnalyzer").w("계산된 평균 속도(" + averageSpeed + " m/s)가 비현실적입니다. IMU 데이터에 노이즈가 많을 수 있습니다.");
            // 필요시 0.0f 또는 다른 값으로 조정 가능
        }

        Timber.tag("MovementAnalyzer").d("IMU 기반 평균 속도: " + averageSpeed + " m/s (거리: " + totalDistance + "m, 시간: " + totalTimeSeconds + "s)");
        return averageSpeed;
    }
}
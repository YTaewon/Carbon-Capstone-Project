package com.example.myapplication12345.AI;

import java.util.List;
import java.util.Map;
import timber.log.Timber;

public class MovementAnalyzer {
    private final List<Map<String, Object>> gpsData; // 보조 데이터 (선택적)
    private final List<Map<String, Object>> imuData; // 주 데이터

    private float finalDistance;

    // 칼만 필터 변수 (속도와 위치 추정용 - 2D (X, Y) 추적을 위해 확장)
    private float positionEstimateX = 0.0f;
    private float velocityEstimateX = 0.0f;
    private float positionEstimateY = 0.0f;
    private float velocityEstimateY = 0.0f;

    private float velocityVarianceX = 1.0f;
    private float positionVarianceX = 1.0f;
    private float velocityVarianceY = 1.0f;
    private float positionVarianceY = 1.0f;

    // 칼만 필터 노이즈 파라미터 (튜닝 필요)
    private static final float PROCESS_NOISE_VEL = 0.05f;
    private static final float PROCESS_NOISE_POS = 0.01f;
    private static final float MEASUREMENT_NOISE_VEL = 0.5f;

    // 중력 제거를 위한 저역통과 필터 변수
    private float[] gravity = {0.0f, 0.0f, 9.81f};
    private static final float ALPHA = 0.8f;

    // 비정상 거리/속도 검증 상수
    private static final float MAX_REALISTIC_SPEED = 50.0f; // 50 m/s (180 km/h)
    private static final float MIN_DISTANCE_THRESHOLD = 0.1f; // 최소 거리 임계값 (0.1m)

    // 정지 감지(ZVU)를 위한 임계값
    private static final float STATIC_LINEAR_ACCEL_THRESHOLD = 0.4f;
    private static final float STATIC_GYRO_THRESHOLD = 0.2f;

    // SensorDataService의 IMU 수집 주기를 기반으로 한 시간 간격 (10ms)
    private static final float IMU_SAMPLE_INTERVAL_SEC = 0.01f;

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData;
        this.imuData = imuData;
        this.finalDistance = 0.0f;
    }

    /**
     * IMU 데이터를 활용한 거리 계산 (칼만 필터, 지구 좌표계, 조건부 ZVU 적용).
     *
     * @return 총 이동 거리 (m)
     */
    public float calculateIMUDistance() {
        // 필터 상태 초기화
        positionEstimateX = 0.0f;
        velocityEstimateX = 0.0f;
        positionEstimateY = 0.0f;
        velocityEstimateY = 0.0f;
        velocityVarianceX = 1.0f;
        positionVarianceX = 1.0f;
        velocityVarianceY = 1.0f;
        positionVarianceY = 1.0f;
        gravity = new float[]{0.0f, 0.0f, 9.81f};

        float totalPathDistance = 0.0f;
        float prevPositionX = 0.0f;
        float prevPositionY = 0.0f;

        if (imuData == null || imuData.isEmpty()) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터 없음 (calculateIMUDistance)");
            return totalPathDistance;
        }

        long startTime = (long) imuData.get(0).get("timestamp");

        for (int i = 0; i < imuData.size(); i++) {
            Map<String, Object> curr = imuData.get(i);

            // SensorDataService는 1초 내의 IMU 데이터에 동일한 timestamp를 사용하므로,
            // 고정된 샘플링 간격으로 deltaTime을 설정해야 함.
            final float deltaTime = IMU_SAMPLE_INTERVAL_SEC;

            // 1. 센서 데이터 추출 (수정된 헬퍼 메서드 사용)
            float[] currentLinearAcc = new float[3];
            float[] currentGyro = new float[3];
            float[] currentQuat = new float[4]; // w, x, y, z

            boolean hasLinearAccel = getVector3fFromMap(curr, "linear_accel", currentLinearAcc);
            boolean hasGyro = getVector3fFromMap(curr, "gyro", currentGyro);
            boolean hasQuat = getQuaternionFromMap(curr, "rot", currentQuat);

            float[] accToRotate = new float[3]; // 회전시킬 가속도 벡터 (장치 좌표계)

            if (hasLinearAccel) {
                System.arraycopy(currentLinearAcc, 0, accToRotate, 0, 3);
            } else {
                float[] rawAcc = new float[3];
                if (getVector3fFromMap(curr, "accel", rawAcc)) {
                    float[] sensorGravity = new float[3];
                    if (getVector3fFromMap(curr, "gravity", sensorGravity)) {
                        accToRotate[0] = rawAcc[0] - sensorGravity[0];
                        accToRotate[1] = rawAcc[1] - sensorGravity[1];
                        accToRotate[2] = rawAcc[2] - sensorGravity[2];
                    } else {
                        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * rawAcc[0];
                        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * rawAcc[1];
                        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * rawAcc[2];
                        accToRotate[0] = rawAcc[0] - gravity[0];
                        accToRotate[1] = rawAcc[1] - gravity[1];
                        accToRotate[2] = rawAcc[2] - gravity[2];
                    }
                } else {
                    Timber.tag("MovementAnalyzer").w("가속도 데이터 없음. 건너뛰기.");
                    continue;
                }
            }

            // 2. 가속도 벡터를 지구 좌표계(Global Frame)로 변환
            float[] globalLinearAcc = new float[3];
            if (hasQuat) {
                rotateVectorByQuaternion(accToRotate, currentQuat, globalLinearAcc);
                globalLinearAcc[2] = 0.0f; // 수평 이동만 고려
            } else {
                Timber.tag("MovementAnalyzer").w("쿼터니언 데이터 없음. 2D IMU 추적 불가.");
                globalLinearAcc[0] = 0.0f;
                globalLinearAcc[1] = 0.0f;
                globalLinearAcc[2] = 0.0f;
            }

            float accelX = globalLinearAcc[0];
            float accelY = globalLinearAcc[1];

            // 3. 정지 감지 (ZVU 조건)
            boolean isStationary = false;
            float linearAccMagnitude = (float) Math.sqrt(accToRotate[0] * accToRotate[0] + accToRotate[1] * accToRotate[1] + accToRotate[2] * accToRotate[2]);
            if (hasGyro) {
                float gyroMagnitude = (float) Math.sqrt(currentGyro[0] * currentGyro[0] + currentGyro[1] * currentGyro[1] + currentGyro[2] * currentGyro[2]);
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD && gyroMagnitude < STATIC_GYRO_THRESHOLD;
            } else {
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD;
            }

            // 4. 칼만 필터 예측 (Predict)
            velocityEstimateX += accelX * deltaTime;
            positionEstimateX += velocityEstimateX * deltaTime;
            velocityVarianceX += PROCESS_NOISE_VEL;
            positionVarianceX += PROCESS_NOISE_POS + velocityVarianceX * deltaTime * deltaTime;

            velocityEstimateY += accelY * deltaTime;
            positionEstimateY += velocityEstimateY * deltaTime;
            velocityVarianceY += PROCESS_NOISE_VEL;
            positionVarianceY += PROCESS_NOISE_POS + velocityVarianceY * deltaTime * deltaTime;

            // 5. 칼만 필터 갱신 (Update) - ZVU
            if (isStationary) {
                float kalmanGainVX = velocityVarianceX / (velocityVarianceX + MEASUREMENT_NOISE_VEL);
                velocityEstimateX += kalmanGainVX * (0.0f - velocityEstimateX);
                velocityVarianceX = (1 - kalmanGainVX) * velocityVarianceX;

                float kalmanGainVY = velocityVarianceY / (velocityVarianceY + MEASUREMENT_NOISE_VEL);
                velocityEstimateY += kalmanGainVY * (0.0f - velocityEstimateY);
                velocityVarianceY = (1 - kalmanGainVY) * velocityVarianceY;
            }

            // 6. 각 스텝에서의 이동 거리 계산 및 누적
            float deltaPos = (float) Math.sqrt(Math.pow(positionEstimateX - prevPositionX, 2) + Math.pow(positionEstimateY - prevPositionY, 2));
            totalPathDistance += deltaPos;

            prevPositionX = positionEstimateX;
            prevPositionY = positionEstimateY;
        }

        // 최종 거리 검증
        this.finalDistance = totalPathDistance;
        long endTime = (long) imuData.get(imuData.size() - 1).get("timestamp");
        float totalTime = (endTime - startTime) / 1000.0f;

        if (this.finalDistance < 0) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 음수 거리 %s", this.finalDistance);
            this.finalDistance = 0.0f;
        } else if (totalTime > 1.0f && this.finalDistance < MIN_DISTANCE_THRESHOLD) {
            this.finalDistance = 0.0f;
        } else if (totalTime > 0 && (this.finalDistance / totalTime) > MAX_REALISTIC_SPEED) {
            this.finalDistance = 0.0f;
        }

        Timber.tag("MovementAnalyzer").d("IMU Total Path Distance: " + totalPathDistance + " meters");
        Timber.tag("MovementAnalyzer").d("Final Distance (after validation): " + this.finalDistance + " meters");
        return this.finalDistance;
    }

    /**
     * [수정됨] Map에서 3축 벡터(x, y, z) 값을 안전하게 가져오는 헬퍼 메서드.
     * SensorDataService가 "baseKey.x", "baseKey.y", "baseKey.z" 형태로 저장하는 데이터를 읽습니다.
     * @return 모든 키가 성공적으로 파싱되면 true, 그렇지 않으면 false.
     */
    private boolean getVector3fFromMap(Map<String, Object> map, String baseKey, float[] outArray) {
        Number numX = (Number) map.get(baseKey + ".x");
        Number numY = (Number) map.get(baseKey + ".y");
        Number numZ = (Number) map.get(baseKey + ".z");
        if (numX != null && numY != null && numZ != null) {
            outArray[0] = numX.floatValue();
            outArray[1] = numY.floatValue();
            outArray[2] = numZ.floatValue();
            return true;
        }
        return false;
    }

    /**
     * [수정됨] Map에서 쿼터니언(w, x, y, z) 값을 안전하게 가져오는 헬퍼 메서드.
     * SensorDataService가 "baseKey.w", "baseKey.x" 형태로 저장하는 데이터를 읽습니다.
     * @return 모든 키가 성공적으로 파싱되면 true, 그렇지 않으면 false.
     */
    private boolean getQuaternionFromMap(Map<String, Object> map, String baseKey, float[] outArray) {
        Number numW = (Number) map.get(baseKey + ".w");
        Number numX = (Number) map.get(baseKey + ".x");
        Number numY = (Number) map.get(baseKey + ".y");
        Number numZ = (Number) map.get(baseKey + ".z");
        if (numW != null && numX != null && numY != null && numZ != null) {
            outArray[0] = numW.floatValue(); // w
            outArray[1] = numX.floatValue(); // x
            outArray[2] = numY.floatValue(); // y
            outArray[3] = numZ.floatValue(); // z
            return true;
        }
        return false;
    }

    /**
     * 쿼터니언으로 벡터를 회전시켜 지구 좌표계로 변환합니다.
     * (이하 코드는 기존과 동일, 수정 없음)
     */
    private void rotateVectorByQuaternion(float[] vectorToRotate, float[] quaternion, float[] rotatedVector) {
        float q0 = quaternion[0]; // w
        float q1 = quaternion[1]; // x
        float q2 = quaternion[2]; // y
        float q3 = quaternion[3]; // z
        float vx = vectorToRotate[0];
        float vy = vectorToRotate[1];
        float vz = vectorToRotate[2];
        float tw = -q1 * vx - q2 * vy - q3 * vz;
        float tvx = q0 * vx + q2 * vz - q3 * vy;
        float tvy = q0 * vy + q3 * vx - q1 * vz;
        float tvz = q0 * vz + q1 * vy - q2 * vx;
        rotatedVector[0] = tw * (-q1) + tvx * q0 - tvy * q3 + tvz * q2;
        rotatedVector[1] = tw * (-q2) + tvy * q0 - tvz * q1 + tvx * q3;
        rotatedVector[2] = tw * (-q3) + tvz * q0 - tvx * q2 + tvy * q1;
    }


    public void analyze() {
        this.finalDistance = calculateIMUDistance();
        Timber.tag("MovementAnalyzer").d("Analysis completed. Final Distance: " + this.finalDistance + " meters");
    }

    public float getDistance() {
        return this.finalDistance;
    }

    // 이하는 calculateIMUDistance의 로직과 거의 동일하므로,
    // 해당 부분의 수정사항(헬퍼메서드, deltaTime)도 동일하게 적용해야 합니다.
    // 기존 코드의 문제점들을 그대로 포함하고 있어 함께 수정합니다.
    public float calculateAverageSpeedFromIMU(List<Map<String, Object>> imuDataToProcess) {
        if (imuDataToProcess == null || imuDataToProcess.size() < 2) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터가 부족하여 평균 속도를 계산할 수 없습니다.");
            return 0.0f;
        }

        // 로컬 변수로 필터 상태 관리
        float currentPositionEstimateX = 0.0f, currentVelocityEstimateX = 0.0f;
        float currentPositionEstimateY = 0.0f, currentVelocityEstimateY = 0.0f;
        float currentVelocityVarianceX = 1.0f, currentPositionVarianceX = 1.0f;
        float currentVelocityVarianceY = 1.0f, currentPositionVarianceY = 1.0f;
        float[] currentGravity = {0.0f, 0.0f, 9.81f};

        float totalPathDistance = 0.0f;
        long firstTimestamp = -1, lastTimestamp = -1;
        float prevPositionX = 0.0f, prevPositionY = 0.0f;

        for (int i = 0; i < imuDataToProcess.size(); i++) {
            Map<String, Object> curr = imuDataToProcess.get(i);
            long timestamp = (long) curr.get("timestamp");

            if (i == 0) {
                firstTimestamp = timestamp;
            }
            lastTimestamp = timestamp;

            final float deltaTime = IMU_SAMPLE_INTERVAL_SEC;

            // 1. 센서 데이터 추출
            float[] currentLinearAcc = new float[3];
            float[] currentGyro = new float[3];
            float[] currentQuat = new float[4];

            boolean hasLinearAccel = getVector3fFromMap(curr, "linear_accel", currentLinearAcc);
            boolean hasGyro = getVector3fFromMap(curr, "gyro", currentGyro);
            boolean hasQuat = getQuaternionFromMap(curr, "rot", currentQuat);

            float[] accToRotate = new float[3];

            if (hasLinearAccel) {
                System.arraycopy(currentLinearAcc, 0, accToRotate, 0, 3);
            } else {
                float[] rawAcc = new float[3];
                if (getVector3fFromMap(curr, "accel", rawAcc)) {
                    float[] sensorGravity = new float[3];
                    if (getVector3fFromMap(curr, "gravity", sensorGravity)) {
                        accToRotate[0] = rawAcc[0] - sensorGravity[0];
                        accToRotate[1] = rawAcc[1] - sensorGravity[1];
                        accToRotate[2] = rawAcc[2] - sensorGravity[2];
                    } else {
                        currentGravity[0] = ALPHA * currentGravity[0] + (1 - ALPHA) * rawAcc[0];
                        currentGravity[1] = ALPHA * currentGravity[1] + (1 - ALPHA) * rawAcc[1];
                        currentGravity[2] = ALPHA * currentGravity[2] + (1 - ALPHA) * rawAcc[2];
                        accToRotate[0] = rawAcc[0] - currentGravity[0];
                        accToRotate[1] = rawAcc[1] - currentGravity[1];
                        accToRotate[2] = rawAcc[2] - currentGravity[2];
                    }
                } else {
                    continue;
                }
            }

            // 2. ~ 6. calculateIMUDistance와 동일한 로직 적용
            float[] globalLinearAcc = new float[3];
            if (hasQuat) {
                rotateVectorByQuaternion(accToRotate, currentQuat, globalLinearAcc);
                globalLinearAcc[2] = 0.0f;
            } else {
                globalLinearAcc[0] = 0.0f;
                globalLinearAcc[1] = 0.0f;
            }

            float accelX = globalLinearAcc[0];
            float accelY = globalLinearAcc[1];

            boolean isStationary = false;
            float linearAccMagnitude = (float) Math.sqrt(accToRotate[0] * accToRotate[0] + accToRotate[1] * accToRotate[1] + accToRotate[2] * accToRotate[2]);
            if (hasGyro) {
                float gyroMagnitude = (float) Math.sqrt(currentGyro[0] * currentGyro[0] + currentGyro[1] * currentGyro[1] + currentGyro[2] * currentGyro[2]);
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD && gyroMagnitude < STATIC_GYRO_THRESHOLD;
            } else {
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD;
            }

            currentVelocityEstimateX += accelX * deltaTime;
            currentPositionEstimateX += currentVelocityEstimateX * deltaTime;
            currentVelocityVarianceX += PROCESS_NOISE_VEL;
            currentPositionVarianceX += PROCESS_NOISE_POS + currentVelocityVarianceX * deltaTime * deltaTime;

            currentVelocityEstimateY += accelY * deltaTime;
            currentPositionEstimateY += currentVelocityEstimateY * deltaTime;
            currentVelocityVarianceY += PROCESS_NOISE_VEL;
            currentPositionVarianceY += PROCESS_NOISE_POS + currentVelocityVarianceY * deltaTime * deltaTime;

            if (isStationary) {
                float kalmanGainVX = currentVelocityVarianceX / (currentVelocityVarianceX + MEASUREMENT_NOISE_VEL);
                currentVelocityEstimateX += kalmanGainVX * (0.0f - currentVelocityEstimateX);
                currentVelocityVarianceX = (1 - kalmanGainVX) * currentVelocityVarianceX;

                float kalmanGainVY = currentVelocityVarianceY / (currentVelocityVarianceY + MEASUREMENT_NOISE_VEL);
                currentVelocityEstimateY += kalmanGainVY * (0.0f - currentVelocityEstimateY);
                currentVelocityVarianceY = (1 - kalmanGainVY) * currentVelocityVarianceY;
            }

            float deltaPos = (float) Math.sqrt(Math.pow(currentPositionEstimateX - prevPositionX, 2) + Math.pow(currentPositionEstimateY - prevPositionY, 2));
            totalPathDistance += deltaPos;

            prevPositionX = currentPositionEstimateX;
            prevPositionY = currentPositionEstimateY;
        }

        if (firstTimestamp == -1 || lastTimestamp == -1 || firstTimestamp == lastTimestamp) {
            return 0.0f;
        }

        // 총 시간 계산 시에는 시작과 끝의 timestamp를 사용
        float totalTimeSeconds = (lastTimestamp - firstTimestamp) / 1000.0f;
        // 데이터 포인트 개수 기반으로 총 시간 계산
        if (imuDataToProcess.size() > 1) {
            totalTimeSeconds = (imuDataToProcess.size() -1) * IMU_SAMPLE_INTERVAL_SEC;
        } else {
            return 0.0f;
        }

        if (totalTimeSeconds <= 0) {
            return 0.0f;
        }

        float averageSpeed = totalPathDistance / totalTimeSeconds;

        if (averageSpeed > MAX_REALISTIC_SPEED) {
            Timber.tag("MovementAnalyzer").w("계산된 평균 속도(" + averageSpeed + " m/s)가 비현실적입니다.");
        }

        Timber.tag("MovementAnalyzer").d("IMU 기반 평균 속도: " + averageSpeed + " m/s (거리: " + totalPathDistance + "m, 시간: " + totalTimeSeconds + "s)");
        return averageSpeed;
    }
}
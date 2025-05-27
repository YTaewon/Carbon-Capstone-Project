package com.example.myapplication12345.AI;

import java.util.List;
import java.util.Map;
import timber.log.Timber;

public class MovementAnalyzer {
    private final List<Map<String, Object>> gpsData; // 보조 데이터 (선택적)
    private final List<Map<String, Object>> imuData; // 주 데이터

    private float finalDistance;

    // 칼만 필터 변수 (속도와 위치 추정용 - 2D (X, Y) 추적을 위해 확장)
    // 각 축(X, Y)에 대한 필터 상태를 별도로 관리
    private float positionEstimateX = 0.0f; // 지구 좌표계 X축 위치 추정치 (m)
    private float velocityEstimateX = 0.0f; // 지구 좌표계 X축 속도 추정치 (m/s)
    private float positionEstimateY = 0.0f; // 지구 좌표계 Y축 위치 추정치 (m)
    private float velocityEstimateY = 0.0f; // 지구 좌표계 Y축 속도 추정치 (m/s)

    private float velocityVarianceX = 1.0f; // 속도 분산 (X축)
    private float positionVarianceX = 1.0f; // 위치 분산 (X축)
    private float velocityVarianceY = 1.0f; // 속도 분산 (Y축)
    private float positionVarianceY = 1.0f; // 위치 분산 (Y축)

    // 칼만 필터 노이즈 파라미터 (튜닝 필요)
    private static final float PROCESS_NOISE_ACCEL = 0.1f; // 가속도 측정 불확실성 (프로세스 노이즈, 크면 필터가 측정값을 더 믿음)
    private static final float PROCESS_NOISE_VEL = 0.05f; // 속도 프로세스 노이즈 (작으면 필터가 추정치를 더 믿음)
    private static final float PROCESS_NOISE_POS = 0.01f; // 위치 프로세스 노이즈

    private static final float MEASUREMENT_NOISE_VEL = 0.5f; // 속도 측정 노이즈 (ZVU용, 작으면 0m/s 측정치를 강하게 믿음)
    private static final float MEASUREMENT_NOISE_POS = 2.0f; // 위치 측정 노이즈 (GPS 사용 시, 작으면 GPS를 강하게 믿음)

    // 중력 제거를 위한 저역통과 필터 변수 (linear_accel 데이터가 없는 경우의 폴백)
    private float[] gravity = {0.0f, 0.0f, 9.81f}; // 초기 중력 벡터 (m/s^2)
    private static final float ALPHA = 0.8f; // 저역통과 필터 상수

    // 비정상 거리/속도 검증 상수
    private static final float MAX_REALISTIC_SPEED = 50.0f; // 50 m/s (180 km/h)
    private static final float MIN_DISTANCE_THRESHOLD = 0.1f; // 최소 거리 임계값 (0.1m)

    // 정지 감지(ZVU)를 위한 임계값 (튜닝 필요)
    private static final float STATIC_LINEAR_ACCEL_THRESHOLD = 0.4f; // m/s^2 (이하일 때 정지 가능성 높음)
    private static final float STATIC_GYRO_THRESHOLD = 0.2f; // rad/s (이하일 때 회전 없음)

    public MovementAnalyzer(List<Map<String, Object>> gpsData, List<Map<String, Object>> imuData) {
        this.gpsData = gpsData;
        this.imuData = imuData;
        this.finalDistance = 0.0f;
    }

    /**
     * IMU 데이터를 활용한 거리 계산 (칼만 필터, 지구 좌표계, 조건부 ZVU 적용).
     * `linear_accel`, `rot` (쿼터니언), `gyro` 데이터 사용을 전제로 함.
     *
     * @return 총 이동 거리 (m)
     */
    public float calculateIMUDistance() {
        // 필터 상태 초기화 (메서드 호출마다 독립적인 계산을 위함)
        positionEstimateX = 0.0f;
        velocityEstimateX = 0.0f;
        positionEstimateY = 0.0f;
        velocityEstimateY = 0.0f;
        velocityVarianceX = 1.0f;
        positionVarianceX = 1.0f;
        velocityVarianceY = 1.0f;
        positionVarianceY = 1.0f;
        gravity = new float[]{0.0f, 0.0f, 9.81f}; // Fallback 용도 초기화

        float totalPathDistance = 0.0f; // 누적 경로 거리
        long prevTime = 0;
        float prevPositionX = 0.0f; // 이전 스텝의 위치 (경로 거리 계산용)
        float prevPositionY = 0.0f;

        if (imuData == null || imuData.isEmpty()) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터 없음 (calculateIMUDistance)");
            return totalPathDistance;
        }

        // 첫 번째 데이터의 타임스탬프를 시작 시간으로 기록 (totalTime 계산용)
        long startTime = (long) imuData.get(0).get("timestamp");

        for (int i = 0; i < imuData.size(); i++) {
            Map<String, Object> curr = imuData.get(i);
            long timestamp = (long) curr.get("timestamp");

            if (i == 0) {
                prevTime = timestamp;
                // 초기 위치를 0으로 설정했으므로 prevPosition도 0으로 시작
                continue;
            }

            float deltaTime = (timestamp - prevTime) / 1000.0f; // 가정: timestamp는 밀리초(ms) 단위
            if (deltaTime <= 0) { // 시간 간격이 없거나 잘못된 경우 건너뛰기
                prevTime = timestamp;
                continue;
            }
            prevTime = timestamp;

            // 1. 센서 데이터 추출 (null-safe)
            float[] currentLinearAcc = new float[3];
            float[] currentGyro = new float[3];
            float[] currentQuat = new float[4]; // w, x, y, z

            boolean hasLinearAccel = getFloatArrayFromMap(curr, "linear_accel.x", "linear_accel.y", "linear_accel.z", currentLinearAcc);
            boolean hasGyro = getFloatArrayFromMap(curr, "gyro.x", "gyro.y", "gyro.z", currentGyro);
            boolean hasQuat = getFloatArrayFromMap(curr, "rot.w", "rot.x", "rot.y", "rot.z", currentQuat);

            float[] accToRotate = new float[3]; // 회전시킬 가속도 벡터 (장치 좌표계)

            // linear_accel 데이터를 우선적으로 사용
            if (hasLinearAccel) {
                accToRotate[0] = currentLinearAcc[0];
                accToRotate[1] = currentLinearAcc[1];
                accToRotate[2] = currentLinearAcc[2];
            } else {
                // linear_accel이 없다면 raw accel에서 중력 제거 시도 (fallback)
                float[] rawAcc = new float[3];
                boolean hasRawAcc = getFloatArrayFromMap(curr, "accel.x", "accel.y", "accel.z", rawAcc);

                if (hasRawAcc) {
                    float[] sensorGravity = new float[3];
                    // gravity 센서 데이터가 있다면 사용
                    if (getFloatArrayFromMap(curr, "gravity.x", "gravity.y", "gravity.z", sensorGravity)) {
                        accToRotate[0] = rawAcc[0] - sensorGravity[0];
                        accToRotate[1] = rawAcc[1] - sensorGravity[1];
                        accToRotate[2] = rawAcc[2] - sensorGravity[2];
                    } else {
                        // gravity 센서도 없다면 기존 LPF 중력 제거 폴백
                        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * rawAcc[0];
                        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * rawAcc[1];
                        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * rawAcc[2];
                        accToRotate[0] = rawAcc[0] - gravity[0];
                        accToRotate[1] = rawAcc[1] - gravity[1];
                        accToRotate[2] = rawAcc[2] - gravity[2];
                    }
                } else {
                    // 가속도 데이터 자체가 없는 경우 (이런 일은 거의 없겠지만)
                    Timber.tag("MovementAnalyzer").w("가속도 데이터 없음. 건너뛰기.");
                    continue;
                }
            }

            // 2. 가속도 벡터를 지구 좌표계(Global Frame)로 변환
            float[] globalLinearAcc = new float[3]; // 변환된 지구 좌표계 가속도
            if (hasQuat) {
                // 쿼터니언이 있다면 정확하게 회전
                rotateVectorByQuaternion(accToRotate, currentQuat, globalLinearAcc);
                // 중력 벡터가 Z축으로 약 9.81인 상태 (기기가 움직이지 않을 때)
                // 지구 좌표계 가속도에서 수직 성분 (Z)은 수평 이동 거리 계산에 영향을 주지 않으므로 무시
                // 다만, 기기가 위로 움직이는 등의 수직 이동이 필요한 경우 globalLinearAcc[2]도 사용
                globalLinearAcc[2] = 0.0f; // 수직 가속도 성분 무시
            } else {
                // 쿼터니언이 없다면 2D 위치 추적은 불가능함.
                // 이 경우 기존처럼 가속도 크기만을 사용하여 총 이동 거리를 누적하거나, 0으로 처리하는 것이 합리적.
                // 여기서는 2D 추적의 의미를 살리기 위해 경고를 남기고 ZVU와 유사하게 처리 (움직임 없다고 가정)
                Timber.tag("MovementAnalyzer").w("쿼터니언 데이터 없음. 정확한 2D IMU 추적 불가.");
                globalLinearAcc[0] = 0.0f;
                globalLinearAcc[1] = 0.0f;
                globalLinearAcc[2] = 0.0f;
            }

            // 지구 좌표계 가속도 (수평 성분)
            float accelX = globalLinearAcc[0];
            float accelY = globalLinearAcc[1];

            // 3. 정지 감지 (ZVU 조건)
            boolean isStationary = false;
            float linearAccMagnitude = (float) Math.sqrt(accToRotate[0] * accToRotate[0] +
                    accToRotate[1] * accToRotate[1] +
                    accToRotate[2] * accToRotate[2]);
            if (hasGyro) {
                float gyroMagnitude = (float) Math.sqrt(currentGyro[0] * currentGyro[0] +
                        currentGyro[1] * currentGyro[1] +
                        currentGyro[2] * currentGyro[2]);
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD &&
                        gyroMagnitude < STATIC_GYRO_THRESHOLD;
            } else { // 자이로가 없다면 선형 가속도만으로 판단
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD;
            }

            // 4. 칼만 필터 예측 (Predict)
            // X축
            velocityEstimateX += accelX * deltaTime;
            positionEstimateX += velocityEstimateX * deltaTime;
            velocityVarianceX += PROCESS_NOISE_VEL;
            positionVarianceX += PROCESS_NOISE_POS + velocityVarianceX * deltaTime * deltaTime;

            // Y축
            velocityEstimateY += accelY * deltaTime;
            positionEstimateY += velocityEstimateY * deltaTime;
            velocityVarianceY += PROCESS_NOISE_VEL;
            positionVarianceY += PROCESS_NOISE_POS + velocityVarianceY * deltaTime * deltaTime;

            // 5. 칼만 필터 갱신 (Update) - ZVU (Zero Velocity Update) 및 GPS 보정
            // ZVU (정지 상태일 때 속도를 0으로 보정)
            if (isStationary) {
                // X축 속도 보정
                float kalmanGainVX = velocityVarianceX / (velocityVarianceX + MEASUREMENT_NOISE_VEL);
                velocityEstimateX += kalmanGainVX * (0.0f - velocityEstimateX); // 측정값 0으로 보정
                velocityVarianceX = (1 - kalmanGainVX) * velocityVarianceX;

                // Y축 속도 보정
                float kalmanGainVY = velocityVarianceY / (velocityVarianceY + MEASUREMENT_NOISE_VEL);
                velocityEstimateY += kalmanGainVY * (0.0f - velocityEstimateY); // 측정값 0으로 보정
                velocityVarianceY = (1 - kalmanGainVY) * velocityVarianceY;
            }

            // 6. 각 스텝에서의 이동 거리 계산 및 누적 (경로 길이)
            // (prevPositionX, prevPositionY)에서 (positionEstimateX, positionEstimateY)까지의 거리
            float deltaPos = (float) Math.sqrt(
                    Math.pow(positionEstimateX - prevPositionX, 2) +
                            Math.pow(positionEstimateY - prevPositionY, 2)
            );
            totalPathDistance += deltaPos;

            prevPositionX = positionEstimateX;
            prevPositionY = positionEstimateY;
        }

        // 최종 거리 검증
        this.finalDistance = totalPathDistance;
        long endTime = (long) imuData.get(imuData.size() - 1).get("timestamp");
        float totalTime = (endTime - startTime) / 1000.0f; // 총 시간 계산 (밀리초 가정)

        if (this.finalDistance < 0) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 음수 거리 %s", this.finalDistance);
            this.finalDistance = 0.0f;
        } else if (totalTime > 1.0f && this.finalDistance < MIN_DISTANCE_THRESHOLD ) { // 1초 이상 움직였는데 거리가 매우 작으면 0으로 처리
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 너무 작은 거리 " + this.finalDistance + " (시간: " + totalTime + "s)");
            this.finalDistance = 0.0f;
        } else if (totalTime > 0 && (this.finalDistance / totalTime) > MAX_REALISTIC_SPEED) {
            Timber.tag("MovementAnalyzer").w("비정상 거리 감지: 비현실적인 속도 " + (this.finalDistance / totalTime) + " m/s");
            this.finalDistance = 0.0f; // 비현실적인 속도의 경우 0으로 처리
        }

        Timber.tag("MovementAnalyzer").d("IMU Total Path Distance: " + totalPathDistance + " meters");
        Timber.tag("MovementAnalyzer").d("Final Distance (after validation): " + this.finalDistance + " meters");
        return this.finalDistance;
    }

    // Haversine 공식으로 GPS 거리 계산 (보조용) - 기존과 동일
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

    // 특정 시점에서의 GPS 기반 거리 추정 (IMU 보정용) - 기존과 동일, XY 보정에는 별도의 로직 필요
    private float estimateGPSDistanceAtTime(long targetTime) {
        if (gpsData == null || gpsData.size() < 2) return -1.0f;

        float totalDistance = 0.0f;

        for (int i = 1; i < gpsData.size(); i++) {
            Map<String, Object> prevGps = gpsData.get(i - 1);
            Map<String, Object> currGps = gpsData.get(i);

            long currGpsTimestamp = (long) currGps.get("timestamp");
            // targetTime에 도달했거나 넘어섰으면 중단 (이전까지의 누적 거리 반환)
            if (currGpsTimestamp > targetTime) {
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
            // currGpsTimestamp가 targetTime을 넘어서면 중단
            if (currGpsTimestamp >= targetTime) break;
        }
        return totalDistance > 0 ? totalDistance : -1.0f;
    }

    /**
     * Map에서 float 배열 값들을 안전하게 가져오는 헬퍼 메서드 (3개 요소).
     * @return 모든 키가 성공적으로 파싱되면 true, 그렇지 않으면 false.
     */
    private boolean getFloatArrayFromMap(Map<String, Object> map, String key1, String key2, String key3, float[] outArray) {
        Number num1 = (Number) map.get(key1);
        Number num2 = (Number) map.get(key2);
        Number num3 = (Number) map.get(key3);
        if (num1 != null && num2 != null && num3 != null) {
            outArray[0] = num1.floatValue();
            outArray[1] = num2.floatValue();
            outArray[2] = num3.floatValue();
            return true;
        }
        return false;
    }

    /**
     * Map에서 float 배열 값들을 안전하게 가져오는 헬퍼 메서드 (4개 요소, 쿼터니언용).
     * @return 모든 키가 성공적으로 파싱되면 true, 그렇지 않으면 false.
     */
    private boolean getFloatArrayFromMap(Map<String, Object> map, String key0, String key1, String key2, String key3, float[] outArray) {
        Number num0 = (Number) map.get(key0);
        Number num1 = (Number) map.get(key1);
        Number num2 = (Number) map.get(key2);
        Number num3 = (Number) map.get(key3);
        if (num0 != null && num1 != null && num2 != null && num3 != null) {
            outArray[0] = num0.floatValue(); // w
            outArray[1] = num1.floatValue(); // x
            outArray[2] = num2.floatValue(); // y
            outArray[3] = num3.floatValue(); // z
            return true;
        }
        return false;
    }

    /**
     * 쿼터니언으로 벡터를 회전시켜 지구 좌표계로 변환합니다.
     * q_rot = [w, x, y, z]
     * v_body = [vx, vy, vz]
     * v_global = q_rot * [0, vx, vy, vz] * q_rot_inverse
     * 여기서 q_rot_inverse = [w, -x, -y, -z] (단위 쿼터니언의 경우)
     *
     * @param vectorToRotate 장치 좌표계의 벡터 (예: 선형 가속도)
     * @param quaternion 기기 방향을 나타내는 쿼터니언 [w, x, y, z]
     * @param rotatedVector 결과 벡터 (지구 좌표계) - 배열 크기 3
     */
    private void rotateVectorByQuaternion(float[] vectorToRotate, float[] quaternion, float[] rotatedVector) {
        // 쿼터니언 성분 (q = w + xi + yj + zk)
        float q0 = quaternion[0]; // w
        float q1 = quaternion[1]; // x
        float q2 = quaternion[2]; // y
        float q3 = quaternion[3]; // z

        // 벡터 성분
        float vx = vectorToRotate[0];
        float vy = vectorToRotate[1];
        float vz = vectorToRotate[2];

        // 쿼터니언-벡터 곱셈 (q_rot * v_body_quat)
        // v_body_quat = [0, vx, vy, vz]
        float tw = -q1 * vx - q2 * vy - q3 * vz;
        float tvx = q0 * vx + q2 * vz - q3 * vy;
        float tvy = q0 * vy + q3 * vx - q1 * vz;
        float tvz = q0 * vz + q1 * vy - q2 * vx;

        // 결과 쿼터니언 * q_rot_inverse
        // q_rot_inverse = [q0, -q1, -q2, -q3]
        rotatedVector[0] = tw * (-q1) + tvx * q0 - tvy * q3 + tvz * q2;
        rotatedVector[1] = tw * (-q2) + tvy * q0 - tvz * q1 + tvx * q3;
        rotatedVector[2] = tw * (-q3) + tvz * q0 - tvx * q2 + tvy * q1;
    }


    // 분석 메서드 (IMU 위주로 거리 계산)
    // 이 메서드는 calculateIMUDistance()를 호출하여 거리 계산을 수행합니다.
    public void analyze() {
        // analyze 호출 시 필터 변수들 초기화 (calculateIMUDistance 내부에서 수행되므로 여기서는 생략 가능)
        // 하지만 analyze 메서드가 직접 거리를 계산하고 finalDistance에 할당해야 한다면, 초기화 로직을 이쪽으로 옮길 수 있습니다.
        // 현재는 calculateIMUDistance()가 독립적으로 초기화하므로 그대로 둡니다.

        float imuDistance = calculateIMUDistance(); // 갱신된 로직 사용
        this.finalDistance = imuDistance; // calculateIMUDistance 내부에서 최종 검증까지 수행하여 할당됨

        // 추가적인 분석 로직이 필요하다면 여기에 구현 (예: GPS와 IMU 결과 비교, 통계 등)
        Timber.tag("MovementAnalyzer").d("Analysis completed. Final Distance: " + this.finalDistance + " meters");
    }

    // Getter 메서드
    public float getDistance() {
        return this.finalDistance;
    }

    /**
     * IMU 데이터만을 사용하여 평균 속도를 계산합니다.
     * 이 메서드는 클래스 멤버 변수(필터 상태)를 변경하지 않고, 로컬 변수만을 사용합니다.
     * 여기서는 calculateIMUDistance와 동일한 로직을 사용하여 누적 경로 거리를 계산하고 평균을 냅니다.
     * @param imuDataToProcess 사용할 IMU 데이터 리스트
     * @return 계산된 평균 속도 (m/s). 데이터가 부족하거나 시간이 0이면 0.0f 반환.
     */
    public float calculateAverageSpeedFromIMU(List<Map<String, Object>> imuDataToProcess) {
        if (imuDataToProcess == null || imuDataToProcess.size() < 2) {
            Timber.tag("MovementAnalyzer").w("IMU 데이터가 부족하여 평균 속도를 계산할 수 없습니다.");
            return 0.0f;
        }

        // 이 메서드 내에서 사용할 로컬 칼만 필터 변수 및 중력 변수
        float currentPositionEstimateX = 0.0f;
        float currentVelocityEstimateX = 0.0f;
        float currentPositionEstimateY = 0.0f;
        float currentVelocityEstimateY = 0.0f;

        float currentVelocityVarianceX = 1.0f;
        float currentPositionVarianceX = 1.0f;
        float currentVelocityVarianceY = 1.0f;
        float currentPositionVarianceY = 1.0f;

        float[] currentGravity = {0.0f, 0.0f, 9.81f};

        float totalPathDistance = 0.0f;
        long firstTimestamp = -1;
        long lastTimestamp = -1;
        long prevTime = 0;
        float prevPositionX = 0.0f;
        float prevPositionY = 0.0f;


        for (int i = 0; i < imuDataToProcess.size(); i++) {
            Map<String, Object> curr = imuDataToProcess.get(i);
            long timestamp = (long) curr.get("timestamp");

            if (i == 0) {
                firstTimestamp = timestamp;
                prevTime = timestamp;
                continue;
            }
            lastTimestamp = timestamp;

            float deltaTime = (timestamp - prevTime) / 1000.0f;
            if (deltaTime <= 0) {
                prevTime = timestamp;
                continue;
            }
            prevTime = timestamp;

            // 1. 센서 데이터 추출
            float[] currentLinearAcc = new float[3];
            float[] currentGyro = new float[3];
            float[] currentQuat = new float[4];

            boolean hasLinearAccel = getFloatArrayFromMap(curr, "linear_accel.x", "linear_accel.y", "linear_accel.z", currentLinearAcc);
            boolean hasGyro = getFloatArrayFromMap(curr, "gyro.x", "gyro.y", "gyro.z", currentGyro);
            boolean hasQuat = getFloatArrayFromMap(curr, "rot.w", "rot.x", "rot.y", "rot.z", currentQuat);

            float[] accToRotate = new float[3];

            if (hasLinearAccel) {
                accToRotate[0] = currentLinearAcc[0];
                accToRotate[1] = currentLinearAcc[1];
                accToRotate[2] = currentLinearAcc[2];
            } else {
                float[] rawAcc = new float[3];
                boolean hasRawAcc = getFloatArrayFromMap(curr, "accel.x", "accel.y", "accel.z", rawAcc);
                if (hasRawAcc) {
                    float[] sensorGravity = new float[3];
                    if (getFloatArrayFromMap(curr, "gravity.x", "gravity.y", "gravity.z", sensorGravity)) {
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

            // 2. 가속도 벡터를 지구 좌표계(Global Frame)로 변환
            float[] globalLinearAcc = new float[3];
            if (hasQuat) {
                rotateVectorByQuaternion(accToRotate, currentQuat, globalLinearAcc);
                globalLinearAcc[2] = 0.0f; // 수직 가속도 성분 무시
            } else {
                globalLinearAcc[0] = 0.0f;
                globalLinearAcc[1] = 0.0f;
                globalLinearAcc[2] = 0.0f;
            }

            float accelX = globalLinearAcc[0];
            float accelY = globalLinearAcc[1];

            // 3. 정지 감지 (ZVU 조건)
            boolean isStationary = false;
            float linearAccMagnitude = (float) Math.sqrt(accToRotate[0] * accToRotate[0] +
                    accToRotate[1] * accToRotate[1] +
                    accToRotate[2] * accToRotate[2]);
            if (hasGyro) {
                float gyroMagnitude = (float) Math.sqrt(currentGyro[0] * currentGyro[0] +
                        currentGyro[1] * currentGyro[1] +
                        currentGyro[2] * currentGyro[2]);
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD &&
                        gyroMagnitude < STATIC_GYRO_THRESHOLD;
            } else {
                isStationary = linearAccMagnitude < STATIC_LINEAR_ACCEL_THRESHOLD;
            }

            // 4. 칼만 필터 예측 (Predict)
            currentVelocityEstimateX += accelX * deltaTime;
            currentPositionEstimateX += currentVelocityEstimateX * deltaTime;
            currentVelocityVarianceX += PROCESS_NOISE_VEL;
            currentPositionVarianceX += PROCESS_NOISE_POS + currentVelocityVarianceX * deltaTime * deltaTime;

            currentVelocityEstimateY += accelY * deltaTime;
            currentPositionEstimateY += currentVelocityEstimateY * deltaTime;
            currentPositionVarianceY += PROCESS_NOISE_POS + currentVelocityVarianceY * deltaTime * deltaTime;
            currentVelocityVarianceY += PROCESS_NOISE_VEL;

            // 5. 칼만 필터 갱신 (Update) - ZVU
            if (isStationary) {
                float kalmanGainVX = currentVelocityVarianceX / (currentVelocityVarianceX + MEASUREMENT_NOISE_VEL);
                currentVelocityEstimateX += kalmanGainVX * (0.0f - currentVelocityEstimateX);
                currentVelocityVarianceX = (1 - kalmanGainVX) * currentVelocityVarianceX;

                float kalmanGainVY = currentVelocityVarianceY / (currentVelocityVarianceY + MEASUREMENT_NOISE_VEL);
                currentVelocityEstimateY += kalmanGainVY * (0.0f - currentVelocityEstimateY);
                currentVelocityVarianceY = (1 - kalmanGainVY) * currentVelocityVarianceY;
            }

            // 6. 각 스텝에서의 이동 거리 계산 및 누적 (경로 길이)
            float deltaPos = (float) Math.sqrt(
                    Math.pow(currentPositionEstimateX - prevPositionX, 2) +
                            Math.pow(currentPositionEstimateY - prevPositionY, 2)
            );
            totalPathDistance += deltaPos;

            prevPositionX = currentPositionEstimateX;
            prevPositionY = currentPositionEstimateY;
        }

        if (firstTimestamp == -1 || lastTimestamp == -1 || firstTimestamp == lastTimestamp) {
            Timber.tag("MovementAnalyzer").w("유효한 시간 간격을 계산할 수 없어 평균 속도를 0으로 반환합니다.");
            return 0.0f;
        }

        float totalTimeSeconds = (lastTimestamp - firstTimestamp) / 1000.0f;

        if (totalTimeSeconds <= 0) {
            Timber.tag("MovementAnalyzer").w("총 이동 시간이 0 또는 음수이므로 평균 속도를 계산할 수 없습니다.");
            return 0.0f;
        }

        float averageSpeed = totalPathDistance / totalTimeSeconds;

        // 비정상 속도 검증 (옵션)
        if (averageSpeed > MAX_REALISTIC_SPEED) {
            Timber.tag("MovementAnalyzer").w("계산된 평균 속도(" + averageSpeed + " m/s)가 비현실적입니다. IMU 데이터에 노이즈가 많을 수 있습니다.");
        }

        Timber.tag("MovementAnalyzer").d("IMU 기반 평균 속도: " + averageSpeed + " m/s (거리: " + totalPathDistance + "m, 시간: " + totalTimeSeconds + "s)");
        return averageSpeed;
    }
}
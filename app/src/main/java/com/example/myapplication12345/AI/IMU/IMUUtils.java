package com.example.myapplication12345.AI.IMU;

import java.util.HashMap;
import java.util.Map;

public class IMUUtils {

    /**
     * Quaternion을 사용하여 축 회전 (Python 방식에 맞춤)
     * rotation: [num_windows][window_size][4]
     * 파이썬 cut_imu 결과 기반 가정: channel 0=rot.x, 1=rot.y, 2=rot.z, 3=rot.w
     * 파이썬 rotate_axis_python 내부 할당 및 공식 사용 순서:
     *   q_x (쿼터니언 x) = rotation[..., 0] (즉, rot.x 값)
     *   q_y (쿼터니언 y) = rotation[..., 1] (즉, rot.y 값)
     *   q_z (쿼터니언 z) = rotation[..., 2] (즉, rot.z 값)
     *   q_w (쿼터니언 w) = rotation[..., 3] (즉, rot.w 값)
     * ax, ay, az: [num_windows][window_size]
     * 반환: result[0]=rotated_ax, result[1]=rotated_ay, result[2]=rotated_az
     */
    // IMUUtils.java

    public static double[][][] rotateAxis(double[][] ax_input, double[][] ay_input, double[][] az_input, double[][][] rotationQuaternion_input) {
        if (ax_input == null || ay_input == null || az_input == null || rotationQuaternion_input == null) {
            throw new IllegalArgumentException("Input arrays cannot be null.");
        }
        // ... (입력 배열 shape 검증 로직은 이전과 동일하게 유지) ...
        int numWindows = ax_input.length;
        if (numWindows == 0) return new double[3][0][0];
        int windowSize = ax_input[0].length;

        double[][][] result = new double[3][numWindows][windowSize];

        // 파이썬 코드는 먼저 모든 데이터를 1D로 ravel 한 후 계산하고 다시 reshape 합니다.
        // 자바에서도 유사하게 하거나, 윈도우/샘플 단위로 직접 계산할 수 있습니다.
        // 여기서는 윈도우/샘플 단위로 직접 계산하는 방식을 유지하되, 파이썬의 ravel 효과를 모방합니다.
        // (실제로는 입력 ax,ay,az,rotationQuaternion이 이미 (num_windows, window_size, channels) 형태이므로
        //  ravel/reshape은 파이썬 코드의 특성일 뿐, 최종 계산은 각 샘플별로 이루어집니다.)

        for (int i = 0; i < numWindows; i++) {
            for (int j = 0; j < windowSize; j++) {
                double qx = rotationQuaternion_input[i][j][0]; // Python의 변수 x (쿼터니언 x)
                double qy = rotationQuaternion_input[i][j][1]; // Python의 변수 y (쿼터니언 y)
                double qz = rotationQuaternion_input[i][j][2]; // Python의 변수 z (쿼터니언 z)
                double qw = rotationQuaternion_input[i][j][3]; // Python의 변수 w (쿼터니언 w, 스칼라)

                // 입력 벡터 (회전 대상)
                double current_ax = ax_input[i][j]; // Python의 변수 ax
                double current_ay = ay_input[i][j]; // Python의 변수 ay
                double current_az = az_input[i][j]; // Python의 변수 az

                // ❗❗❗ 파이썬 코드의 회전 공식과 정확히 일치하도록 수정 ❗❗❗
                result[0][i][j] = ( // _ax
                        1.0 // 이 '1'이 어디서 왔는지, 의도된 것인지 확인 필요. 표준 공식에는 없음.
                                - 2.0 * (qy*qy + qz*qz) * current_ax // ax가 괄호 안에 있음
                                + 2.0 * (qx * qy - qw * qz) * current_ay
                                + 2.0 * (qx * qz + qw * qy) * current_az
                );

                result[1][i][j] = ( // _ay
                        2.0 * (qx * qy + qw * qz) * current_ax
                                + 1.0 // 이 '1'
                                - 2.0 * (qx*qx + qz*qz) * current_ay // ay가 괄호 안에 있음
                                + 2.0 * (qy * qz - qw * qx) * current_az
                );

                result[2][i][j] = ( // _az
                        2.0 * (qx * qz - qw * qy) * current_ax
                                + 2.0 * (qy * qz + qw * qx) * current_ay
                                + 1.0 // 이 '1'
                                - 2.0 * (qx*qx + qy*qy) * current_az // az가 괄호 안에 있음
                );
            }
        }
        return result;
    }

    /**
     * 중력 벡터를 이용하여 각도(theta) 계산 (Python 방식에 맞춤)
     * lx, ly, lz: [num_windows][window_size]
     * gravity: [num_windows][window_size][3] (gx, gy, gz)
     */
    public static double[][] calculateAngle(double[][] lx, double[][] ly, double[][] lz, double[][][] gravity) {
        if (lx == null || ly == null || lz == null || gravity == null) {
            throw new IllegalArgumentException("Input arrays cannot be null for calculateAngle.");
        }
        int numWindows = lx.length;
        if (numWindows == 0) return new double[0][0];
        int windowSize = (lx[0] != null) ? lx[0].length : 0;
        if (windowSize == 0 && numWindows > 0) return new double[numWindows][0];

        // 입력 배열들의 shape 일관성 검사
        if (!(ly.length == numWindows && lz.length == numWindows && gravity.length == numWindows)) {
            throw new IllegalArgumentException("Input arrays must have the same number of windows for calculateAngle.");
        }
        for(int i=0; i<numWindows; ++i) {
            if (lx[i] == null || ly[i] == null || lz[i] == null || gravity[i] == null ||
                    lx[i].length != windowSize || ly[i].length != windowSize || lz[i].length != windowSize || gravity[i].length != windowSize) {
                throw new IllegalArgumentException("Window sizes must be consistent across all input arrays for window " + i + " in calculateAngle.");
            }
            for(int j=0; j<windowSize; ++j) {
                if (gravity[i][j] == null || gravity[i][j].length < 3) {
                    throw new IllegalArgumentException("Gravity data for window " + i + ", sample " + j + " is null or has insufficient channels.");
                }
            }
        }


        double[][] theta = new double[numWindows][windowSize];

        for (int i = 0; i < numWindows; i++) {
            for (int j = 0; j < windowSize; j++) {
                // Python의 np.nan_to_num 과 동일하게 처리
                double gx = Double.isNaN(gravity[i][j][0]) ? 0.0 : gravity[i][j][0];
                double gy = Double.isNaN(gravity[i][j][1]) ? 0.0 : gravity[i][j][1];
                double gz = Double.isNaN(gravity[i][j][2]) ? 0.0 : gravity[i][j][2];
                double currentLx = Double.isNaN(lx[i][j]) ? 0.0 : lx[i][j];
                double currentLy = Double.isNaN(ly[i][j]) ? 0.0 : ly[i][j];
                double currentLz = Double.isNaN(lz[i][j]) ? 0.0 : lz[i][j];

                double dotProduct = gx * currentLx + gy * currentLy + gz * currentLz;
                double magG = Math.sqrt(gx * gx + gy * gy + gz * gz);
                double magL = Math.sqrt(currentLx * currentLx + currentLy * currentLy + currentLz * currentLz);

                double denominator = magG * magL;
                // Python np.where(d == 0, 0.000001, d) 와 유사하게 매우 작은 값 처리
                if (Math.abs(denominator) < 1e-9) {
                    denominator = 1e-6; // Python과 동일한 값으로 설정
                }

                double cosTheta = dotProduct / denominator;
                // 파이썬 np.clip(cos_theta, -1.0, 1.0) 과 동일한 효과
                cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta));
                theta[i][j] = Math.acos(cosTheta);
            }
        }
        return theta;
    }

    /**
     * 배열 차분 계산 (Python np.diff(array, axis=1)과 동일하게)
     * 입력 array: [num_rows][num_cols]
     * 반환: Map<String, double[][]> {"difference": [num_rows][num_cols-1]}
     */
    public static Map<String, double[][]> diff(double[][] array) {
        if (array == null) throw new IllegalArgumentException("Input array cannot be null for diff.");
        int numRows = array.length;
        if (numRows == 0) { // 빈 배열에 대한 처리
            Map<String, double[][]> resultMap = new HashMap<>(1);
            resultMap.put("difference", new double[0][0]);
            return resultMap;
        }
        int numCols = (array[0] != null) ? array[0].length : 0;
        if (numCols < 2) { // 차분을 계산할 수 없는 경우
            Map<String, double[][]> resultMap = new HashMap<>(1);
            resultMap.put("difference", new double[numRows][0]);
            return resultMap;
        }

        double[][] diffArray = new double[numRows][numCols - 1];
        for (int i = 0; i < numRows; i++) {
            if (array[i] == null || array[i].length != numCols) {
                throw new IllegalArgumentException("Row " + i + " has inconsistent column count or is null.");
            }
            for (int j = 0; j < numCols - 1; j++) {
                diffArray[i][j] = array[i][j + 1] - array[i][j];
            }
        }

        Map<String, double[][]> resultMap = new HashMap<>(1);
        resultMap.put("difference", diffArray);
        return resultMap;
    }
}
package com.example.myapplication12345.AI.IMU;

import java.util.HashMap;
import java.util.Map;

public class IMUUtils {

    public static double[][][] rotateAxis(double[][] ax_input, double[][] ay_input, double[][] az_input, double[][][] rotationQuaternion_input) {
        if (ax_input == null || ay_input == null || az_input == null || rotationQuaternion_input == null) {
            throw new IllegalArgumentException("Input arrays cannot be null for rotateAxis.");
        }
        int numWindows = ax_input.length;
        if (numWindows == 0) return new double[3][0][0];
        int windowSize = ax_input[0].length;

        double[][][] result = new double[3][numWindows][windowSize];

        for (int i = 0; i < numWindows; i++) {
            for (int j = 0; j < windowSize; j++) {
                double qx = rotationQuaternion_input[i][j][0];
                double qy = rotationQuaternion_input[i][j][1];
                double qz = rotationQuaternion_input[i][j][2];
                double qw = rotationQuaternion_input[i][j][3];

                // 입력 벡터 (회전 대상)
                double current_ax = ax_input[i][j];
                double current_ay = ay_input[i][j];
                double current_az = az_input[i][j];

                // --- 회전 공식 ---
                result[0][i][j] = (
                        1.0
                                - 2.0 * (qy*qy + qz*qz) * current_ax
                                + 2.0 * (qx * qy - qw * qz) * current_ay
                                + 2.0 * (qx * qz + qw * qy) * current_az
                );

                result[1][i][j] = (
                        2.0 * (qx * qy + qw * qz) * current_ax
                                + 1.0
                                - 2.0 * (qx*qx + qz*qz) * current_ay
                                + 2.0 * (qy * qz - qw * qx) * current_az
                );

                result[2][i][j] = (
                        2.0 * (qx * qz - qw * qy) * current_ax
                                + 2.0 * (qy * qz + qw * qx) * current_ay
                                + 1.0
                                - 2.0 * (qx*qx + qy*qy) * current_az
                );

            }
        }
        return result;
    }

    public static double[][] calculateAngle(double[][] lx, double[][] ly, double[][] lz, double[][][] gravity) {
        if (lx == null || ly == null || lz == null || gravity == null) {
            throw new IllegalArgumentException("Input arrays cannot be null for calculateAngle.");
        }
        int numWindows = lx.length;
        if (numWindows == 0) return new double[0][0];
        int windowSize = (lx[0] != null) ? lx[0].length : 0;
        if (windowSize == 0) return new double[numWindows][0];

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
                // --- Python np.where(d == 0, ...) 과 동일하게 '정확히 0'일 때만 대체 ---
                if (denominator == 0.0) { // Python은 0.000001 (1e-6)로 정확히 0일 때만 바꿉니다.
                    denominator = 1e-6; // Python과 동일한 값으로 설정
                }

                double cosThetaVal;
                // 이 부분은 denominator가 0.0으로 남아있을 경우의 방어 코드입니다.
                // 위에서 1e-6으로 바뀌므로 실제 이 브랜치로 올 일은 거의 없을 것입니다.
                cosThetaVal = dotProduct / denominator;

                // 파이썬 np.clip(cos_theta, -1.0, 1.0) 과 동일한 효과
                cosThetaVal = Math.max(-1.0, Math.min(1.0, cosThetaVal));
                theta[i][j] = Math.acos(cosThetaVal);
            }
        }
        return theta;
    }

    public static Map<String, double[][]> diff(double[][] array) {
        if (array == null) throw new IllegalArgumentException("Input array cannot be null for diff.");
        int numRows = array.length;
        if (numRows == 0) { // 빈 배열에 대한 처리
            Map<String, double[][]> resultMap = new HashMap<>(1);
            resultMap.put("difference", new double[0][0]);
            return resultMap;
        }
        int numCols = (array[0] != null) ? array[0].length : 0;
        if (numCols < 2) { // 차분을 계산할 수 없는 경우 (Python도 0 열 반환)
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
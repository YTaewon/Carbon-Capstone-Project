package com.example.myapplication12345.AI.IMU;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IMUUtils {

    /**
     * Quaternion을 사용하여 축 회전 (Python의 rotate_axis() 변환)
     * @param ax X축 데이터
     * @param ay Y축 데이터
     * @param az Z축 데이터
     * @param rotation Quaternion 회전 행렬 [rows][cols][4] (w, x, y, z)
     * @return 회전된 x, y, z 배열
     */
    public static double[][][] rotateAxis(double[][] ax, double[][] ay, double[][] az, double[][][] rotation) {
        int rows = ax.length;
        int cols = ax[0].length;
        double[][] x = new double[rows][cols];
        double[][] y = new double[rows][cols];
        double[][] z = new double[rows][cols];
        double[][] w = new double[rows][cols];

        // Rotation 배열에서 w, x, y, z 값 추출
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                w[i][j] = rotation[i][j][0];
                x[i][j] = rotation[i][j][1];
                y[i][j] = rotation[i][j][2];
                z[i][j] = rotation[i][j][3];
            }
        }

        // 1D 배열 변환
        double[] wFlat = flatten(w);
        double[] xFlat = flatten(x);
        double[] yFlat = flatten(y);
        double[] zFlat = flatten(z);
        double[] axFlat = flatten(ax);
        double[] ayFlat = flatten(ay);
        double[] azFlat = flatten(az);

        double[] _ax = new double[axFlat.length];
        double[] _ay = new double[ayFlat.length];
        double[] _az = new double[azFlat.length];

        // Quaternion 회전 적용
        for (int i = 0; i < axFlat.length; i++) {
            _ax[i] = (1 - 2 * (yFlat[i] * yFlat[i] + zFlat[i] * zFlat[i])) * axFlat[i]
                    + 2 * (xFlat[i] * yFlat[i] - wFlat[i] * zFlat[i]) * ayFlat[i]
                    + 2 * (xFlat[i] * zFlat[i] + wFlat[i] * yFlat[i]) * azFlat[i];

            _ay[i] = 2 * (xFlat[i] * yFlat[i] + wFlat[i] * zFlat[i]) * axFlat[i]
                    + (1 - 2 * (xFlat[i] * xFlat[i] + zFlat[i] * zFlat[i])) * ayFlat[i]
                    + 2 * (yFlat[i] * zFlat[i] - wFlat[i] * xFlat[i]) * azFlat[i];

            _az[i] = 2 * (xFlat[i] * zFlat[i] - wFlat[i] * yFlat[i]) * axFlat[i]
                    + 2 * (yFlat[i] * zFlat[i] + wFlat[i] * xFlat[i]) * ayFlat[i]
                    + (1 - 2 * (xFlat[i] * xFlat[i] + yFlat[i] * yFlat[i])) * azFlat[i];
        }

        // 원래 2D 배열로 변환
        return new double[][][]{
                reshape(_ax, rows, cols),
                reshape(_ay, rows, cols),
                reshape(_az, rows, cols)
        };
    }

    /**
     * 중력 벡터를 이용하여 각도(theta) 계산
     * @param lx X축 데이터
     * @param ly Y축 데이터
     * @param lz Z축 데이터
     * @param gravity 중력 벡터 데이터 (3D 배열, 각 샘플에 대해 [gx, gy, gz])
     * @return 각도(theta) 2D 배열
     */
    public static double[][] calculateAngle(double[][] lx, double[][] ly, double[][] lz, double[][][] gravity) {
        int rows = lx.length;
        int cols = lx[0].length;

        double[][] gx = new double[rows][cols];
        double[][] gy = new double[rows][cols];
        double[][] gz = new double[rows][cols];

        // gravity 배열에서 gx, gy, gz 값을 추출
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                gx[i][j] = gravity[i][j][0];
                gy[i][j] = gravity[i][j][1];
                gz[i][j] = gravity[i][j][2];
            }
        }

        // NaN 값을 0으로 대체
        replaceNaNWithZero(gx);
        replaceNaNWithZero(gy);
        replaceNaNWithZero(gz);
        replaceNaNWithZero(lx);
        replaceNaNWithZero(ly);
        replaceNaNWithZero(lz);

        double[][] theta = new double[rows][cols];

        // 각 샘플별 각도 계산 (벡터 내적)
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double dotProduct = gx[i][j] * lx[i][j] + gy[i][j] * ly[i][j] + gz[i][j] * lz[i][j];
                double magnitudeG = Math.sqrt(gx[i][j] * gx[i][j] + gy[i][j] * gy[i][j] + gz[i][j] * gz[i][j]);
                double magnitudeL = Math.sqrt(lx[i][j] * lx[i][j] + ly[i][j] * ly[i][j] + lz[i][j] * lz[i][j]);

                double denominator = magnitudeG * magnitudeL;
                if (denominator == 0) {
                    denominator = 0.000001; // 0으로 나누는 것을 방지
                }
                theta[i][j] = Math.acos(dotProduct / denominator);
            }
        }

        return theta;
    }

    /** NaN 값을 0으로 변경 */
    private static void replaceNaNWithZero(double[][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                if (Double.isNaN(matrix[i][j])) {
                    matrix[i][j] = 0.0;
                }
            }
        }
    }

    /** 2D 배열을 1D 배열로 변환 */
    private static double[] flatten(double[][] matrix) {
        return Arrays.stream(matrix).flatMapToDouble(Arrays::stream).toArray();
    }

    /** 1D 배열을 2D 배열로 변환 */
    private static double[][] reshape(double[] array, int rows, int cols) {
        double[][] reshaped = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(array, i * cols, reshaped[i], 0, cols);
        }
        return reshaped;
    }

    public static Map<String, double[][]> diff(double[][] array) {
        int numRows = array.length;
        int numCols = array[0].length;

        // Create an array to store the differences (one less column than the original)
        double[][] diffArray = new double[numRows][numCols - 1];

        // Calculate the difference between adjacent columns for each row
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols - 1; j++) {
                diffArray[i][j] = array[i][j + 1] - array[i][j];
            }
        }

        // Create a map to store the result
        Map<String, double[][]> resultMap = new HashMap<>();
        resultMap.put("difference", diffArray);

        return resultMap;
    }

}

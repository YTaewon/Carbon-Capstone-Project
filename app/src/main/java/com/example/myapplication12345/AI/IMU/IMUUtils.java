package com.example.myapplication12345.AI.IMU;


import java.util.HashMap;
import java.util.Map;

public class IMUUtils {

    /**
     * Quaternion을 사용하여 축 회전 (Optimized for float)
     */
    public static float[][][] rotateAxis(float[][] ax, float[][] ay, float[][] az, float[][][] rotation) {
        int rows = ax.length;
        int cols = ax[0].length;
        float[][][] result = new float[3][rows][cols]; // Directly allocate result array [x, y, z]

        // Temporary arrays for quaternion components
        float[] wFlat = new float[rows * cols];
        float[] xFlat = new float[rows * cols];
        float[] yFlat = new float[rows * cols];
        float[] zFlat = new float[rows * cols];

        // Flatten rotation quaternion once
        for (int i = 0, k = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++, k++) {
                wFlat[k] = rotation[i][j][0];
                xFlat[k] = rotation[i][j][1];
                yFlat[k] = rotation[i][j][2];
                zFlat[k] = rotation[i][j][3];
            }
        }

        // Flatten input arrays
        float[] axFlat = flatten(ax);
        float[] ayFlat = flatten(ay);
        float[] azFlat = flatten(az);

        // Apply quaternion rotation with minimal redundant operations
        for (int i = 0; i < axFlat.length; i++) {
            float x2 = xFlat[i] * xFlat[i];
            float y2 = yFlat[i] * yFlat[i];
            float z2 = zFlat[i] * zFlat[i];
            float xy = xFlat[i] * yFlat[i];
            float xz = xFlat[i] * zFlat[i];
            float yz = yFlat[i] * zFlat[i];
            float wx = wFlat[i] * xFlat[i];
            float wy = wFlat[i] * yFlat[i];
            float wz = wFlat[i] * zFlat[i];

            result[0][i / cols][i % cols] = (1 - 2 * (y2 + z2)) * axFlat[i] + 2 * (xy - wz) * ayFlat[i] + 2 * (xz + wy) * azFlat[i];
            result[1][i / cols][i % cols] = 2 * (xy + wz) * axFlat[i] + (1 - 2 * (x2 + z2)) * ayFlat[i] + 2 * (yz - wx) * azFlat[i];
            result[2][i / cols][i % cols] = 2 * (xz - wy) * axFlat[i] + 2 * (yz + wx) * ayFlat[i] + (1 - 2 * (x2 + y2)) * azFlat[i];
        }

        return result; // [0] = x, [1] = y, [2] = z
    }

    /**
     * 중력 벡터를 이용하여 각도(theta) 계산 (Optimized for float)
     */
    public static float[][] calculateAngle(float[][] lx, float[][] ly, float[][] lz, float[][][] gravity) {
        int rows = lx.length;
        int cols = lx[0].length;
        float[][] theta = new float[rows][cols];

        // Process each element directly without intermediate arrays
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float gx = Float.isNaN(gravity[i][j][0]) ? 0f : gravity[i][j][0];
                float gy = Float.isNaN(gravity[i][j][1]) ? 0f : gravity[i][j][1];
                float gz = Float.isNaN(gravity[i][j][2]) ? 0f : gravity[i][j][2];
                float lxVal = Float.isNaN(lx[i][j]) ? 0f : lx[i][j];
                float lyVal = Float.isNaN(ly[i][j]) ? 0f : ly[i][j];
                float lzVal = Float.isNaN(lz[i][j]) ? 0f : lz[i][j];

                float dotProduct = gx * lxVal + gy * lyVal + gz * lzVal;
                float magG = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                float magL = (float) Math.sqrt(lxVal * lxVal + lyVal * lyVal + lzVal * lzVal);

                float denominator = magG * magL;
                theta[i][j] = (float) Math.acos(dotProduct / (denominator == 0 ? 0.000001f : denominator));
            }
        }

        return theta;
    }

    /** 2D 배열을 1D 배열로 변환 (Optimized for float) */
    private static float[] flatten(float[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        float[] flat = new float[rows * cols];
        for (int i = 0, k = 0; i < rows; i++) {
            System.arraycopy(matrix[i], 0, flat, k, cols);
            k += cols;
        }
        return flat;
    }
    /**
     * 배열 차분 계산 (Optimized for float)
     */
    public static Map<String, float[][]> diff(float[][] array) {
        int numRows = array.length;
        int numCols = array[0].length;
        float[][] diffArray = new float[numRows][numCols - 1];

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols - 1; j++) {
                diffArray[i][j] = array[i][j + 1] - array[i][j];
            }
        }

        Map<String, float[][]> resultMap = new HashMap<>(1); // Predefine capacity
        resultMap.put("difference", diffArray);
        return resultMap;
    }
}
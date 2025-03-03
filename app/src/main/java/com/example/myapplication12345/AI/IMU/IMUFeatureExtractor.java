package com.example.myapplication12345.AI.IMU;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IMUFeatureExtractor {

    /**
     * 벡터 크기(매그니튜드) 계산 - 루프 최적화 및 float 사용
     */
    public static float[][] magnitude(float[][] x, float[][] y, float[][] z) {
        int rows = x.length;
        int cols = x[0].length;
        float[][] result = new float[rows][cols];

        for (int i = 0; i < rows; i++) {
            float[] xRow = x[i], yRow = y[i], zRow = z[i], resRow = result[i];
            for (int j = 0; j < cols; j++) {
                float xVal = xRow[j], yVal = yRow[j], zVal = zRow[j];
                resRow[j] = (float) Math.sqrt(xVal * xVal + yVal * yVal + zVal * zVal);
            }
        }
        return result;
    }

    /**
     * 통계 특징 계산 - 스트림 제거 및 직접 계산
     */
    public static Map<String, float[][]> calculateStatFeatures(float[][] magnitude, String prefix) {
        int rows = magnitude.length;
        Map<String, float[][]> result = new HashMap<>(10);

        float[][] mean = new float[rows][1];
        float[][] std = new float[rows][1];
        float[][] max = new float[rows][1];
        float[][] min = new float[rows][1];
        float[][] mad = new float[rows][1];
        float[][] iqr = new float[rows][1];
        float[][] maxCorr = new float[rows][1];
        float[][] argmaxCorr = new float[rows][1];
        float[][] zcr = new float[rows][1];
        float[][] fzc = new float[rows][1];

        StandardDeviation stdDev = new StandardDeviation();
        Max maxCalc = new Max();
        Min minCalc = new Min();

        for (int i = 0; i < rows; i++) {
            float[] row = magnitude[i];
            double[] data = new double[row.length];
            for (int j = 0; j < row.length; j++) {
                data[j] = row[j];
            }

            mean[i][0] = (float) StatUtils.mean(data);
            std[i][0] = (float) stdDev.evaluate(data);
            max[i][0] = (float) maxCalc.evaluate(data);
            min[i][0] = (float) minCalc.evaluate(data);
            mad[i][0] = computeMAD(row);
            iqr[i][0] = computeIQR(row);

            float[] autocorrData = computeAutocorrelation(row);
            float[] autocorrResult = findMaxAndArgMax(autocorrData);
            maxCorr[i][0] = autocorrResult[0];
            argmaxCorr[i][0] = autocorrResult[1];

            zcr[i][0] = computeZCR(row);
            fzc[i][0] = computeFZC(row);
        }

        result.put(prefix + "_mean", mean);
        result.put(prefix + "_std", std);
        result.put(prefix + "_max", max);
        result.put(prefix + "_min", min);
        result.put(prefix + "_mad", mad);
        result.put(prefix + "_iqr", iqr);
        result.put(prefix + "_max.corr", maxCorr);
        result.put(prefix + "_idx.max.corr", argmaxCorr);
        result.put(prefix + "_zcr", zcr);
        result.put(prefix + "_fzc", fzc);

        return result;
    }

    /** 중앙절대편차 (MAD) 계산 - float 기반으로 직접 계산 */
    private static float computeMAD(float[] data) {
        float[] copy = data.clone();
        Arrays.sort(copy);
        float median = copy[copy.length / 2];
        float[] deviations = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            deviations[i] = Math.abs(data[i] - median);
        }
        Arrays.sort(deviations);
        return deviations[deviations.length / 2];
    }

    /** 사분위 범위 (IQR) 계산 - float 기반으로 직접 계산 */
    private static float computeIQR(float[] data) {
        float[] copy = data.clone();
        Arrays.sort(copy);
        int q1Idx = copy.length / 4;
        int q3Idx = copy.length * 3 / 4;
        return copy[q3Idx] - copy[q1Idx];
    }

    /** 자기상관 계산 - float 기반 */
    private static float[] computeAutocorrelation(float[] x) {
        int n = x.length;
        float[] result = new float[n];
        for (int lag = 0; lag < n; lag++) {
            float sum = 0;
            for (int i = 0; i < n - lag; i++) {
                sum += x[i] * x[i + lag];
            }
            result[lag] = sum / (n - lag);
        }
        return result;
    }

    /** 최대값과 그 위치 계산 */
    private static float[] findMaxAndArgMax(float[] autocorr) {
        float maxValue = autocorr[0];
        int maxIndex = 0;
        for (int i = 1; i < autocorr.length; i++) {
            if (autocorr[i] > maxValue) {
                maxValue = autocorr[i];
                maxIndex = i;
            }
        }
        return new float[]{maxValue, maxIndex};
    }

    /** Zero Crossing Rate (ZCR) 계산 */
    private static float computeZCR(float[] data) {
        int count = 0;
        for (int i = 1; i < data.length; i++) {
            if ((data[i] >= 0) != (data[i - 1] >= 0)) {
                count++;
            }
        }
        return (float) count / data.length;
    }

    /** 첫 번째 Zero Crossing (FZC) 계산 */
    private static float computeFZC(float[] data) {
        for (int i = 1; i < data.length; i++) {
            if ((data[i] >= 0) != (data[i - 1] >= 0)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 주파수 특징 계산 - float 기반으로 최적화
     */
    public static Map<String, float[][]> calculateSpectralFeatures(float[][] magnitude, String prefix) {
        int rows = magnitude.length;
        int fs = 100;
        Map<String, float[][]> result = new HashMap<>(5);

        float[][] maxPSD = new float[rows][1];
        float[][] entropy = new float[rows][1];
        float[][] freqCenter = new float[rows][1];
        float[][] kurtosis = new float[rows][1];
        float[][] skewness = new float[rows][1];

        for (int i = 0; i < rows; i++) {
            float[] data = magnitude[i];
            float[] psd = computeWelchPSD(data, fs, data.length);

            maxPSD[i][0] = findMax(psd);
            entropy[i][0] = computeEntropy(psd);
            freqCenter[i][0] = computeFrequencyCenter(psd, fs, data.length);
            float[] moments = computeMoments(psd);
            kurtosis[i][0] = moments[0];
            skewness[i][0] = moments[1];
        }

        result.put(prefix + "_max.psd", maxPSD);
        result.put(prefix + "_entropy", entropy);
        result.put(prefix + "_fc", freqCenter);
        result.put(prefix + "_kurt", kurtosis);
        result.put(prefix + "_skew", skewness);

        return result;
    }

    /** Welch PSD 계산 - float 기반 */
    private static float[] computeWelchPSD(float[] data, int fs, int nperseg) {
        int n = data.length;
        int step = nperseg / 2;
        int numSegments = (n - nperseg) / step + 1;
        int paddedLength = getNextPowerOfTwo(nperseg);
        float[] psd = new float[paddedLength / 2];

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        for (int i = 0; i < numSegments; i++) {
            int start = i * step;
            float[] segment = Arrays.copyOfRange(data, start, start + nperseg);

            // Hanning Window 적용
            for (int j = 0; j < segment.length; j++) {
                segment[j] *= (float) (0.5 * (1 - Math.cos(2 * Math.PI * j / (segment.length - 1))));
            }

            double[] paddedSegment = new double[paddedLength];
            for (int j = 0; j < segment.length; j++) paddedSegment[j] = segment[j];
            Complex[] fftResult = fft.transform(paddedSegment, TransformType.FORWARD);

            for (int j = 0; j < psd.length; j++) {
                double real = fftResult[j].getReal();
                double imag = fftResult[j].getImaginary();
                psd[j] += (float) ((real * real + imag * imag) / numSegments);
            }
        }

        float scale = 2.0f / (fs * nperseg);
        for (int i = 0; i < psd.length; i++) {
            psd[i] *= scale;
        }
        return psd;
    }

    /** 다음 2의 거듭제곱 계산 */
    private static int getNextPowerOfTwo(int num) {
        return Integer.highestOneBit(num - 1) << 1;
    }

    /** 최대 PSD 값 계산 */
    private static float findMax(float[] psd) {
        float max = psd[0];
        for (int i = 1; i < psd.length; i++) {
            if (psd[i] > max) max = psd[i];
        }
        return max;
    }

    /** 주파수 엔트로피 계산 */
    private static float computeEntropy(float[] psd) {
        float sum = 0;
        for (float p : psd) sum += p;
        if (sum == 0) return 0;

        float entropy = 0;
        float invSum = 1.0f / sum;
        for (float p : psd) {
            if (p > 0) {
                float pNorm = p * invSum;
                entropy -= pNorm * (float) Math.log(pNorm);
            }
        }
        return entropy;
    }

    /** 주파수 중심 계산 */
    private static float computeFrequencyCenter(float[] psd, int fs, int nperseg) {
        float sumPsd = 0, weightedSum = 0;
        float freqStep = (float) fs / nperseg;
        for (int i = 0; i < psd.length; i++) {
            sumPsd += psd[i];
            weightedSum += i * freqStep * psd[i];
        }
        return sumPsd == 0 ? 0 : weightedSum / sumPsd;
    }

    /** 첨도와 왜도 계산 */
    private static float[] computeMoments(float[] psd) {
        float mean = 0, m2 = 0, m3 = 0, m4 = 0;
        int n = psd.length;

        for (float p : psd) mean += p;
        mean /= n;

        for (float p : psd) {
            float diff = p - mean;
            float diff2 = diff * diff;
            m2 += diff2;
            m3 += diff2 * diff;
            m4 += diff2 * diff2;
        }

        m2 /= n;
        m3 /= n;
        m4 /= n;

        float std = (float) Math.sqrt(m2);
        float invStd = std == 0 ? 1e-10f : 1 / std;
        float kurtosis = m4 * invStd * invStd * invStd * invStd;
        float skewness = m3 * invStd * invStd * invStd;

        return new float[]{kurtosis, skewness};
    }
}
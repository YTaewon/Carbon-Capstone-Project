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
     * 벡터 크기(매그니튜드) 계산 (2D 배열 입력, 2D 배열 출력)
     * @param x X축 데이터 (2D 배열)
     * @param y Y축 데이터 (2D 배열)
     * @param z Z축 데이터 (2D 배열)
     * @return 각 샘플의 벡터 크기(매그니튜드) (2D 배열)
     */
    public static double[][] magnitude(double[][] x, double[][] y, double[][] z) {
        int rows = x.length;
        int cols = x[0].length;
        double[][] result = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = Math.sqrt(x[i][j] * x[i][j] + y[i][j] * y[i][j] + z[i][j] * z[i][j]);
            }
        }
        return result;
    }


    public static Map<String, double[][]> calculateStatFeatures(double[][] magnitude, String prefix) {
        int rows = magnitude.length;
        Map<String, double[][]> result = new HashMap<>();

        double[][] mean = new double[rows][1];
        double[][] std = new double[rows][1];
        double[][] max = new double[rows][1];
        double[][] min = new double[rows][1];
        double[][] mad = new double[rows][1];
        double[][] iqr = new double[rows][1];
        double[][] maxcorr = new double[rows][1];
        double[][] argmax_corr = new double[rows][1];
        double[][] zcr = new double[rows][1];
        double[][] fzc = new double[rows][1];

        for (int i = 0; i < rows; i++) {
            double[] data = magnitude[i];
            mean[i][0] = StatUtils.mean(data);
            std[i][0] = new StandardDeviation().evaluate(data);
            max[i][0] = new Max().evaluate(data);
            min[i][0] = new Min().evaluate(data);
            mad[i][0] = medianAbsoluteDeviation(data);
            iqr[i][0] = interquartileRange(data);
            maxcorr[i][0] = autocorrelationMax(data);
            argmax_corr[i][0] = argMaxAutocorrelation(data);
            zcr[i][0] = zeroCrossingRate(data);
            fzc[i][0] = firstZeroCrossing(data);
        }

        result.put(prefix + "_mean", mean);
        result.put(prefix + "_std", std);
        result.put(prefix + "_max", max);
        result.put(prefix + "_min", min);
        result.put(prefix + "_mad", mad);
        result.put(prefix + "_iqr", iqr);
        result.put(prefix + "_max.corr", maxcorr);
        result.put(prefix + "_idx.max.corr", argmax_corr);
        result.put(prefix + "_zcr", zcr);
        result.put(prefix + "_fzc", fzc);

        return result;
    }

    /** 중앙절대편차 (MAD) 계산 */
    private static double medianAbsoluteDeviation(double[] data) {
        double median = StatUtils.percentile(data, 50);
        double[] deviations = Arrays.stream(data).map(d -> Math.abs(d - median)).toArray();
        return StatUtils.percentile(deviations, 50);
    }

    /** 사분위 범위 (IQR) 계산 */
    private static double interquartileRange(double[] data) {
        double q1 = StatUtils.percentile(data, 25);
        double q3 = StatUtils.percentile(data, 75);
        return q3 - q1;
    }

    /** 최대 자기상관계수 계산 */
    private static double autocorrelationMax(double[] data) {
        double[] autocorr = computeAutocorrelation(data);
        return Arrays.stream(autocorr).max().orElse(0);
    }

    /** 자기상관 최대값 위치 계산 */
    private static double argMaxAutocorrelation(double[] data) {
        double[] autocorr = computeAutocorrelation(data);
        return Arrays.stream(autocorr).max().orElse(0);
    }

    /** 자기상관 함수 계산 */
    private static double[] computeAutocorrelation(double[] x) {
        int n = x.length;
        double[] result = new double[n];
        for (int lag = 0; lag < n; lag++) {
            double sum = 0;
            for (int i = 0; i < n - lag; i++) {
                sum += x[i] * x[i + lag];
            }
            result[lag] = sum / (n - lag);
        }
        return result;
    }

    /** Zero Crossing Rate (ZCR) 계산 */
    private static double zeroCrossingRate(double[] data) {
        int count = 0;
        for (int i = 1; i < data.length; i++) {
            if (Math.signum(data[i]) != Math.signum(data[i - 1])) {
                count++;
            }
        }
        return (double) count / data.length;
    }

    /** First Zero Crossing (FZC) 계산 */
    private static int firstZeroCrossing(double[] data) {
        for (int i = 1; i < data.length; i++) {
            if (Math.signum(data[i]) != Math.signum(data[i - 1])) {
                return i;
            }
        }
        return 0;
    }

    /** 주파수 특징 계산 (Welch PSD, 엔트로피, 중심주파수 등) */
    public static Map<String, double[][]> calculateSpectralFeatures(double[][] magnitude, String prefix) {
        int rows = magnitude.length;
        int fs = 100;
        Map<String, double[][]> result = new HashMap<>();

        double[][] maxPSD = new double[rows][1];
        double[][] entropy = new double[rows][1];
        double[][] frequencyCenter = new double[rows][1];
        double[][] kurtosis = new double[rows][1];
        double[][] skewness = new double[rows][1];

        for (int i = 0; i < rows; i++) {
            double[] data = magnitude[i];

            // Compute Welch PSD
            double[] psd = computeWelchPSD(data, fs, data.length);

            // Calculate features
            maxPSD[i][0] = Arrays.stream(psd).max().orElse(0);  // Max PSD value
            entropy[i][0] = calculateEntropy(psd);              // Frequency entropy
            frequencyCenter[i][0] = calculateFrequencyCenter(psd, fs, data.length); // Frequency center
            kurtosis[i][0] = calculateKurtosis(psd);            // Kurtosis
            skewness[i][0] = calculateSkewness(psd);            // Skewness
        }

        // Store results in the map with appropriate keys
        result.put(prefix + "_max.psd", maxPSD);
        result.put(prefix + "_entropy", entropy);
        result.put(prefix + "_fc", frequencyCenter);
        result.put(prefix + "_kurt", kurtosis);
        result.put(prefix + "_skew", skewness);

        return result;
    }

    public static double[] computeWelchPSD(double[] data, int fs, int nperseg) {
        int n = data.length;
        int step = nperseg / 2;  // 50% 오버랩 적용
        int numSegments = (n - nperseg) / step + 1;

        // 패딩된 데이터 길이 계산 (2의 거듭제곱)
        int paddedLength = getNextPowerOfTwo(nperseg);
        double[] psd = new double[paddedLength / 2];

        for (int i = 0; i < numSegments; i++) {
            int start = i * step;
            double[] segment = Arrays.copyOfRange(data, start, start + nperseg);

            // Hanning Window 적용
            for (int j = 0; j < segment.length; j++) {
                segment[j] *= 0.5 * (1 - Math.cos(2 * Math.PI * j / (segment.length - 1)));
            }

            // 패딩 추가
            double[] paddedSegment = new double[paddedLength];
            System.arraycopy(segment, 0, paddedSegment, 0, segment.length);

            // FFT 수행 using Apache Commons Math
            FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
            Complex[] fftResult = fft.transform(paddedSegment, TransformType.FORWARD);

            // PSD 계산 (실수부² + 허수부²)
            for (int j = 0; j < psd.length; j++) {
                double real = fftResult[j].getReal();
                double imag = fftResult[j].getImaginary();
                psd[j] += (real * real + imag * imag) / numSegments;  // 평균을 내서 Welch PSD 완성
            }
        }

        // Scale the PSD by the sampling frequency and segment length
        for (int i = 0; i < psd.length; i++) {
            psd[i] *= 2.0 / (fs * nperseg);
        }

        return psd;
    }

    private static int getNextPowerOfTwo(int num) {
        int power = 1;
        while (power < num) {
            power *= 2;
        }
        return power;
    }

    /** 주파수 엔트로피 계산 */
    public static double calculateEntropy(double[] psd) {
        double sum = Arrays.stream(psd).sum();
        if (sum == 0) return 0;

        return Arrays.stream(psd)
                .map(p -> p / sum)
                .filter(p -> p > 0)
                .map(p -> -p * Math.log(p))
                .sum();
    }

    /** 주파수 중심(Frequency Center) 계산 */
    public static double calculateFrequencyCenter(double[] psd, int fs, int nperseg) {
        double sumPsd = Arrays.stream(psd).sum();
        if (sumPsd == 0) return 0;

        double weightedSum = 0;
        for (int i = 0; i < psd.length; i++) {
            double freq = i * (fs / (double) nperseg);
            weightedSum += freq * psd[i];
        }
        return weightedSum / sumPsd;
    }

    /** 첨도(Kurtosis) 계산 */
    public static double calculateKurtosis(double[] psd) {
        double mean = Arrays.stream(psd).average().orElse(0);
        double std = Math.sqrt(Arrays.stream(psd).map(d -> Math.pow(d - mean, 2)).average().orElse(0));
        return Arrays.stream(psd)
                .map(d -> Math.pow((d - mean) / std, 4))
                .average()
                .orElse(0);
    }

    /** 왜도(Skewness) 계산 */
    public static double calculateSkewness(double[] psd) {
        double mean = Arrays.stream(psd).average().orElse(0);
        double std = Math.sqrt(Arrays.stream(psd).map(d -> Math.pow(d - mean, 2)).average().orElse(0));
        return Arrays.stream(psd)
                .map(d -> Math.pow((d - mean) / std, 3))
                .average()
                .orElse(0);
    }
}

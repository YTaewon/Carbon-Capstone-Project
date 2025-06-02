package com.example.myapplication12345.AI.IMU;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.descriptive.rank.Percentile.EstimationType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class IMUFeatureExtractor {

    // --- 통계 피처 계산 함수들 ---
    public static Map<String, double[][]> calculateStatFeatures(double[][] dataWindows, String prefix) {
        if (dataWindows == null || dataWindows.length == 0) {
            return new HashMap<>();
        }
        int numWindows = dataWindows.length;
        Map<String, double[][]> result = new HashMap<>();

        double[][] mean = new double[numWindows][1];
        double[][] std = new double[numWindows][1];
        double[][] max = new double[numWindows][1];
        double[][] min = new double[numWindows][1];
        double[][] mad = new double[numWindows][1];
        double[][] iqr = new double[numWindows][1];
        double[][] maxCorr = new double[numWindows][1];
        double[][] idxMaxCorr = new double[numWindows][1];
        double[][] zcr = new double[numWindows][1];
        double[][] fzc = new double[numWindows][1];

        StandardDeviation stdDevCalc = new StandardDeviation(false);
        Percentile percentileCalcR7 = new Percentile().withEstimationType(EstimationType.R_7);

        for (int i = 0; i < numWindows; i++) {
            double[] currentWindow = dataWindows[i];
            if (currentWindow == null || currentWindow.length == 0) {
                mean[i][0] = std[i][0] = max[i][0] = min[i][0] = mad[i][0] = iqr[i][0] = Double.NaN;
                maxCorr[i][0] = idxMaxCorr[i][0] = zcr[i][0] = fzc[i][0] = Double.NaN;
                continue;
            }

            boolean hasOnlyNaN = true;
            for(double val : currentWindow) { if (!Double.isNaN(val)) { hasOnlyNaN = false; break; } }
            if (hasOnlyNaN) {
                mean[i][0] = std[i][0] = max[i][0] = min[i][0] = mad[i][0] = iqr[i][0] = Double.NaN;
                maxCorr[i][0] = idxMaxCorr[i][0] = zcr[i][0] = fzc[i][0] = Double.NaN;
                continue;
            }

            mean[i][0] = StatUtils.mean(currentWindow);
            std[i][0] = stdDevCalc.evaluate(currentWindow);
            max[i][0] = StatUtils.max(currentWindow);
            min[i][0] = StatUtils.min(currentWindow);
            mad[i][0] = computeRawMAD(currentWindow);
            iqr[i][0] = percentileCalcR7.evaluate(currentWindow, 75.0) - percentileCalcR7.evaluate(currentWindow, 25.0);

            double[] autocorrData = computeAutocorrelation(currentWindow);
            if (autocorrData.length > 0) {
                double[] autocorrResult = findMaxAndArgMax(autocorrData);
                maxCorr[i][0] = autocorrResult[0];
                idxMaxCorr[i][0] = autocorrResult[1];
                zcr[i][0] = computeZCR(autocorrData);
                fzc[i][0] = computeFZC(autocorrData);
            } else {
                maxCorr[i][0] = idxMaxCorr[i][0] = zcr[i][0] = fzc[i][0] = Double.NaN;
            }
        }
        result.put(prefix + "_mean", mean); result.put(prefix + "_std", std); result.put(prefix + "_max", max);
        result.put(prefix + "_min", min); result.put(prefix + "_mad", mad); result.put(prefix + "_iqr", iqr);
        result.put(prefix + "_max.corr", maxCorr); result.put(prefix + "_idx.max.corr", idxMaxCorr);
        result.put(prefix + "_zcr", zcr); result.put(prefix + "_fzc", fzc);
        return result;
    }

    private static double computeRawMAD(double[] data) {
        if (data == null || data.length == 0) return Double.NaN;
        double[] cleanData = Arrays.stream(data).filter(d -> !Double.isNaN(d)).toArray();
        if (cleanData.length == 0) return Double.NaN;
        double median = new Percentile().withEstimationType(EstimationType.R_7).evaluate(cleanData, 50.0);
        if (Double.isNaN(median)) return Double.NaN;
        double[] absDeviations = new double[cleanData.length];
        for (int i = 0; i < cleanData.length; i++) absDeviations[i] = Math.abs(cleanData[i] - median);
        return new Percentile().withEstimationType(EstimationType.R_7).evaluate(absDeviations, 50.0);
    }

    private static double[] computeAutocorrelation(double[] x) {
        int n = x.length; if (n == 0) return new double[0];
        double[] xClean = Arrays.stream(x).map(val -> Double.isNaN(val) ? 0.0 : val).toArray();
        double[] fullCorr = new double[2 * n - 1];
        for (int lag = 0; lag < 2 * n - 1; lag++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                int j = i - (lag - (n - 1));
                if (j >= 0 && j < n) sum += xClean[i] * xClean[j];
            }
            fullCorr[lag] = sum;
        }
        return Arrays.copyOfRange(fullCorr, n - 1, fullCorr.length);
    }

    private static double[] findMaxAndArgMax(double[] data) {
        if (data == null || data.length == 0) return new double[]{Double.NaN, Double.NaN};
        double maxValue = data[0]; int maxIndex = 0; boolean allNaN = Double.isNaN(data[0]);
        for (int i = 1; i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                allNaN = false;
                if (data[i] > maxValue) { maxValue = data[i]; maxIndex = i;}
            }
        }
        return allNaN ? new double[]{Double.NaN, Double.NaN} : new double[]{maxValue, (double)maxIndex};
    }

    private static double computeZCR(double[] data) {
        if (data == null || data.length < 2) return 0.0;
        double[] dataClean = Arrays.stream(data).map(val -> Double.isNaN(val) ? 0.0 : val).toArray();
        int[] signs = new int[dataClean.length];
        for (int i = 0; i < dataClean.length; i++) signs[i] = (dataClean[i] >= 0) ? 1 : -1;
        int S = 0; for (int i = 1; i < signs.length; i++) S += Math.abs(signs[i] - signs[i-1]);
        return (dataClean.length == 0) ? 0.0 : (0.5 * S) / dataClean.length;
    }

    private static double computeFZC(double[] data) {
        if (data == null || data.length < 2) return 0.0;
        double[] dataClean = Arrays.stream(data).map(val -> Double.isNaN(val) ? 0.0 : val).toArray();
        for (int i = 0; i < dataClean.length - 1; i++) {
            if (Math.signum(dataClean[i]) != Math.signum(dataClean[i+1])) return i + 1;
        }
        return 0;
    }

    // --- 스펙트럼 피처 계산 함수들 ---
    public static Map<String, double[][]> calculateSpectralFeatures(
            double[][] magnitudeData2D, String prefix, int fs, String detrendType) {
        if (magnitudeData2D == null || magnitudeData2D.length == 0) return new HashMap<>();
        int numWindows = magnitudeData2D.length;

        double[][] maxPSD_res_all = new double[numWindows][1];
        double[][] entropy_res_all = new double[numWindows][1];
        double[][] fc_res_all = new double[numWindows][1];
        double[][] kurt_res_all = new double[numWindows][1];
        double[][] skew_res_all = new double[numWindows][1];

        for (int i = 0; i < numWindows; i++) {
            double[] singleWindowMagnitude = magnitudeData2D[i];
            if (singleWindowMagnitude == null || singleWindowMagnitude.length == 0) {
                maxPSD_res_all[i][0] = entropy_res_all[i][0] = fc_res_all[i][0] = kurt_res_all[i][0] = skew_res_all[i][0] = Double.NaN;
                continue;
            }
            int nperseg = singleWindowMagnitude.length;
            int nfftForWelch;
            if ((nperseg & (nperseg - 1)) == 0) nfftForWelch = nperseg;
            else nfftForWelch = getNextPowerOfTwo(nperseg);
            if (nfftForWelch < 2) nfftForWelch = 2;

            double[] psd = computeWelchPSD(singleWindowMagnitude, fs, nperseg, "hann", detrendType);

            if (psd.length == 0) { continue; }

            maxPSD_res_all[i][0] = findMax(psd);
            entropy_res_all[i][0] = computeEntropy(psd);
            fc_res_all[i][0] = computeFrequencyCenter(psd, fs, nfftForWelch);

            double[] cleanPsdForStats = Arrays.stream(psd).filter(val -> !Double.isNaN(val) && Double.isFinite(val)).toArray();
            kurt_res_all[i][0] = (cleanPsdForStats.length > 0) ? calculateKurtosisSciPyStyle(cleanPsdForStats) : Double.NaN;
            skew_res_all[i][0] = (cleanPsdForStats.length > 0) ? calculateSkewnessSciPyStyle(cleanPsdForStats) : Double.NaN;
        }
        Map<String, double[][]> result = new HashMap<>();
        result.put(prefix + "_max.psd", maxPSD_res_all); result.put(prefix + "_entropy", entropy_res_all);
        result.put(prefix + "_fc", fc_res_all); result.put(prefix + "_kurt", kurt_res_all);
        result.put(prefix + "_skew", skew_res_all);
        return result;
    }

    // Welch PSD (BigDecimal 디트렌딩 및 로깅 정리 버전)
    public static double[] computeWelchPSD(double[] dataInput, int fs, int npersegInput, String windowType, String detrendType) {
        int n = dataInput.length;

        if (n == 0) {
            int nfftForEmpty = npersegInput;
            if (nfftForEmpty <= 0) nfftForEmpty = 256;
            if ((nfftForEmpty & nfftForEmpty - 1) != 0) {
                nfftForEmpty = getNextPowerOfTwo(nfftForEmpty);
            }
            if (nfftForEmpty < 2) nfftForEmpty = 2;
            return new double[nfftForEmpty / 2 + 1];
        }

        double[] data = new double[n];
        if ("mean".equalsIgnoreCase(detrendType) || "constant".equalsIgnoreCase(detrendType)) {
            BigDecimal sumBd = BigDecimal.ZERO;
            for (double v : dataInput) {
                sumBd = sumBd.add(BigDecimal.valueOf(v)); // 수정: String.valueOf 대신 BigDecimal.valueOf
            }
            // 나눗셈의 정밀도를 매우 높게 설정 (예: 소수점 50자리 이상)
            BigDecimal meanBd = sumBd.divide(new BigDecimal(n), 50, RoundingMode.HALF_UP);
            // System.out.printf(Locale.US, "WELCH_JAVA_DEBUG: Detrend 'mean', BigDecimal mean = %s%n", meanBd.toPlainString());

            for (int k = 0; k < n; k++) {
                data[k] = BigDecimal.valueOf(dataInput[k]).subtract(meanBd).doubleValue(); // 수정
            }
        } else if ("linear".equalsIgnoreCase(detrendType)) {
            if (n > 1) {
                double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
                for (int k = 0; k < n; ++k) { sumX += k; sumY += dataInput[k]; sumXY += (double)k * dataInput[k]; sumXX += (double)k * k; }
                double slopeDeno = (double)n * sumXX - sumX * sumX;
                double slope = (slopeDeno == 0) ? 0 : ((double)n * sumXY - sumX * sumY) / slopeDeno;
                if (Double.isNaN(slope) || Double.isInfinite(slope)) slope = 0;
                double intercept = (sumY - slope * sumX) / n;
                if (Double.isNaN(intercept) || Double.isInfinite(intercept)) intercept = sumY / n;
                for (int k = 0; k < n; k++) data[k] = dataInput[k] - (slope * k + intercept);
            } else { data[0] = 0.0; }
        } else {
            System.arraycopy(dataInput, 0, data, 0, n);
        }

        int nperseg = npersegInput; if (nperseg <= 0 || nperseg > n) nperseg = n;
        int nfft;
        if ((nperseg & (nperseg - 1)) == 0) nfft = nperseg; else nfft = getNextPowerOfTwo(nperseg);
        if (nfft < 2) nfft = 2;

        double[] window = new double[nperseg];
        if ("hann".equalsIgnoreCase(windowType)) { // SciPy hann(M, sym=False) => 분모 M
            if (nperseg == 1) window[0] = 1.0;
            else {
                for (int k = 0; k < nperseg; k++)
                    window[k] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * k / (double) nperseg));
            }
        } else Arrays.fill(window, 1.0);

        double sumSqWindow = 0; for (double v : window) sumSqWindow += v * v; if (sumSqWindow == 0) sumSqWindow = 1.0;

        double[] segmentDataForFFT = new double[nfft];
        for (int j = 0; j < nperseg; j++) segmentDataForFFT[j] = data[j] * window[j];

        FastFourierTransformer fftTransformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] fftResult = fftTransformer.transform(segmentDataForFFT, TransformType.FORWARD);

        int psdLen = (nfft / 2) + 1;
        double[] psd = new double[psdLen];
        for (int k = 0; k < psdLen; k++) {
            psd[k] = fftResult[k].getReal() * fftResult[k].getReal() + fftResult[k].getImaginary() * fftResult[k].getImaginary();
        }

        for (int k = 1; k < psdLen; k++) {
            if (!(nfft % 2 == 0 && k == psdLen - 1)) {
                psd[k] *= 2.0;
            }
        }

        double scaleFactor = fs * sumSqWindow;
        if (scaleFactor == 0) {
            scaleFactor = (sumSqWindow > 0) ? sumSqWindow : 1.0;
        }

        for (int k = 0; k < psdLen; k++) {
            psd[k] /= scaleFactor;
        }

        return psd;
    }

    public static int getNextPowerOfTwo(int number) {
        if (number <= 0) return 2;
        int power = 1;
        while (power < number) { power <<= 1; if (power <= 0) throw new IllegalArgumentException("Number too large"); }
        return power;
    }

    public static double computeEntropy(double[] psd) {
        if (psd == null || psd.length == 0) return 0.0;
        double sumPk = 0; for (double p_val : psd) if (!Double.isNaN(p_val) && p_val > 0) sumPk += p_val;
        double safeSumPk = (sumPk == 0.0) ? 1e-6 : sumPk; double entropy = 0.0;
        for (double p_val : psd) {
            if (Double.isNaN(p_val) || p_val <= 0) continue;
            double pk_norm = p_val / safeSumPk;
            if (pk_norm > 1e-15) entropy -= pk_norm * Math.log(pk_norm); // Math.log is natural log
        }
        return entropy;
    }

    public static double computeFrequencyCenter(double[] psd, int fs, int nfftUsedForPsd) {
        if (psd == null || psd.length == 0) return Double.NaN;
        int nfft = nfftUsedForPsd;
        if (nfft <= 0 || psd.length != (nfft / 2 + 1)) {
            if (psd.length > 1) nfft = (psd.length - 1) * 2;
            else nfft = 2;
        }
        if (nfft < 1) return Double.NaN;
        double sumPsdRaw = 0; double weightedSumRaw = 0;
        double freqStep = fs == 0 ? 0 : (double) fs / nfft;
        for (int k = 0; k < psd.length; k++) {
            if (!Double.isNaN(psd[k]) && psd[k] >=0) { sumPsdRaw += psd[k]; weightedSumRaw += (k * freqStep) * psd[k]; }
        }
        double denominatorForFc = (sumPsdRaw == 0.0) ? 1e-6 : sumPsdRaw;
        return weightedSumRaw / denominatorForFc;
    }

    public static double calculateKurtosisSciPyStyle(double[] data) {
        double[] cleanData = Arrays.stream(data).filter(d -> !Double.isNaN(d) && Double.isFinite(d)).toArray();
        int n = cleanData.length;
        if (n == 0) return Double.NaN;
        if (n == 1) return -3.0; // SciPy: fisher=True, bias=True
        // For n < 4, SciPy kurtosis with bias=True, fisher=True might return NaN if variance is not well-defined or zero.

        double mean = 0; for (double v : cleanData) mean += v; mean /= n;
        double m2 = 0, m4 = 0;
        for (double v : cleanData) { double d = v - mean; m2 += d * d; m4 += d * d * d * d; }
        m2 /= n; m4 /= n;

        if (Math.abs(m2) < 1e-40) {
            return Double.NaN;
        }
        double denom = m2 * m2;
        if (Math.abs(denom) < 1e-80) { // 분모가 극도로 작음
            return Double.NaN;
        }
        double kurt = (m4 / denom) - 3.0;
        return Double.isFinite(kurt) ? kurt : Double.NaN; // 최종 결과가 유한한지 확인
    }

    public static double calculateSkewnessSciPyStyle(double[] data) {
        double[] cleanData = Arrays.stream(data).filter(d -> !Double.isNaN(d) && Double.isFinite(d)).toArray();
        int n = cleanData.length;
        if (n == 0) return Double.NaN;
        if (n == 1) return 0.0;

        double mean = 0; for (double v : cleanData) mean += v; mean /= n;
        double m2 = 0, m3 = 0;
        for (double v : cleanData) { double d = v - mean; m2 += d * d; m3 += d * d * d; }
        m2 /= n; m3 /= n;

        if (Math.abs(m2) < 1e-40) {
            return Double.NaN;
        }
        double m2_pow_1_5 = Math.pow(m2, 1.5);
        if (Double.isNaN(m2_pow_1_5) || Math.abs(m2_pow_1_5) < 1e-60) {
            return (Math.abs(m3) < 1e-60 && m3 != 0) ? Double.NaN : 0.0;
        }
        double skew = m3 / m2_pow_1_5;
        return Double.isFinite(skew) ? skew : Double.NaN;
    }

    public static double findMax(double[] data) {
        if (data == null || data.length == 0) return Double.NaN;
        double maxVal = Double.NEGATIVE_INFINITY;
        boolean foundNumeric = false;
        for (double v : data) {
            if (!Double.isNaN(v) && Double.isFinite(v)) {
                foundNumeric = true;
                if (v > maxVal) maxVal = v;
            }
        }
        return foundNumeric ? maxVal : Double.NaN;
    }

    public static double[][] magnitude(double[][] x, double[][] y, double[][] z) {
        if (x == null || y == null || z == null || x.length == 0 || y.length == 0 || z.length == 0) {
            return new double[0][0];
        }
        int numWindows = x.length;
        int windowSize = x[0].length;

        if (y.length != numWindows || z.length != numWindows || (windowSize > 0 && (y[0].length != windowSize || z[0].length != windowSize))) {
            throw new IllegalArgumentException("Input arrays must have the same dimensions for magnitude calculation.");
        }

        double[][] result = new double[numWindows][windowSize];
        for (int i = 0; i < numWindows; i++) {
            if (x[i] == null || y[i] == null || z[i] == null || x[i].length != windowSize || y[i].length != windowSize || z[i].length != windowSize) {
                throw new IllegalArgumentException("Inconsistent window size or null window at index " + i + " for magnitude.");
            }
            for (int j = 0; j < windowSize; j++) {
                result[i][j] = Math.sqrt(x[i][j] * x[i][j] + y[i][j] * y[i][j] + z[i][j] * z[i][j]);
            }
        }
        return result;
    }
}
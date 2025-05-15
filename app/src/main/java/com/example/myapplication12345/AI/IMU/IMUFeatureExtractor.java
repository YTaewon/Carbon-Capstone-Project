package com.example.myapplication12345.AI.IMU;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.descriptive.rank.Percentile.EstimationType;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IMUFeatureExtractor {

    /**
     * 벡터 크기(매그니튜드) 계산 (double 사용)
     */
    public static double[][] magnitude(double[][] x, double[][] y, double[][] z) {
        if (x == null || y == null || z == null) throw new IllegalArgumentException("Input arrays cannot be null for magnitude.");
        int rows = x.length;
        if (rows == 0) return new double[0][0];
        int cols = (x[0] != null) ? x[0].length : 0;
        if (cols == 0 && rows > 0) return new double[rows][0];

        if (!(y.length == rows && z.length == rows)) {
            throw new IllegalArgumentException("Input arrays must have the same number of rows for magnitude.");
        }
        for(int i=0; i<rows; ++i) {
            if (x[i] == null || y[i] == null || z[i] == null ||
                    x[i].length != cols || y[i].length != cols || z[i].length != cols) {
                throw new IllegalArgumentException("Window sizes must be consistent across all input arrays for window " + i + " in magnitude.");
            }
        }

        double[][] result = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double xVal = x[i][j];
                double yVal = y[i][j];
                double zVal = z[i][j];
                result[i][j] = Math.sqrt(xVal * xVal + yVal * yVal + zVal * zVal);
            }
        }
        return result;
    }

    /**
     * 통계 특징 계산 - 파이썬 방식에 맞춤 (MAD 스케일링 제거, IQR EstimationType R-7)
     */
    public static Map<String, double[][]> calculateStatFeatures(double[][] magnitudeData, String prefix) {
        if (magnitudeData == null) throw new IllegalArgumentException("magnitudeData cannot be null.");
        int rows = magnitudeData.length;
        Map<String, double[][]> result = new HashMap<>(10);

        double[][] mean = new double[rows][1];
        double[][] std = new double[rows][1];
        double[][] max = new double[rows][1];
        double[][] min = new double[rows][1];
        double[][] mad = new double[rows][1];
        double[][] iqr = new double[rows][1];
        double[][] maxCorr = new double[rows][1];
        double[][] argmaxCorr = new double[rows][1];
        double[][] zcr = new double[rows][1];
        double[][] fzc = new double[rows][1];

        StandardDeviation stdDevCalc = new StandardDeviation(false);
        Max maxCalc = new Max();
        Min minCalc = new Min();
        Percentile percentileCalc = new Percentile().withEstimationType(EstimationType.R_7);


        for (int i = 0; i < rows; i++) {
            if (magnitudeData[i] == null) {
                Arrays.fill(mean[i], Double.NaN); Arrays.fill(std[i], Double.NaN);
                Arrays.fill(max[i], Double.NaN); Arrays.fill(min[i], Double.NaN);
                Arrays.fill(mad[i], Double.NaN); Arrays.fill(iqr[i], Double.NaN);
                Arrays.fill(maxCorr[i], Double.NaN); Arrays.fill(argmaxCorr[i], Double.NaN);
                Arrays.fill(zcr[i], Double.NaN); Arrays.fill(fzc[i], Double.NaN);
                continue;
            }
            double[] row = magnitudeData[i];
            double[] cleanRow = new double[row.length];
            for (int k = 0; k < row.length; k++) {
                cleanRow[k] = Double.isNaN(row[k]) ? 0.0 : row[k];
            }

            if (cleanRow.length == 0) {
                mean[i][0] = Double.NaN; std[i][0] = Double.NaN; max[i][0] = Double.NaN; min[i][0] = Double.NaN;
                mad[i][0] = Double.NaN; iqr[i][0] = Double.NaN; maxCorr[i][0] = Double.NaN; argmaxCorr[i][0] = Double.NaN;
                zcr[i][0] = Double.NaN; fzc[i][0] = Double.NaN;
                continue;
            }

            mean[i][0] = StatUtils.mean(cleanRow);
            std[i][0] = stdDevCalc.evaluate(cleanRow);
            max[i][0] = maxCalc.evaluate(cleanRow);
            min[i][0] = minCalc.evaluate(cleanRow);
            mad[i][0] = computeRawMAD(cleanRow);
            iqr[i][0] = percentileCalc.evaluate(cleanRow, 75.0) - percentileCalc.evaluate(cleanRow, 25.0);

            double[] autocorrData = computeAutocorrelationPythonStyle(cleanRow);
            double[] autocorrResult = findMaxAndArgMax(autocorrData);
            maxCorr[i][0] = autocorrResult[0];
            argmaxCorr[i][0] = autocorrResult[1];

            zcr[i][0] = computeZCRPythonStyle(autocorrData);
            fzc[i][0] = computeFZCPythonStyle(autocorrData);
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

    private static double computeRawMAD(double[] data) {
        if (data == null || data.length == 0) return Double.NaN;
        double[] sortedData = Arrays.copyOf(data, data.length);
        Arrays.sort(sortedData);
        double median;
        if (sortedData.length % 2 == 0) {
            median = (sortedData[sortedData.length / 2 - 1] + sortedData[sortedData.length / 2]) / 2.0;
        } else {
            median = sortedData[sortedData.length / 2];
        }

        double[] absDeviations = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            absDeviations[i] = Math.abs(data[i] - median);
        }
        Arrays.sort(absDeviations);
        if (absDeviations.length % 2 == 0) {
            return (absDeviations[absDeviations.length / 2 - 1] + absDeviations[absDeviations.length / 2]) / 2.0;
        } else {
            return absDeviations[absDeviations.length / 2];
        }
    }

    private static double[] computeAutocorrelationPythonStyle(double[] x) {
        int n = x.length;
        if (n == 0) return new double[0];
        double[] fullCorr = new double[2 * n - 1];
        for (int lag = 0; lag < 2 * n - 1; lag++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                int j = i - (lag - (n - 1));
                if (j >= 0 && j < n) {
                    sum += x[i] * x[j];
                }
            }
            fullCorr[lag] = sum;
        }
        return Arrays.copyOfRange(fullCorr, n - 1, fullCorr.length);
    }

    private static double[] findMaxAndArgMax(double[] data) {
        if (data == null || data.length == 0) return new double[]{Double.NaN, Double.NaN};
        double maxValue = data[0]; // 초기값을 첫 번째 요소로 설정
        int maxIndex = 0;
        for (int i = 1; i < data.length; i++) {
            if (data[i] > maxValue) {
                maxValue = data[i];
                maxIndex = i;
            }
        }
        return new double[]{maxValue, (double)maxIndex};
    }

    private static double computeZCRPythonStyle(double[] data) {
        if (data == null || data.length < 2) return 0.0;
        int crossings = 0;
        for (int i = 0; i < data.length - 1; i++) {
            if (Math.signum(data[i]) * Math.signum(data[i + 1]) < 0) {
                crossings++;
            }
        }
        return (double) crossings / data.length;
    }

    private static double computeFZCPythonStyle(double[] data) {
        if (data == null || data.length < 2) return 0.0;
        for (int i = 0; i < data.length - 1; i++) {
            if (Math.signum(data[i]) != Math.signum(data[i+1])) {
                if (Math.signum(data[i]) == 0 && Math.signum(data[i+1]) == 0) continue;
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 주파수 특징 계산 - 파이썬 방식에 맞춤 (Detrending, PSD 스케일링, 엔트로피, 주파수 중심 수정)
     */
    public static Map<String, double[][]> calculateSpectralFeatures(double[][] magnitudeData, String prefix) {
        if (magnitudeData == null) throw new IllegalArgumentException("magnitudeData cannot be null for spectral features.");
        int rows = magnitudeData.length;
        int fs = 100; // Sampling frequency
        Map<String, double[][]> result = new HashMap<>(5);

        double[][] maxPSD = new double[rows][1];
        double[][] entropy = new double[rows][1];
        double[][] freqCenter = new double[rows][1];
        double[][] kurtosis = new double[rows][1];
        double[][] skewness = new double[rows][1];

        Kurtosis kurtosisCalc = new Kurtosis();
        Skewness skewnessCalc = new Skewness();

        for (int i = 0; i < rows; i++) {
            if (magnitudeData[i] == null || magnitudeData[i].length == 0) {
                maxPSD[i][0] = Double.NaN; entropy[i][0] = Double.NaN; freqCenter[i][0] = Double.NaN;
                kurtosis[i][0] = Double.NaN; skewness[i][0] = Double.NaN;
                continue;
            }
            double[] data = magnitudeData[i];
            double[] psd = computeWelchPSDPythonStyle(data, fs, data.length, "hann");

            if (psd.length == 0) {
                maxPSD[i][0] = Double.NaN; entropy[i][0] = Double.NaN; freqCenter[i][0] = Double.NaN;
                kurtosis[i][0] = Double.NaN; skewness[i][0] = Double.NaN;
                continue;
            }

            maxPSD[i][0] = findMax(psd);
            entropy[i][0] = computeEntropyPythonStyle(psd);
            freqCenter[i][0] = computeFrequencyCenterPythonStyle(psd, fs, data.length);

            double[] cleanPsd = Arrays.stream(psd).filter(val -> !Double.isNaN(val)).toArray();
            if (cleanPsd.length > 3) {
                kurtosis[i][0] = kurtosisCalc.evaluate(cleanPsd);
                skewness[i][0] = skewnessCalc.evaluate(cleanPsd);
            } else {
                kurtosis[i][0] = Double.NaN;
                skewness[i][0] = Double.NaN;
            }
        }

        result.put(prefix + "_max.psd", maxPSD);
        result.put(prefix + "_entropy", entropy);
        result.put(prefix + "_fc", freqCenter);
        result.put(prefix + "_kurt", kurtosis);
        result.put(prefix + "_skew", skewness);

        return result;
    }

    private static double[] computeWelchPSDPythonStyle(double[] dataInput, int fs, int npersegInput, String windowType) {
        int n = dataInput.length;
        int nperseg = npersegInput;

        if (n == 0) {
            int tempNfftForEmpty = getNextPowerOfTwo(nperseg > 0 ? nperseg : 1);
            double[] emptyPsd = new double[tempNfftForEmpty / 2 + 1];
            Arrays.fill(emptyPsd, 0.0); return emptyPsd;
        }
        if (nperseg == 0) {
            int tempNfftForZeroNperseg = getNextPowerOfTwo(1);
            double[] emptyPsd = new double[tempNfftForZeroNperseg / 2 + 1];
            Arrays.fill(emptyPsd, 0.0); return emptyPsd;
        }

        double meanVal = 0;
        for (double v : dataInput) meanVal += v;
        meanVal /= n;
        double[] data = new double[n];
        for (int i = 0; i < n; i++) { data[i] = dataInput[i] - meanVal; }

        if (n < nperseg) nperseg = n;
        int nfft = getNextPowerOfTwo(nperseg);

        double[] window;
        if ("hanning".equalsIgnoreCase(windowType) || "hann".equalsIgnoreCase(windowType)) {
            window = new double[nperseg];
            if (nperseg > 1) {
                for (int i = 0; i < nperseg; i++) {
                    window[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (double)nperseg)); // fftbins=True
                }
            } else if (nperseg == 1) { window[0] = 1.0; }
            else { window = new double[0]; } // nperseg가 0인 경우 (위에서 이미 처리됨)
        } else {
            window = new double[nperseg];
            Arrays.fill(window, 1.0);
        }

        double sumSqWindow = 0;
        for (double v : window) sumSqWindow += v * v;
        if (sumSqWindow == 0 && nperseg > 0) sumSqWindow = 1e-12; // nperseg가 0이면 window도 비어있음
        else if (sumSqWindow == 0 && nperseg == 0) sumSqWindow = 1.0; // 0으로 나누는 것 방지, 또는 다른 처리


        int noverlap = nperseg / 2;
        int step = nperseg - noverlap;
        if (step <= 0) step = (nperseg > 0) ? nperseg : 1;

        int numSegments;
        if (n < nperseg) numSegments = 1;
        else if (n < noverlap) numSegments = 1; // 이 조건은 n < nperseg에 포함될 수 있음
        else numSegments = (n - noverlap) / step;

        if (numSegments <= 0 && n >= nperseg) numSegments = 1;
        if (numSegments <= 0 && n < nperseg && n > 0) numSegments = 1;
        if (n == 0) numSegments = 0;

        if (numSegments == 0) {
            double[] emptyPsd = new double[nfft / 2 + 1];
            Arrays.fill(emptyPsd, 0.0); return emptyPsd;
        }

        double[] avgPsd = new double[nfft / 2 + 1];
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        int actualSegmentsProcessed = 0;
        for (int i = 0; i < numSegments; i++) {
            int currentSegmentStart = i * step;
            int currentSegmentLength = Math.min(nperseg, n - currentSegmentStart);

            if (currentSegmentLength <= 0) continue;
            // SciPy는 nperseg보다 짧은 마지막 세그먼트를 detrend 후 제로패딩하여 nperseg로 만듦.
            // 현재 로직은 currentSegmentLength 만큼만 window를 적용하고 나머지는 0으로 패딩하여 nfft로 만듦.

            double[] segmentForFFT = new double[nfft];
            for (int j = 0; j < currentSegmentLength; j++) {
                segmentForFFT[j] = data[currentSegmentStart + j] * window[j];
            }

            Complex[] fftResult = fft.transform(segmentForFFT, TransformType.FORWARD);
            for (int k = 0; k < avgPsd.length; k++) {
                double real = fftResult[k].getReal();
                double imag = fftResult[k].getImaginary();
                avgPsd[k] += (real * real + imag * imag);
            }
            actualSegmentsProcessed++;
        }

        if (actualSegmentsProcessed == 0) {
            Arrays.fill(avgPsd, 0.0); return avgPsd;
        }

        double scale = 1.0 / (fs * sumSqWindow);

        for (int k = 0; k < avgPsd.length; k++) {
            avgPsd[k] /= actualSegmentsProcessed;
            if (k > 0 && k < avgPsd.length - 1) {
                avgPsd[k] *= 2.0;
            }
            avgPsd[k] *= scale;
        }
        return avgPsd;
    }

    private static int getNextPowerOfTwo(int number) {
        if (number <= 0) return 1;
        int power = 1;
        while (power < number) {
            power *= 2;
            if (power <= 0) {
                throw new IllegalArgumentException("Number is too large to find next power of two: " + number);
            }
        }
        return power;
    }

    private static double findMax(double[] psd) {
        if (psd == null || psd.length == 0) return Double.NaN;
        double maxVal = Double.NEGATIVE_INFINITY;
        boolean allNaN = true;
        for (double v : psd) {
            if (!Double.isNaN(v)) {
                allNaN = false;
                if (v > maxVal) {
                    maxVal = v;
                }
            }
        }
        return allNaN ? Double.NaN : maxVal;
    }

    private static double computeEntropyPythonStyle(double[] psd) {
        if (psd == null || psd.length == 0) return Double.NaN;
        double sumPk = 0;
        int positiveCount = 0;
        for (double p_val : psd) {
            if (!Double.isNaN(p_val) && p_val > 0) {
                sumPk += p_val;
                positiveCount++;
            }
        }
        if (positiveCount == 0 || sumPk == 0) return 0.0;

        double entropy = 0;
        for (double p_val : psd) {
            if (!Double.isNaN(p_val) && p_val > 0) {
                double pk_norm = p_val / sumPk;
                entropy -= pk_norm * Math.log(pk_norm);
            }
        }
        return entropy;
    }

    private static double computeFrequencyCenterPythonStyle(double[] psd, int fs, int npersegOriginalWindowSize) {
        if (psd == null || psd.length == 0 || npersegOriginalWindowSize == 0) return Double.NaN;

        int nfft = getNextPowerOfTwo(npersegOriginalWindowSize);
        if (psd.length != (nfft / 2 + 1)) {
            if (psd.length > 1) nfft = (psd.length - 1) * 2;
            else if (psd.length == 1) nfft = (nfft == 1 || nfft == 2 || nfft==0) ? (nfft==0?2:nfft) : 2; // DC만, nfft=0이면 기본 2
            else return Double.NaN;
        }
        if (nfft == 0) nfft = 2; // 최소 nfft=2 (DC, Nyquist) 가정 (psd.length=2 일때)


        double sumPsd = 0;
        double weightedSum = 0;
        double freqStep = (double) fs / nfft;

        for (int k = 0; k < psd.length; k++) {
            if (!Double.isNaN(psd[k])) {
                sumPsd += psd[k];
                weightedSum += (k * freqStep) * psd[k];
            }
        }
        if (Math.abs(sumPsd) < 1e-12) return 0.0;
        return weightedSum / sumPsd;
    }
}
package com.example.myapplication12345.AI;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PyTorchHelper {
    private static final String TAG = "PyTorchHelper";
    private static final String MODEL_FILENAME = "model.pt";
    private static final String NPZ_FILENAME = "processed_data.npz";
    private static final String[] TRANSPORT_MODES = {
            "WALK", "BIKE", "BUS", "CAR", "SUBWAY", "ETC", "OTHER1", "OTHER2", "OTHER3", "OTHER4", "OTHER5"
    };
    private Module model;
    private Context context;
    private String predictedResult; // 예측 결과를 저장하는 멤버 변수

    public PyTorchHelper(Context context) {
        this.context = context;
        this.predictedResult = "알 수 없음"; // 초기값 설정
        try {
            String modelPath = assetFilePath(context, MODEL_FILENAME);
            model = Module.load(modelPath);
            Log.d(TAG, "PyTorch 모델 로드 완료: " + modelPath);
        } catch (IOException e) {
            Log.e(TAG, "모델 파일 복사 오류: " + e.getMessage(), e);
            Toast.makeText(context, "모델 로드 실패", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "모델 로드 중 오류: " + e.getMessage(), e);
            Toast.makeText(context, "모델 초기화 실패", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Assets에서 파일을 내부 저장소로 복사
     */
    private String assetFilePath(Context context, String filename) throws IOException {
        File file = new File(context.getFilesDir(), filename);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(filename);
             FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Assets 파일 복사 실패: " + filename, e);
            throw e;
        }
        return file.getAbsolutePath();
    }

    /**
     * .npz 파일에서 데이터 로드 (Tensor 입력을 사용할 경우 필요 없을 수 있음)
     */
    private float[] loadNpzData(String npzPath) throws IOException {
        File npzFile = new File(npzPath);
        try (FileInputStream fis = new FileInputStream(npzFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("value.npy")) {
                    ByteBuffer buffer = ByteBuffer.allocate((int) entry.getSize());
                    byte[] bytes = new byte[4096];
                    int read;
                    while ((read = zis.read(bytes)) != -1) {
                        buffer.put(bytes, 0, read);
                    }
                    buffer.flip();
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    float[] data = new float[buffer.remaining() / 4];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = buffer.getFloat();
                    }
                    Log.d(TAG, "NPZ 데이터 로드 완료, 크기: " + data.length);
                    return data;
                }
            }
        }
        throw new IOException("NPZ 파일에서 'value' 데이터를 찾을 수 없음");
    }

    /**
     * Tensor 입력을 받아 모델 예측 실행
     */
    public void predictMovingMode(Tensor inputTensor) {
        try {
            // 1. 입력 데이터 확인
            long[] inputShape = inputTensor.shape();
            Log.d(TAG, "✅ 입력 텐서 크기: " + Arrays.toString(inputShape));

            // 입력 크기 검증 (선택적)
            int batchSize = (int) inputShape[0];
            int features = (int) inputShape[1];
            int timeSteps = (int) inputShape[2];
            if (features != 340 || timeSteps != 60) {
                Log.w(TAG, "입력 크기 예상과 다름: 예상 [X, 340, 60], 실제 " + Arrays.toString(inputShape));
            }

            // 2. 모델 추론
            Tensor outputTensor = model.forward(IValue.from(inputTensor)).toTensor();
            Log.d(TAG, "✅ 출력 텐서 크기: " + Arrays.toString(outputTensor.shape()));

            // 3. 결과 처리
            float[] logits = outputTensor.getDataAsFloatArray();
            float[] probabilities = softmax(logits);
            Log.d(TAG, "전체 확률 값: " + Arrays.toString(probabilities));

            // 4. 오류 체크
            if (probabilities.length != 11 * batchSize) {
                Log.e(TAG, "출력 크기 불일치: " + probabilities.length + ", 예상: " + (11 * batchSize));
                predictedResult = "알 수 없음 (출력 오류)";
                Toast.makeText(context, predictedResult, Toast.LENGTH_LONG).show();
                return;
            }
            for (float prob : probabilities) {
                if (Float.isNaN(prob)) {
                    Log.e(TAG, "NaN 값 감지");
                    predictedResult = "알 수 없음 (NaN 오류)";
                    Toast.makeText(context, predictedResult, Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // 5. 최대 확률 클래스 선택 (첫 번째 샘플만 처리)
            int maxIndex = 0;
            float maxProb = probabilities[0];
            for (int i = 1; i < 11; i++) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i];
                    maxIndex = i;
                }
            }

            // 6. 결과 저장
            float threshold = 0.9f;
            if (maxProb >= threshold) {
                predictedResult = TRANSPORT_MODES[maxIndex];
                Log.d(TAG, "예측된 이동수단: " + predictedResult + ", 확률: " + maxProb);
            } else {
                predictedResult = "알 수 없음 (확률 낮음)";
                Log.w(TAG, "확률 낮음: " + maxProb);
            }

        } catch (Exception e) {
            Log.e(TAG, "예측 중 오류: " + e.getMessage(), e);
            predictedResult = "알 수 없음 (예측 실패)";
            Toast.makeText(context, predictedResult, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * .npz 파일을 사용하는 기존 메서드 (옵션으로 유지)
     */
    public void predictMovingMode() {
        try {
            String npzPath = assetFilePath(context, NPZ_FILENAME);
            float[] inputData = loadNpzData(npzPath);
            int timeSteps = 60;
            int totalElements = inputData.length;
            int features = 340;
            int batchSize = totalElements / (features * timeSteps);

            if (totalElements % (features * timeSteps) != 0) {
                int expectedSize = batchSize * features * timeSteps;
                if (totalElements > expectedSize) {
                    inputData = Arrays.copyOf(inputData, expectedSize);
                    Log.w(TAG, "데이터 크기 조정: " + totalElements + " -> " + expectedSize);
                } else {
                    Log.e(TAG, "입력 데이터 크기 불일치: " + totalElements);
                    predictedResult = "알 수 없음 (크기 오류)";
                    Toast.makeText(context, predictedResult, Toast.LENGTH_LONG).show();
                    return;
                }
            }

            long[] inputShape = {batchSize, features, timeSteps};
            Tensor inputTensor = Tensor.fromBlob(inputData, inputShape);
            predictMovingMode(inputTensor); // Tensor 입력 메서드 호출
        } catch (IOException e) {
            Log.e(TAG, "NPZ 로드 실패: " + e.getMessage(), e);
            predictedResult = "알 수 없음 (데이터 로드 실패)";
            Toast.makeText(context, predictedResult, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 소프트맥스 변환
     */
    private float[] softmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) {
                maxLogit = logit;
            }
        }

        float sum = 0.0f;
        float[] expLogits = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            expLogits[i] = (float) Math.exp(logits[i] - maxLogit);
            sum += expLogits[i];
        }

        float[] probabilities = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = expLogits[i] / sum;
        }
        return probabilities;
    }

    /**
     * 예측 결과 Getter
     */
    public String getPredictedResult() {
        return predictedResult;
    }

    /**
     * 예측 결과 Setter (나중에 설정 가능)
     */
    public void setPredictedResult(String result) {
        this.predictedResult = result;
        Log.d(TAG, "예측 결과 설정: " + result);
    }

    /**
     * 결과를 Toast로 출력하는 메서드
     */
    public void showResult() {
        Toast.makeText(context, "이동수단: " + predictedResult, Toast.LENGTH_SHORT).show();
    }

    /**
     * 안드로이드 Activity에서 호출 예시
     */
    public static void runPrediction(Context context) {
        PyTorchHelper helper = new PyTorchHelper(context);

        // Tensor 입력 예시
        float[] sampleData = new float[20400]; // [1, 340, 60] 크기 가정
        Arrays.fill(sampleData, 0.1f); // 테스트 데이터
        Tensor inputTensor = Tensor.fromBlob(sampleData, new long[]{1, 340, 60});
        helper.predictMovingMode(inputTensor);

        // 결과 확인 및 나중에 설정
        String result = helper.getPredictedResult();
        Log.d(TAG, "저장된 결과: " + result);
        helper.showResult(); // 필요 시 출력

        // 나중에 결과 변경 예시
        helper.setPredictedResult("BUS");
        helper.showResult();
    }

    //사용 방법
//    PyTorchHelper helper = new PyTorchHelper(this);
//    float[] data = new float[20400]; // 실제 데이터로 채움
//    Tensor tensor = Tensor.fromBlob(data, new long[]{1, 340, 60});
//    helper.predictMovingMode(tensor);
//    String result = helper.getPredictedResult();
}
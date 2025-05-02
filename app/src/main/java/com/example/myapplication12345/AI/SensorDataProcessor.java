package com.example.myapplication12345.AI;

import android.annotation.SuppressLint;
import android.content.Context;

import com.example.myapplication12345.AI.AP.APProcessor;
import com.example.myapplication12345.AI.BTS.BTSProcessor;
import com.example.myapplication12345.AI.GPS.GPSProcessor;
import com.example.myapplication12345.AI.IMU.IMUProcessor;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import timber.log.Timber;

public class SensorDataProcessor {
    private static final String TAG = "SensorDataProcessor";
    private static final String MODEL_FILENAME = "model.ptl"; // 모델 파일 이름 (동일하다고 가정)
    // 모델 입력 특성 크기 (예: 334 또는 340) - 명확성을 위해 이름 변경, 기존 값 유지
    private static final int MODEL_INPUT_FEATURE_SIZE = 340;
    private static final int MIN_TIMESTAMP_COUNT = 60; // 필요한 최소 타임스탬프 개수 (기존 값 유지)
    private static final int SEGMENT_SIZE = 10; // CSV 저장 시 세그먼트 크기 (기존 값 유지)

    // --- 업데이트된 이동 수단 매핑 ---
    // 새로운 라벨 명세(인덱스 0-12)에 해당
    private static final String[] TRANSPORT_MODES = {
            "WALK",        // 0: 걷기
            "RUN",         // 1: 달리기
            "BIKE",        // 2: 자전거
            "CAR",         // 3: 차량 (모델이 구분하지 않는 한 택시 포함)
            "BUS",         // 4: 버스
            "TRAIN",       // 5: KTX/기차
            "SUBWAY",      // 6: 지하철
            "MOTORCYCLE",  // 7: 오토바이
            "E_BIKE",      // 8: 전기자전거
            "E_SCOOTER",   // 9: 전동 킥보드
            "TAXI"         // 10: 택시 (명시적으로 추가)
    };
    // 모델에서 예상되는 출력 클래스 개수
    private static final int EXPECTED_MODEL_OUTPUT_SIZE = TRANSPORT_MODES.length; // 13이어야 함

    // --- 기본값 ---
    private static final String DEFAULT_MODE_UNKNOWN = "ETC"; // 낮은 신뢰도 또는 오류 시 사용
    private static final String DEFAULT_MODE_STOPPED = "STOP"; // 움직임이 거의 없을 때 사용

    private static final SimpleDateFormat dateFormat;

    static {
        dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    }

    private static SensorDataProcessor instance;
    private final Context context;
    private Module model;
    private String lastPredictedResult = DEFAULT_MODE_UNKNOWN; // 마지막 유효 예측 결과 저장
    private volatile boolean isModelLoaded = false; // 모델 로딩 완료 여부

    public static synchronized SensorDataProcessor getInstance(Context context) {
        if (instance == null) {
            instance = new SensorDataProcessor(context.getApplicationContext()); // Application Context 사용 권장
        }
        return instance;
    }

    private SensorDataProcessor(Context context) {
        this.context = context;
        loadModelAsync(); // 비동기 모델 로딩 시작
    }

    // 모델 비동기 로딩
    private void loadModelAsync() {
        new Thread(() -> {
            try {
                String modelPath = assetFilePath(context);
                model = Module.load(modelPath);
                isModelLoaded = true;
                Timber.tag(TAG).d("PyTorch 모델 로드 완료: %s", modelPath);
            } catch (IOException e) {
                Timber.tag(TAG).e(e, "모델 파일 복사 오류: %s", e.getMessage());
                // 사용자 알림 또는 나중에 다시 로드 시도 고려
            } catch (Exception e) {
                Timber.tag(TAG).e(e, "모델 로드 중 오류: %s", e.getMessage());
                // 사용자 알림 또는 나중에 다시 로드 시도 고려
            }
            // 여기서 IllegalStateException을 발생시키지 않아 모델 없이도 앱이 실행될 가능성 허용
        }).start();
    }

    // Assets 폴더의 모델 파일을 내부 저장소로 복사하고 경로 반환
    private String assetFilePath(Context context) throws IOException {
        File file = new File(context.getFilesDir(), SensorDataProcessor.MODEL_FILENAME);
        // 파일 존재 여부만 확인 (Module.load가 손상된 파일 처리)
        // 파일이 없으면 항상 assets에서 복사하여 무결성 보장
        if (!file.exists()) {
            Timber.tag(TAG).d("내부 저장소에 모델 파일 없음, assets에서 복사 시작.");
            try (InputStream is = context.getAssets().open(SensorDataProcessor.MODEL_FILENAME);
                 FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                Timber.tag(TAG).d("모델 파일 복사 완료: %s", file.getAbsolutePath());
            } catch (IOException e) {
                Timber.tag(TAG).e(e, "assets에서 모델 파일 복사 실패.");
                throw e; // 예외 다시 던지기
            }
        } else {
            Timber.tag(TAG).d("내부 저장소에서 모델 파일 발견: %s", file.getAbsolutePath());
        }
        return file.getAbsolutePath();
    }

    /**
     * 센서 데이터를 받아 전처리 후 이동 수단을 예측하고 결과를 저장합니다.
     * @param gpsData GPS 데이터 리스트
     * @param apData AP 데이터 리스트
     * @param btsData BTS 데이터 리스트
     * @param imuData IMU 데이터 리스트
     */
    public void processSensorData(List<Map<String, Object>> gpsData,
                                  List<Map<String, Object>> apData,
                                  List<Map<String, Object>> btsData,
                                  List<Map<String, Object>> imuData) {

        // 모델 로딩 완료 여부 확인
        if (!isModelLoaded) {
            Timber.tag(TAG).w("모델이 아직 로드되지 않음. 데이터 처리 스킵");
            // lastPredictedResult는 이전 값 또는 기본값 유지
            return;
        }

        // 모델 객체 null 여부 확인
        if (model == null) {
            Timber.tag(TAG).e("모델 로딩 시도 후에도 model 객체가 null임. 예측 스킵.");
            return;
        }

        // 거리 먼저 계산
        MovementAnalyzer analyzer = new MovementAnalyzer(gpsData, imuData);
        analyzer.analyze();
        float distance = analyzer.getDistance(); // 해당 배치의 총 이동 거리

        Timber.tag(TAG).d("수신된 데이터 크기 - GPS: %d, AP: %d, BTS: %d, IMU: %d",
                (gpsData != null ? gpsData.size() : 0),
                (apData != null ? apData.size() : 0),
                (btsData != null ? btsData.size() : 0),
                (imuData != null ? imuData.size() : 0));

        // 데이터 유효성 검사 (null 또는 최소 개수 미만) *처리 전* 수행
        if (gpsData == null || gpsData.size() < MIN_TIMESTAMP_COUNT ||
                apData == null || apData.isEmpty() || // AP/BTS는 모델 요구사항에 따라 선택적일 수 있음
                btsData == null || btsData.isEmpty() ||
                imuData == null || imuData.size() < MIN_TIMESTAMP_COUNT) {
            Timber.tag(TAG).w("필요한 최소 데이터 요구사항 충족되지 않음 (GPS >= %d, IMU >= %d, AP/BTS 비어있지 않아야 함)", MIN_TIMESTAMP_COUNT, MIN_TIMESTAMP_COUNT);
            // 예측 불가 시 동작 정의: 마지막 예측 유지 또는 STOP/ETC 설정? -> 안정성을 위해 마지막 유효값 유지 고려.
            // 단, 거리가 매우 작으면 나중에 덮어쓸 수 있음.
            // GPS 데이터가 충분하면, 마지막 모드 또는 STOP으로 세그먼트 저장 시도.
            saveSegmentsWithFallback(gpsData, distance, DEFAULT_MODE_STOPPED); // 데이터 부족 시 STOP으로 저장
            return;
        }

        // --- 데이터 전처리 ---
        // 리스트를 수정할 수 있는 처리 전에 가장 빠른 타임스탬프 찾기
        long apTimestamp = findEarliestTimestamp(apData);
        long btsTimestamp = findEarliestTimestamp(btsData);
        long gpsTimestamp = findEarliestTimestamp(gpsData);
        // IMU 처리는 자체 로직에 따라 타임스탬프 필요 여부 결정

        List<Map<String, Object>> processedAP = APProcessor.processAP(apData, apTimestamp);
        List<Map<String, Object>> processedBTS = BTSProcessor.processBTS(btsData, btsTimestamp);
        List<Map<String, Object>> processedGPS = GPSProcessor.processGPS(gpsData, gpsTimestamp);
        List<Map<String, Object>> processedIMU = IMUProcessor.preImu(imuData); // preImu가 필요시 타임스탬프 처리 가정

        // 전처리 후 데이터 유효성 검사
        if (processedAP.isEmpty() || processedBTS.isEmpty() || processedGPS.isEmpty() || processedIMU.isEmpty()) {
            Timber.tag(TAG).w("데이터 전처리 실패 - 하나 이상의 센서 데이터가 전처리 후 비어 있음");
            saveSegmentsWithFallback(gpsData, distance, DEFAULT_MODE_STOPPED); // 처리 실패 시 STOP으로 저장
            return;
        }

        // --- 특성 벡터 생성 및 예측 ---
        try {
            Tensor inputTensor = getProcessedFeatureVector(processedAP, processedBTS, processedGPS, processedIMU);
            if (inputTensor == null) {
                Timber.tag(TAG).e("입력 텐서 생성 실패");
                saveSegmentsWithFallback(gpsData, distance, DEFAULT_MODE_STOPPED); // 텐서 생성 실패 시 STOP으로 저장
                return;
            }
            // predictMovingMode 내부에서 lastPredictedResult 업데이트 및 세그먼트 저장
            predictMovingMode(inputTensor, gpsData, distance);
        } catch (Exception e) {
            Timber.tag(TAG).e(e, "데이터 처리 또는 예측 중 오류 발생");
            saveSegmentsWithFallback(gpsData, distance, DEFAULT_MODE_UNKNOWN); // 일반 오류 시 ETC로 저장
        }
    }

    /**
     * 전처리된 센서 데이터 리스트들을 받아 모델 입력에 맞는 텐서 형태로 변환합니다.
     * 각 타임스텝(열)마다 모든 센서의 특성(feature)들을 합쳐 지정된 크기(MODEL_INPUT_FEATURE_SIZE)의 행을 만듭니다.
     * @param apData 전처리된 AP 데이터
     * @param btsData 전처리된 BTS 데이터
     * @param gpsData 전처리된 GPS 데이터
     * @param imuData 전처리된 IMU 데이터
     * @return 모델 입력 텐서 ([1, numFeatures, numTimesteps] 형태) 또는 생성 실패 시 null
     */
    private Tensor getProcessedFeatureVector(List<Map<String, Object>> apData,
                                             List<Map<String, Object>> btsData,
                                             List<Map<String, Object>> gpsData,
                                             List<Map<String, Object>> imuData) {
        // 데이터 정렬 (프로세서가 순서 보장 안 할 경우 대비) 및 타임스탬프 제거 (복사본 사용)
        List<Map<String, Object>> sortedAP = sortAndRemoveTimestamp(new ArrayList<>(apData));
        List<Map<String, Object>> sortedBTS = sortAndRemoveTimestamp(new ArrayList<>(btsData));
        List<Map<String, Object>> sortedGPS = sortAndRemoveTimestamp(new ArrayList<>(gpsData));
        List<Map<String, Object>> sortedIMU = sortAndRemoveTimestamp(new ArrayList<>(imuData));

        // 정렬/타임스탬프 제거 후 크기 재확인 (이전 검사가 더 좋음)
        if (sortedGPS.isEmpty() || sortedIMU.isEmpty()) {
            Timber.tag(TAG).w("정렬/타임스탬프 제거 후 GPS 또는 IMU 데이터가 비어있음.");
            return null; // 텐서 생성 실패 알림
        }

        List<Map<String, Object>> combinedData = new ArrayList<>();
        final int repetitionsPerGpsRecord = 5; // GPS 레코드당 반복 횟수 (기존 로직 유지)

        // 필요한 타임스탬프 개수(MIN_TIMESTAMP_COUNT)만큼 반복
        for (int i = 0; i < MIN_TIMESTAMP_COUNT; i++) {
            Map<String, Object> row = new LinkedHashMap<>(); // 순서 보장

            int gpsIndex = i / repetitionsPerGpsRecord;
            // 안전하게 데이터 가져오기 (인덱스 벗어나면 마지막 유효 레코드 사용)
//            row.putAll(getWithFallback(sortedGPS, gpsIndex)); // GPS 데이터 추가
            row.putAll(getWithFallback(sortedAP, 0));
            row.putAll(getWithFallback(sortedBTS, i % Math.min(12, sortedBTS.size())));
            row.putAll(getWithFallback(sortedGPS, i % Math.min(12, sortedGPS.size())));
            row.putAll(getWithFallback(sortedIMU, i));

            // --- 중요: 모델이 AP/BTS 특성을 요구하는 경우 아래 주석 해제 및 조정 필요 ---
            // 예시: 항상 첫 번째 AP 데이터 사용
            // row.putAll(getWithFallback(sortedAP, 0));
            // 예시: BTS 데이터를 순환하며 사용
            // row.putAll(getWithFallback(sortedBTS, i % sortedBTS.size()));
            // --- 중요: AP/BTS 추가 시 총 특성 수가 MODEL_INPUT_FEATURE_SIZE와 일치해야 함 ---

            combinedData.add(row); // 완성된 타임스텝 데이터 추가
        }
        // Timber.tag(TAG).d("결합된 데이터 행 예시 (0): %s", combinedData.get(0).toString());
        return convertListMapToTensor(combinedData); // 최종 텐서 변환
    }

    // 리스트에서 안전하게 데이터를 가져오는 헬퍼 함수 (인덱스 초과 시 마지막 요소 사용)
    private static Map<String, Object> getWithFallback(List<Map<String, Object>> list, int index) {
        if (list == null || list.isEmpty()) return new HashMap<>(); // 리스트가 null이거나 비었으면 빈 맵 반환
        int actualIndex = Math.min(index, list.size() - 1); // index 또는 마지막 유효 인덱스 사용
        return list.get(actualIndex);
    }

    /**
     * 여러 센서 데이터가 결합된 리스트를 PyTorch 모델 입력 형식의 텐서로 변환합니다.
     * 텐서의 각 열(타임스텝)은 여러 센서에서 나온 특성(feature)들을 순서대로 포함하며,
     * 총 특성 수가 MODEL_INPUT_FEATURE_SIZE와 일치하도록 필요시 0.0f로 패딩합니다.
     * @param dataList 각 요소가 한 타임스텝의 모든 센서 특성을 포함하는 Map인 리스트
     * @return 모델 입력 텐서 ([1, MODEL_INPUT_FEATURE_SIZE, MIN_TIMESTAMP_COUNT]) 또는 null (오류 시)
     */
    private static Tensor convertListMapToTensor(List<Map<String, Object>> dataList) {
        // 텐서 형태 정의를 위해 상수 사용
        int numFeatures = MODEL_INPUT_FEATURE_SIZE; // 텐서의 행 (특성 차원)
        int numTimesteps = MIN_TIMESTAMP_COUNT;     // 텐서의 열 (시간 차원)

        float[] dataArray = new float[numFeatures * numTimesteps]; // 1차원 배열로 데이터 저장
        int dataArrayIndex = 0; // 현재 dataArray에 채워넣을 인덱스

        // 입력 데이터 리스트의 타임스텝 수가 요구사항보다 적은지 확인
        if (dataList.size() < numTimesteps) {
            Timber.tag(TAG).w("결합된 데이터 리스트 크기(%d)가 필요한 타임스텝(%d)보다 작음. 패딩 처리됩니다.", dataList.size(), numTimesteps);
            // 아래 패딩 로직이 처리하지만, 로그로 남김
        }

        // 타임스텝 (열) 순회
        for (int t = 0; t < numTimesteps; t++) {
            // 현재 타임스텝(t)에 해당하는 데이터 가져오기 (없으면 빈 Map -> 패딩용)
            Map<String, Object> timestepData = (t < dataList.size()) ? dataList.get(t) : new HashMap<>();

            int featuresInThisTimestep = 0; // 현재 타임스텝에서 추출된 특성 수

            // 현재 타임스텝 데이터(Map)의 모든 값(특성)을 순회하며 dataArray에 추가
            // 중요: 이 로직은 getProcessedFeatureVector에서 Map에 데이터를 넣은 순서가
            //       모델이 기대하는 특성 순서와 일치한다고 가정합니다.
            for (Object value : timestepData.values()) {
                // 배열 범위 내이고 숫자 타입인 경우에만 추가
                if (dataArrayIndex < dataArray.length && value instanceof Number) {
                    dataArray[dataArrayIndex++] = ((Number) value).floatValue();
                    featuresInThisTimestep++;
                }
            }

            // --- 핵심 패딩 로직 ---
            // 현재 타임스텝에서 추출된 특성 수가 numFeatures보다 적으면 0.0f로 패딩
            if (featuresInThisTimestep < numFeatures) {
                // Timber.tag(TAG).v("타임스텝 %d: 특성 %d개 발견, %d개 패딩 필요.", t, featuresInThisTimestep, numFeatures - featuresInThisTimestep);
                for (int p = featuresInThisTimestep; p < numFeatures; p++) {
                    if (dataArrayIndex < dataArray.length) {
                        dataArray[dataArrayIndex++] = 0.0f; // 부족한 특성 0으로 채우기
                    } else {
                        // 이 경우는 발생하면 안 됨 (배열 크기 계산 오류 의미)
                        Timber.tag(TAG).e("오류: 타임스텝 %d 특성 패딩 중 텐서 버퍼 오버플로우.", t);
                        return null; // 텐서 생성 실패
                    }
                }
            } else if (featuresInThisTimestep > numFeatures) {
                // 이 경우도 발생하면 안 됨 (getProcessedFeatureVector 로직 오류 의미)
                Timber.tag(TAG).e("오류: 타임스텝 %d에 특성 %d개가 발견되어 예상치(%d) 초과.", t, featuresInThisTimestep, numFeatures);
                return null; // 오류 알림
            }
            // featuresInThisTimestep == numFeatures 인 경우는 정상
        }

        // 최종 검증: dataArray가 완전히 채워졌는지 확인
        if (dataArrayIndex != numFeatures * numTimesteps) {
            Timber.tag(TAG).e("텐서 버퍼 채우기 불일치. 예상 크기: %d, 실제 채워진 크기: %d.", numFeatures * numTimesteps, dataArrayIndex);
            return null; // 텐서 생성 실패
        }

        // 최종 텐서 생성: 형태 [1, 특성 수, 타임스텝 수]
        return Tensor.fromBlob(dataArray, new long[]{1, numFeatures, numTimesteps});
    }


    /**
     * 입력된 데이터 리스트의 복사본을 타임스탬프 기준으로 정렬하고, 각 Map에서 "timestamp" 키를 제거합니다.
     * @param dataList 원본 데이터 리스트
     * @return 타임스탬프 제거 및 정렬된 새 리스트
     */
    private List<Map<String, Object>> sortAndRemoveTimestamp(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return new ArrayList<>(); // 입력이 null이거나 비었으면 빈 리스트 반환
        }
        // 원본 수정을 피하기 위해 복사본 생성
        List<Map<String, Object>> sortedList = new ArrayList<>(dataList);

        // 타임스탬프 기준 정렬 (null 안전 처리 포함)
        try {
            Collections.sort(sortedList, Comparator.comparingLong(m ->
                    (m != null && m.get("timestamp") instanceof Number) ?
                            ((Number) m.get("timestamp")).longValue() : Long.MAX_VALUE // null 이거나 timestamp 없으면 맨 뒤로
            ));
        } catch (Exception e) {
            Timber.tag(TAG).e(e, "타임스탬프 정렬 중 오류 발생");
            // 정렬 실패 시 원본 순서의 복사본 반환 (또는 다른 오류 처리)
            return new ArrayList<>(dataList);
        }


        // 정렬된 복사본 리스트의 각 Map에서 "timestamp" 제거
        for (Map<String, Object> data : sortedList) {
            if (data != null) {
                data.remove("timestamp");
            }
        }
        return sortedList;
    }


    /**
     * 예측이 불가능하거나 신뢰도가 낮을 때, GPS 데이터를 기반으로 세그먼트를 저장하는 함수.
     * @param gpsData GPS 데이터 리스트
     * @param totalDistance 해당 배치의 총 이동 거리 (STOP 판정 기준)
     * @param fallbackMode 사용할 대체 이동 수단 (예: DEFAULT_MODE_STOPPED, DEFAULT_MODE_UNKNOWN)
     */
    private void saveSegmentsWithFallback(List<Map<String, Object>> gpsData, float totalDistance, String fallbackMode) {
        // 세그먼트 생성을 위한 최소 GPS 데이터 개수 확인
        if (gpsData == null || gpsData.size() < SEGMENT_SIZE) {
            Timber.tag(TAG).w("GPS 데이터 부족 (%d개)하여 세그먼트 저장 불가 (Fallback)", gpsData != null ? gpsData.size() : 0);
            return;
        }

        int totalSegments = gpsData.size() / SEGMENT_SIZE; // 생성 가능한 총 세그먼트 수
        // 세그먼트당 대략적인 이동 거리 계산 (정확하지 않을 수 있음)
        double distancePerSegment = (totalSegments > 0) ? (double)totalDistance / totalSegments : 0.0;

        Timber.tag(TAG).d("대체 모드(%s)로 %d개 세그먼트 저장 시도", fallbackMode, totalSegments);

        for (int segment = 0; segment < totalSegments; segment++) {
            int startIndex = segment * SEGMENT_SIZE;
            int endIndex = startIndex + SEGMENT_SIZE - 1; // 세그먼트 크기(SEGMENT_SIZE) 보장

            // 인덱스 유효성 재확인 (루프 조건상 불필요하나 안전 장치)
            if (startIndex >= gpsData.size() || endIndex >= gpsData.size()) {
                Timber.tag(TAG).w("세그먼트 %d에 대한 인덱스(%d-%d)가 GPS 데이터 크기(%d)를 벗어남", segment, startIndex, endIndex, gpsData.size());
                continue; // 잘못된 인덱스면 해당 세그먼트 건너뛰기
            }

            Map<String, Object> startData = gpsData.get(startIndex);
            Map<String, Object> endData = gpsData.get(endIndex);

            try {
                // 데이터 안전하게 추출 (null 발생 시 예외 처리)
                long startTimestamp = ((Number) Objects.requireNonNull(startData.get("timestamp"), "시작 타임스탬프 null")).longValue();
                double startLat = ((Number) Objects.requireNonNull(startData.get("latitude"), "시작 위도 null")).doubleValue();
                double startLon = ((Number) Objects.requireNonNull(startData.get("longitude"), "시작 경도 null")).doubleValue();
                double endLat = ((Number) Objects.requireNonNull(endData.get("latitude"), "종료 위도 null")).doubleValue();
                double endLon = ((Number) Objects.requireNonNull(endData.get("longitude"), "종료 경도 null")).doubleValue();

                // 세그먼트의 최종 이동 수단 결정
                String segmentMode = fallbackMode; // 기본적으로 전달된 fallback 사용
                // 만약 전체 거리가 매우 작으면, fallbackMode와 관계없이 STOP으로 강제 설정
                if (totalDistance <= 0.05) { // 단순화를 위해 전체 배치 거리 기준 사용
                    segmentMode = DEFAULT_MODE_STOPPED;
                }

                // CSV 저장 함수 호출
                savePredictionToCSV(segmentMode, distancePerSegment, startTimestamp, startLat, startLon, endLat, endLon);

            } catch (NullPointerException e) {
                Timber.tag(TAG).e(e, "세그먼트 %d 데이터 추출 중 NullPointerException 발생", segment);
                // 해당 세그먼트 저장 실패 처리
            } catch (Exception e) {
                Timber.tag(TAG).e(e, "세그먼트 %d 처리 중 예외 발생", segment);
                // 해당 세그먼트 저장 실패 처리
            }
        }
    }


    /**
     * 예측된 이동 수단 및 관련 정보를 CSV 파일에 저장합니다.
     * 파일은 날짜별로 생성되며, 데이터는 기존 파일에 추가됩니다.
     * @param transportMode 예측된 이동 수단 문자열
     * @param distance 이동 거리 (미터)
     * @param startTimestamp 세그먼트 시작 타임스탬프
     * @param startLat 세그먼트 시작 위도
     * @param startLon 세그먼트 시작 경도
     * @param endLat 세그먼트 종료 위도
     * @param endLon 세그먼트 종료 경도
     */
    @SuppressLint("DefaultLocale")
    private void savePredictionToCSV(String transportMode, double distance, long startTimestamp,
                                     double startLat, double startLon, double endLat, double endLon) {
        String date = dateFormat.format(startTimestamp); // 시작 타임스탬프 기준으로 날짜 폴더 사용
        String fileName = date + "_predictions.csv";

        File directory = new File(context.getExternalFilesDir(null), "Map");
        // 디렉토리 존재 확인 및 생성
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Timber.tag(TAG).e("Map 디렉토리 생성 실패");
                return; // 디렉토리 생성 실패 시 저장 불가
            }
            Timber.tag(TAG).d("Map 디렉토리 생성 성공: %s", directory.getAbsolutePath());
        }

        File file = new File(directory, fileName);
        boolean writeHeader = !file.exists() || file.length() == 0; // 새 파일이거나 비어있으면 헤더 작성

        // try-with-resources 사용하여 FileWriter 자동 종료
        try (FileWriter writer = new FileWriter(file, true)) { // true: 이어쓰기 모드
            if (writeHeader) {
                // CSV 헤더 작성
                writer.append("start_timestamp,transport_mode,distance_meters,start_latitude,start_longitude,end_latitude,end_longitude\n");
            }
            // 데이터 행 추가 (Locale.US 사용하여 소수점 '.' 보장)
            writer.append(String.format(Locale.US, "%d,%s,%.2f,%.6f,%.6f,%.6f,%.6f\n",
                    startTimestamp, transportMode, distance, startLat, startLon, endLat, endLon));
            // 저장 성공 로그 (verbose 레벨로 변경 또는 제거 가능)
            // Timber.tag(TAG).v("세그먼트 저장 완료 (%s): 모드=%s, 거리=%.2fm", fileName, transportMode, distance);
        } catch (IOException e) {
            Timber.tag(TAG).e(e, "예측 결과 CSV 저장 실패: %s", e.getMessage());
        }
    }

    /**
     * 입력 텐서를 사용하여 모델 추론을 수행하고, 결과를 처리하여 이동 수단을 결정한 후,
     * GPS 데이터를 세그먼트로 나누어 각 세그먼트 정보를 CSV 파일에 저장합니다.
     * @param inputTensor 모델 입력 텐서
     * @param gpsData 원본 GPS 데이터 리스트 (세그먼트 분할 및 정보 추출용)
     * @param totalDistance 해당 배치의 총 이동 거리 (STOP 판정용)
     */
    private void predictMovingMode(Tensor inputTensor, List<Map<String, Object>> gpsData, float totalDistance) {
        // model null 체크는 processSensorData에서 이미 수행됨

        try {
            // --- 모델 추론 ---
            IValue output = model.forward(IValue.from(inputTensor));
            Tensor outputTensor = output.toTensor();
            float[] logits = outputTensor.getDataAsFloatArray();

            // --- 출력 유효성 검증 ---
            if (logits.length != EXPECTED_MODEL_OUTPUT_SIZE) {
                Timber.tag(TAG).e("모델 출력 크기가 예상(%d)과 다름: %d", EXPECTED_MODEL_OUTPUT_SIZE, logits.length);
                lastPredictedResult = DEFAULT_MODE_UNKNOWN; // 예상치 못한 출력이면 ETC로 설정
                saveSegmentsWithFallback(gpsData, totalDistance, lastPredictedResult); // ETC로 세그먼트 저장
                return;
            }

            // --- 출력 처리 (Softmax 및 최대 확률 인덱스 찾기) ---
            float[] probabilities = softmax(logits);
            int maxIndex = 0; // 최대 확률을 가진 클래스의 인덱스
            float maxProb = probabilities[0]; // 최대 확률 값
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i];
                    maxIndex = i;
                }
            }

            // --- 예측된 이동 수단 결정 ---
            String predictedMode;
            float threshold = 0.9f; // 예측 신뢰도 임계값
            // 확률이 임계값 이상이고 인덱스가 유효 범위 내인 경우
            if (maxProb >= threshold && maxIndex >= 0 && maxIndex < TRANSPORT_MODES.length) {
                predictedMode = TRANSPORT_MODES[maxIndex]; // 매핑된 이동 수단 문자열 사용
                Timber.tag(TAG).d("예측된 이동수단: %s (인덱스: %d), 확률: %.4f", predictedMode, maxIndex, maxProb);
            } else {
                // 신뢰도가 낮거나 인덱스가 유효하지 않은 경우
                predictedMode = DEFAULT_MODE_UNKNOWN; // "ETC" 사용
                Timber.tag(TAG).w("확률(%.4f)이 임계값(%.1f) 미만이거나 유효하지 않은 인덱스(%d). '%s' 사용", maxProb, threshold, maxIndex, predictedMode);
            }

            // 클래스 멤버 변수(마지막 예측 결과) 업데이트
            lastPredictedResult = predictedMode;

            // --- 세그먼트 저장 로직 ---
            // GPS 데이터가 세그먼트화하기에 충분한지 확인
            if (gpsData != null && gpsData.size() >= MIN_TIMESTAMP_COUNT) { // 혹은 SEGMENT_SIZE 기준으로 체크? 여기선 MIN_TIMESTAMP_COUNT 사용
                int totalSegments = gpsData.size() / SEGMENT_SIZE;
                // 세그먼트당 대략적인 이동 거리
                double distancePerSegment = (totalSegments > 0) ? (double)totalDistance / totalSegments : 0.0;

                Timber.tag(TAG).d("배치 총 이동 거리: %.2f m. %d개 세그먼트 저장 시작.", totalDistance, totalSegments);

                for (int segment = 0; segment < totalSegments; segment++) {
                    int startIndex = segment * SEGMENT_SIZE;
                    int endIndex = startIndex + SEGMENT_SIZE - 1; // 세그먼트 끝 인덱스

                    // 인덱스 범위 체크 (루프 조건상 안전하지만 추가)
                    if (endIndex >= gpsData.size()) break;

                    Map<String, Object> startData = gpsData.get(startIndex);
                    Map<String, Object> endData = gpsData.get(endIndex);

                    try {
                        long startTimestamp = ((Number) Objects.requireNonNull(startData.get("timestamp"))).longValue();
                        double startLat = ((Number) Objects.requireNonNull(startData.get("latitude"))).doubleValue();
                        double startLon = ((Number) Objects.requireNonNull(startData.get("longitude"))).doubleValue();
                        double endLat = ((Number) Objects.requireNonNull(endData.get("latitude"))).doubleValue();
                        double endLon = ((Number) Objects.requireNonNull(endData.get("longitude"))).doubleValue();

                        // --- 세그먼트 최종 이동 수단 결정 ---
                        String finalTransportMode = predictedMode; // 모델 예측 결과로 시작
                        // 전체 배치의 거리가 매우 작으면 STOP으로 덮어쓰기
                        if (totalDistance <= 0.05) {
                            finalTransportMode = DEFAULT_MODE_STOPPED;
                            // Timber.tag(TAG).v("세그먼트 %d: 이동 거리 미미(%.2fm), %s로 변경", segment, totalDistance, finalTransportMode);
                        }

                        // 최종 결정된 정보 로그 출력
                        Timber.tag(TAG).d("세그먼트 %d 최종 모드: %s (거리: %.2fm, 시작: %.6f,%.6f, 종료: %.6f,%.6f)",
                                segment, finalTransportMode, distancePerSegment, startLat, startLon, endLat, endLon);

                        // CSV 파일에 저장
                        savePredictionToCSV(finalTransportMode, distancePerSegment, startTimestamp, startLat, startLon, endLat, endLon);

                    } catch (NullPointerException e) {
                        Timber.tag(TAG).e(e, "세그먼트 %d 데이터 추출 중 NullPointerException (예측 후 저장 단계)", segment);
                    } catch (Exception e) {
                        Timber.tag(TAG).e(e, "세그먼트 %d 처리 중 예외 발생 (예측 후 저장 단계)", segment);
                    }
                }
            } else {
                // GPS 데이터 부족으로 세그먼트 저장 불가
                Timber.tag(TAG).w("GPS 데이터 부족 (%d개)하여 세그먼트 저장 불가 (예측 후)", gpsData != null ? gpsData.size() : 0);
            }

        } catch (Exception e) {
            // 예측 또는 세그먼트 저장 과정에서 예외 발생 시
            Timber.tag(TAG).e(e, "예측 또는 세그먼트 저장 중 오류: %s", e.getMessage());
            lastPredictedResult = DEFAULT_MODE_UNKNOWN; // 오류 시 ETC로 설정
            // 예측 실패 시에도 대체 저장 시도
            saveSegmentsWithFallback(gpsData, totalDistance, DEFAULT_MODE_UNKNOWN);
        }
    }


    // Softmax 함수 구현 (변경 없음)
    private float[] softmax(float[] logits) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            if (logit > maxLogit) maxLogit = logit;
        }
        float sum = 0.0f;
        float[] expLogits = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            // 수치 안정성을 위해 최대 logit 빼기
            expLogits[i] = (float) Math.exp(logits[i] - maxLogit);
            sum += expLogits[i];
        }
        float[] probabilities = new float[logits.length];
        // 모든 expLogits가 0인 경우 (매우 드묾) 0으로 나누기 방지
        if (sum == 0) {
            Timber.tag(TAG).w("Softmax 합계가 0입니다. 균일 확률 반환.");
            float uniformProb = 1.0f / logits.length;
            for (int i = 0; i < logits.length; i++) {
                probabilities[i] = uniformProb;
            }
            return probabilities;
        }
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = expLogits[i] / sum;
        }
        return probabilities;
    }

    // 가장 이른 타임스탬프 찾는 함수 (null/빈 리스트 처리 추가)
    private static long findEarliestTimestamp(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return System.currentTimeMillis(); // 리스트가 비었으면 현재 시간 반환
        }
        return dataList.stream()
                // null map, timestamp 키 부재, timestamp가 숫자가 아닌 경우 필터링
                .filter(map -> map != null && map.containsKey("timestamp") && map.get("timestamp") instanceof Number)
                // Long 값으로 변환
                .mapToLong(map -> ((Number) map.get("timestamp")).longValue())
                // 최소값 찾기
                .min()
                // 유효한 타임스탬프가 없으면 현재 시간 반환
                .orElse(System.currentTimeMillis());
    }
}
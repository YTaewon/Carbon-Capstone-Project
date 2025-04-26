package com.example.myapplication12345.AI.GPS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class GPSProcessor { // 클래스 이름 변경 (선택 사항)
    private static final int MILLI = 1000;
    private static final int STEP = 5 * MILLI; // ✅ 5초 간격
    private static final double EARTH_RADIUS_KM = 6371.0; // ✅ 지구 반경 (km) - double 유지 (계산 정밀도)

    /**
     * Haversine 공식을 사용하여 두 지점 간의 거리를 계산합니다.
     * 입력은 float이지만, 계산 정밀도를 위해 내부적으로 double을 사용합니다.
     *
     * @param lat1 첫 번째 지점의 위도 (float)
     * @param lon1 첫 번째 지점의 경도 (float)
     * @param lat2 두 번째 지점의 위도 (float)
     * @param lon2 두 번째 지점의 경도 (float)
     * @return 두 지점 간의 거리 (km, double)
     */
    public static double haversineDistance(float lat1, float lon1, float lat2, float lon2) {
        // float를 double로 변환하여 계산 수행
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1); // lon1 사용 (오타 수정 가능성)
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c; // 결과는 double로 반환
    }

    /**
     * GPS 데이터를 처리하여 5초 간격의 속도 통계(최소, 최대, 평균, 표준편차)를 계산합니다.
     * 입력 데이터의 위도/경도는 float로 가정하고, 출력 통계 값은 float로 생성합니다.
     *
     * @param gpsData        GPS 데이터 목록. 각 Map은 "timestamp"(Long), "latitude"(Float), "longitude"(Float) 키를 포함해야 함.
     * @param startTimestamp 처리 시작 타임스탬프 (milliseconds)
     * @return 처리된 데이터 목록. 각 Map은 "timestamp"(Long), "speed_min"(Float), "speed_max"(Float), "speed_mean"(Float), "speed_std"(Float) 키를 포함.
     */
    public static List<Map<String, Object>> processGPS(List<Map<String, Object>> gpsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        // 1분(60초) 동안 5초 간격으로 처리
        long endTime = startTimestamp + (60 * MILLI);
        for (long curTime = startTimestamp; curTime < endTime; curTime += STEP) {
            final long currentTime = curTime;
            final long intervalEndTime = curTime + STEP;

            // ✅ 현재 간격 내 데이터 필터링
            List<Map<String, Object>> currentData = gpsData.stream()
                    .filter(record -> {
                        // 필수 키 존재 및 타입 확인 강화
                        if (record == null ||
                                !record.containsKey("timestamp") || !(record.get("timestamp") instanceof Number) ||
                                !record.containsKey("latitude") || !(record.get("latitude") instanceof Number) ||
                                !record.containsKey("longitude") || !(record.get("longitude") instanceof Number)) {
                            //System.err.println("Skipping invalid record: " + record); // 디버깅용
                            return false;
                        }
                        long timestamp = ((Number) Objects.requireNonNull(record.get("timestamp"))).longValue();
                        return timestamp >= currentTime && timestamp < intervalEndTime;
                    })
                    .sorted(Comparator.comparingLong(record -> ((Number) Objects.requireNonNull(record.get("timestamp"))).longValue())) // 시간 순서 정렬 추가
                    .collect(Collectors.toList());

            // ✅ 최소 2개의 데이터가 없으면 -1.0f 반환
            if (currentData.size() < 2) {
                processedData.add(createEmptyResult(currentTime));
                continue;
            }

            Map<String, Object> result = calculateSpeedStats(currentData, currentTime);
            processedData.add(result);
        }

        return processedData;
    }

    // ✅ 빈 데이터 처리 (float 출력)
    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("speed_min", -1.0f); // float 리터럴 사용
        result.put("speed_max", -1.0f); // float 리터럴 사용
        result.put("speed_mean", -1.0f); // float 리터럴 사용
        result.put("speed_std", -1.0f); // float 리터럴 사용
        return result;
    }

    // ✅ 속도 통계 계산 (float 입력/출력)
    private static Map<String, Object> calculateSpeedStats(List<Map<String, Object>> currentData, long timestamp) {
        List<Float> speeds = new ArrayList<>(); // 속도를 float로 저장
        Float prevLat = null, prevLon = null; // 이전 위치를 float로 저장 (Wrapper 사용)
        Long prevTime = null;

        for (Map<String, Object> row : currentData) {
            long currentTime = ((Number) Objects.requireNonNull(row.get("timestamp"))).longValue();
            // 입력 데이터에서 float 값 추출
            float currentLat = ((Number) Objects.requireNonNull(row.get("latitude"))).floatValue();
            float currentLon = ((Number) Objects.requireNonNull(row.get("longitude"))).floatValue();

            if (prevLat != null) {
                // haversineDistance는 float를 받아 double을 반환
                double distance_2d = haversineDistance(prevLat, prevLon, currentLat, currentLon); // km
                double timeDiffSeconds = (currentTime - prevTime) / (double) MILLI; // 초 단위 시간 차이 (double로 계산)

                if (timeDiffSeconds > 0) {
                    // 속도 계산 (km/h) - double로 계산 후 float로 캐스팅
                    double speedKmh = (distance_2d / timeDiffSeconds) * 3600.0;
                    speeds.add((float) speedKmh); // 계산된 속도를 float로 변환하여 리스트에 추가
                }
            }

            prevLat = currentLat;
            prevLon = currentLon;
            prevTime = currentTime;
        }

        // 속도 리스트가 비어있으면 (첫번째 포인트만 있거나 시간차가 0인 경우) 빈 결과 반환
        if (speeds.isEmpty()) {
            return createEmptyResult(timestamp);
        }

        return createSpeedResult(timestamp, speeds);
    }

    // ✅ 속도 통계 결과 생성 (float 출력)
    private static Map<String, Object> createSpeedResult(long timestamp, List<Float> speeds) {
        // speeds 리스트는 이제 Float 객체를 포함
        if (speeds == null || speeds.isEmpty()) {
            return createEmptyResult(timestamp); // 방어 코드
        }

        // 최소/최대값 계산 (Collections 사용)
        float speedMin = Collections.min(speeds);
        float speedMax = Collections.max(speeds);

        // 평균 계산 (Stream 사용, double로 중간 계산 후 float로 캐스팅)
        // OptionalDouble을 사용하여 값이 없는 경우 처리
        OptionalDouble avgOpt = speeds.stream()
                .mapToDouble(Float::doubleValue) // 평균 계산 위해 double로 변환
                .average();
        float speedMean = avgOpt.isPresent() ? (float) avgOpt.getAsDouble() : -1.0f;

        // 표준 편차 계산 (Stream 사용, double로 중간 계산 후 float로 캐스팅)
        float speedStd;
        if (speeds.size() > 1 && speedMean != -1.0f) { // 표준편차는 데이터가 2개 이상일 때 의미 있음
            final double mean = speedMean; // effectively final 변수 사용
            double variance = speeds.stream()
                    .mapToDouble(Float::doubleValue) // double로 변환
                    .map(d -> Math.pow(d - mean, 2))
                    .average()
                    .orElse(0.0); // 분산 계산
            speedStd = (float) Math.sqrt(variance); // 표준편차 계산 후 float로 캐스팅
        } else if (speeds.size() == 1) {
            speedStd = 0.0f; // 데이터가 하나면 표준편차는 0
        } else {
            speedStd = -1.0f; // 평균 계산이 불가능했거나 데이터가 부족한 경우
        }


        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("speed_min", speedMin);
        result.put("speed_max", speedMax);
        result.put("speed_mean", speedMean);
        result.put("speed_std", speedStd);

        return result;
    }
}
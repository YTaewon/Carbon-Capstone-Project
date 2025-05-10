package com.example.myapplication12345.AI.GPS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GPSProcessor {
    private static final int MILLI = 1000;
    private static final int STEP = 5 * MILLI; // 5초 간격
    private static final double EARTH_RADIUS_KM = 6371000.0; // 지구 평균 반지름 (km)

    // Haversine 공식을 사용하여 두 지점 간의 거리 계산 (km 단위)
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c; // 결과는 km 단위
    }

    public static List<Map<String, Object>> processGPS(List<Map<String, Object>> gpsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        if (gpsData == null) {
            for (long curTime = startTimestamp; curTime < startTimestamp + (60 * MILLI); curTime += STEP) {
                processedData.add(createEmptyResult(curTime));
            }
            return processedData;
        }

        for (long curTime = startTimestamp; curTime < startTimestamp + (60 * MILLI); curTime += STEP) {
            final long currentTimeWindowStart = curTime;
            final long currentTimeWindowEnd = curTime + STEP;

            List<Map<String, Object>> currentDataInStep = gpsData.stream()
                    .filter(record -> {
                        if (record == null || record.get("timestamp") == null) return false;
                        if (!(record.get("timestamp") instanceof Number)) return false;
                        long timestampVal = ((Number) record.get("timestamp")).longValue();
                        return timestampVal >= currentTimeWindowStart && timestampVal < currentTimeWindowEnd;
                    })
                    .sorted((r1, r2) -> { // 시간 순으로 정렬 (필수)
                        long ts1 = ((Number) r1.get("timestamp")).longValue();
                        long ts2 = ((Number) r2.get("timestamp")).longValue();
                        return Long.compare(ts1, ts2);
                    })
                    .collect(Collectors.toList());

            if (currentDataInStep.size() < 2) {
                processedData.add(createEmptyResult(currentTimeWindowStart));
                continue;
            }

            Map<String, Object> result = calculateSpeedStats(currentDataInStep, currentTimeWindowStart);
            processedData.add(result);
        }

        return processedData;
    }

    // 빈 결과 생성 시 float 타입 사용 (timestamp 제외)
    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp); // long
        result.put("speed_min", -1.0f);   // float
        result.put("speed_max", -1.0f);   // float
        result.put("speed_mean", -1.0f);  // float
        result.put("speed_std", -1.0f);   // float
        return result;
    }

    // 속도 통계 계산 (km/h 단위, Haversine 거리 계산 사용)
    private static Map<String, Object> calculateSpeedStats(List<Map<String, Object>> currentDataInStep, long timestamp) {
        List<Double> speedsKmh = new ArrayList<>();
        Map<String, Object> prevRecord = null;

        for (Map<String, Object> currentRecord : currentDataInStep) {
            // 위도, 경도, 타임스탬프 값 추출 및 유효성 검사
            if (currentRecord.get("latitude") == null || !(currentRecord.get("latitude") instanceof Number) ||
                    currentRecord.get("longitude") == null || !(currentRecord.get("longitude") instanceof Number) ||
                    currentRecord.get("timestamp") == null || !(currentRecord.get("timestamp") instanceof Number)) {
                prevRecord = currentRecord;
                continue;
            }

            long currentTimeMs = ((Number) currentRecord.get("timestamp")).longValue();
            double currentLat = ((Number) currentRecord.get("latitude")).doubleValue();
            double currentLon = ((Number) currentRecord.get("longitude")).doubleValue();

            if (prevRecord != null) {
                long prevTimeMs = ((Number) prevRecord.get("timestamp")).longValue();
                double prevLat = ((Number) prevRecord.get("latitude")).doubleValue();
                double prevLon = ((Number) prevRecord.get("longitude")).doubleValue();

                // Haversine 공식을 사용하여 거리 계산 (km 단위)
                double distanceKm = haversineDistance(prevLat, prevLon, currentLat, currentLon);
                double timeDiffMs = currentTimeMs - prevTimeMs;

                if (timeDiffMs > 0) { // 시간 차이가 있어야 속도 계산 가능
                    // 시간 (ms) -> 시간 (h) : timeDiffMs / (1000.0 ms/s * 3600.0 s/h)
                    double timeDiffHours = timeDiffMs / (1000.0 * 3600.0);
                    if (timeDiffHours > 0) { // 시간 차이가 0이 아닐 때
                        double speedKmh = distanceKm / timeDiffHours; // km/h
                        speedsKmh.add(speedKmh);
                    }
                }
            }
            prevRecord = currentRecord;
        }
        return createSpeedResult(timestamp, speedsKmh);
    }

    // 속도 통계 결과 생성, 숫자 값을 float으로 변환 (timestamp 제외)
    private static Map<String, Object> createSpeedResult(long timestamp, List<Double> speeds) {
        double speedMinDouble = -1.0;
        double speedMaxDouble = -1.0;
        double speedMeanDouble = -1.0;
        double speedStdDouble = -1.0;

        if (!speeds.isEmpty()) {
            // NaN이나 Infinity 값, 음수 속도 필터링
            List<Double> validSpeeds = speeds.stream()
                    .filter(s -> s != null && !Double.isNaN(s) && !Double.isInfinite(s) && s >= 0)
                    .collect(Collectors.toList());

            if (!validSpeeds.isEmpty()) {
                speedMinDouble = Collections.min(validSpeeds);
                speedMaxDouble = Collections.max(validSpeeds);
                speedMeanDouble = validSpeeds.stream().mapToDouble(d -> d).average().orElse(-1.0);

                if (validSpeeds.size() > 1 && speedMeanDouble != -1.0) {
                    final double meanForStd = speedMeanDouble; // 람다용
                    double variance = validSpeeds.stream()
                            .mapToDouble(d -> Math.pow(d - meanForStd, 2))
                            .average()
                            .orElse(0.0);
                    speedStdDouble = Math.sqrt(variance);
                } else if (validSpeeds.size() == 1) { // 데이터가 하나일 경우 표준편차는 0
                    speedStdDouble = 0.0;
                }
                // validSpeeds가 비었거나, 평균이 -1이면 speedStdDouble은 이미 -1.0으로 초기화됨
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp); // long
        result.put("speed_min", (float) speedMinDouble);   // float
        result.put("speed_max", (float) speedMaxDouble);   // float
        result.put("speed_mean", (float) speedMeanDouble);  // float
        result.put("speed_std", (float) speedStdDouble);   // float

        return result;
    }
}
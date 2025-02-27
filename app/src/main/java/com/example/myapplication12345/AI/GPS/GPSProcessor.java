package com.example.myapplication12345.AI.GPS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GPSProcessor {
    private static final int MILLI = 1000;
    private static final int STEP = 5 * MILLI; // ✅ 5초 간격
    private static final float EARTH_RADIUS = 6371.0f; // ✅ 지구 반경 (km)

    public static List<Map<String, Object>> processGPS(List<Map<String, Object>> gpsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        for (long curTime = startTimestamp; curTime < startTimestamp + (60 * MILLI); curTime += STEP) {
            final long currentTime = curTime;

            // ✅ 현재 간격 내 데이터 필터링
            List<Map<String, Object>> currentData = gpsData.stream()
                    .filter(record -> {
                        long timestamp = ((Number) Objects.requireNonNull(record.get("timestamp"))).longValue();
                        return timestamp >= currentTime && timestamp < currentTime + STEP;
                    })
                    .collect(Collectors.toList());

            // ✅ 최소 2개의 데이터가 없으면 -1 반환
            if (currentData.size() < 2) {
                processedData.add(createEmptyResult(currentTime));
                continue;
            }

            Map<String, Object> result = calculateSpeedStats(currentData, currentTime);
            processedData.add(result);
        }

        return processedData;
    }

    // ✅ Haversine 공식을 사용한 WGS84 기반 거리 계산
    private static float haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        float c = 2 * (float)Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // ✅ 결과는 km 단위
    }


    // ✅ 빈 데이터 처리 (-1 반환)
    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>(); // ✅ 순서 보장
        result.put("timestamp", timestamp);
        result.put("speed_min", -1.0f);
        result.put("speed_max", -1.0f);
        result.put("speed_mean", -1.0f);
        result.put("speed_std", -1.0f);
        return result;
    }

    // ✅ 속도 통계 계산
    private static Map<String, Object> calculateSpeedStats(List<Map<String, Object>> currentData, long timestamp) {
        List<Float> speeds = new ArrayList<>();
        float prevLat = Float.NaN, prevLon = Float.NaN;
        Long prevTime = null;

        for (Map<String, Object> row : currentData) {
            long currentTime = ((Number) Objects.requireNonNull(row.get("timestamp"))).longValue();
            double currentLat = ((Number) Objects.requireNonNull(row.get("latitude"))).doubleValue();
            double currentLon = ((Number) Objects.requireNonNull(row.get("longitude"))).doubleValue();

            if (!Float.isNaN(prevLat) && !Float.isNaN(prevLon)) {
                double distance_2d = haversineDistance(prevLat, prevLon, currentLat, currentLon);
                double timeDiff = (currentTime - prevTime) / 1000.0; // 초 단위 변환

                if (timeDiff > 0) {
                    float speed = (float) ((distance_2d / timeDiff) * 3600); // km/h 변환
                    speeds.add(speed);
                }
            }

            prevLat = (float) currentLat;
            prevLon = (float) currentLon;
            prevTime = currentTime;
        }

        return createSpeedResult(timestamp, speeds);
    }

    // ✅ 속도 통계 결과 생성
    private static Map<String, Object> createSpeedResult(long timestamp, List<Float> speeds) {
        float speedMin = speeds.isEmpty() ? -1.0f : Collections.min(speeds);
        float speedMax = speeds.isEmpty() ? -1.0f : Collections.max(speeds);
        float speedMean = speeds.isEmpty() ? -1.0f : (float) speeds.stream().mapToDouble(Float::doubleValue).average().orElse(-1.0f);
        float speedStd = speeds.isEmpty() ? -1.0f : (float) Math.sqrt(
                speeds.stream().mapToDouble(d -> Math.pow(d - speedMean, 2)).average().orElse(0.0));

        Map<String, Object> result = new LinkedHashMap<>(); // ✅ 순서 보장
        result.put("timestamp", timestamp);
        result.put("speed_min", speedMin);
        result.put("speed_max", speedMax);
        result.put("speed_mean", speedMean);
        result.put("speed_std", speedStd);

        return result;
    }
}

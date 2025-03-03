package com.example.myapplication12345.AI.GPS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GPSProcessor {
    private static final int MILLI = 1000;
    private static final int STEP = 5 * MILLI; // 5초 간격
    private static final float EARTH_RADIUS = 6371.0f; // 지구 반경 (km)
    private static final float KMH_CONVERSION_FACTOR = 3600.0f; // km/h 변환 상수

    public static List<Map<String, Object>> processGPS(List<Map<String, Object>> gpsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();
        long endTimestamp = startTimestamp + (60 * MILLI);

        // Pre-filter data outside the loop to reduce redundant filtering
        Map<Long, List<Map<String, Object>>> groupedData = gpsData.stream()
                .collect(Collectors.groupingBy(
                        record -> {
                            long timestamp = ((Number) Objects.requireNonNull(record.get("timestamp"))).longValue();
                            return timestamp - (timestamp % STEP); // Group by 5-second intervals
                        },
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (long curTime = startTimestamp; curTime < endTimestamp; curTime += STEP) {
            List<Map<String, Object>> currentData = groupedData.getOrDefault(curTime, List.of());

            assert currentData != null;
            if (currentData.size() < 2) {
                processedData.add(createEmptyResult(curTime));
            } else {
                processedData.add(calculateSpeedStats(currentData, curTime));
            }
        }

        return processedData;
    }

    // Haversine 공식: float 기반으로 수정
    private static float haversineDistance(float lat1, float lon1, float lat2, float lon2) {
        float dLat = (float) Math.toRadians(lat2 - lat1);
        float dLon = (float) Math.toRadians(lon2 - lon1);
        float sinDLat2 = (float) Math.sin(dLat / 2);
        float sinDLon2 = (float) Math.sin(dLon / 2);

        float a = sinDLat2 * sinDLat2 +
                (float) (Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))) * sinDLon2 * sinDLon2;

        return EARTH_RADIUS * (2 * (float) Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("speed_min", -1.0f);
        result.put("speed_max", -1.0f);
        result.put("speed_mean", -1.0f);
        result.put("speed_std", -1.0f);
        return result;
    }

    private static Map<String, Object> calculateSpeedStats(List<Map<String, Object>> currentData, long timestamp) {
        List<Float> speeds = new ArrayList<>(currentData.size() - 1);
        float prevLat = 0.0f, prevLon = 0.0f;
        Long prevTime = null;

        for (int i = 0; i < currentData.size(); i++) {
            Map<String, Object> row = currentData.get(i);
            long currentTime = ((Number) Objects.requireNonNull(row.get("timestamp"))).longValue();
            float currentLat = ((Number) Objects.requireNonNull(row.get("latitude"))).floatValue();
            float currentLon = ((Number) Objects.requireNonNull(row.get("longitude"))).floatValue();

            if (i > 0) { // 첫 번째 데이터는 이전 값이 없으므로 스킵
                float distance = haversineDistance(prevLat, prevLon, currentLat, currentLon);
                float timeDiff = (currentTime - prevTime) / (float) MILLI; // 초 단위로 변환

                if (timeDiff > 0) {
                    speeds.add((distance / timeDiff) * KMH_CONVERSION_FACTOR); // km/h
                }
            }

            prevLat = currentLat;
            prevLon = currentLon;
            prevTime = currentTime;
        }

        return createSpeedResult(timestamp, speeds);
    }

    private static Map<String, Object> createSpeedResult(long timestamp, List<Float> speeds) {
        if (speeds.isEmpty()) {
            return createEmptyResult(timestamp);
        }

        float sum = 0.0f;
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        for (float speed : speeds) {
            sum += speed;
            min = Math.min(min, speed);
            max = Math.max(max, speed);
        }

        float mean = sum / speeds.size();
        float sumSquaredDiff = 0.0f;
        for (float speed : speeds) {
            float diff = speed - mean;
            sumSquaredDiff += diff * diff;
        }
        float std = (float) Math.sqrt(sumSquaredDiff / speeds.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("speed_min", min);
        result.put("speed_max", max);
        result.put("speed_mean", mean);
        result.put("speed_std", std);

        return result;
    }
}
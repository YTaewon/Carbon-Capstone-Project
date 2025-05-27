package com.example.myapplication12345.AI.GPS;

import java.util.*;
import java.util.stream.Collectors;

public class GPSProcessor {
    private static final int MILLI = 1000;
    private static final int STEP = 5 * MILLI; // 5초 간격
    private static final double EARTH_RADIUS_KM = 6371.0; // 지구 반경 (km)

    // Haversine 공식 기반 거리 계산 (km)
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c; // 결과는 km
    }

    public static List<Map<String, Object>> processGPS(List<Map<String, Object>> gpsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        for (long curTime = startTimestamp; curTime < startTimestamp + (60 * MILLI); curTime += STEP) {
            final long currentTime = curTime;

            List<Map<String, Object>> currentData = gpsData.stream()
                    .filter(record -> {
                        long timestamp = ((Number) record.get("timestamp")).longValue();
                        return timestamp >= currentTime && timestamp < currentTime + STEP;
                    })
                    .collect(Collectors.toList());

            if (currentData.size() < 2) {
                processedData.add(createEmptyResult(currentTime));
                continue;
            }

            Map<String, Object> result = calculateSpeedStats(currentData, currentTime);
            processedData.add(result);
        }

        return processedData;
    }

    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("speed_min", -1.0);
        result.put("speed_max", -1.0);
        result.put("speed_mean", -1.0);
        result.put("speed_std", -1.0);
        return result;
    }

    private static Map<String, Object> calculateSpeedStats(List<Map<String, Object>> currentData, long timestamp) {
        List<Double> speeds = new ArrayList<>();
        Double prevLat = null, prevLon = null;
        Long prevTime = null;

        for (Map<String, Object> row : currentData) {
            long currentTime = ((Number) row.get("timestamp")).longValue();
            double currentLat = ((Number) row.get("latitude")).doubleValue();
            double currentLon = ((Number) row.get("longitude")).doubleValue();

            if (prevLat != null && prevLon != null && prevTime != null) {
                double distance_km = haversineDistance(prevLat, prevLon, currentLat, currentLon); // km
                double distance_meters = distance_km * 1000.0; // ✅ km를 미터로 변환
                double timeDiff = (currentTime - prevTime); // milliseconds

                if (timeDiff > 0) {
                    double speed = (distance_meters / timeDiff) * 3600000.0; // ✅ m/h
                    speeds.add(speed);
                }
            }

            prevLat = currentLat;
            prevLon = currentLon;
            prevTime = currentTime;
        }

        if (speeds.isEmpty()) {
            return createEmptyResult(timestamp);
        }

        return createSpeedResult(timestamp, speeds);
    }

    private static Map<String, Object> createSpeedResult(long timestamp, List<Double> speeds) {
        double speedMin = speeds.isEmpty() ? -1.0 : Collections.min(speeds);
        double speedMax = speeds.isEmpty() ? -1.0 : Collections.max(speeds);
        double speedMean = speeds.isEmpty() ? -1.0 : speeds.stream().mapToDouble(d -> d).average().orElse(-1.0);

        double speedStd = -1.0;
        if (!speeds.isEmpty() && speedMean != -1.0) {
            speedStd = Math.sqrt(speeds.stream().mapToDouble(d -> Math.pow(d - speedMean, 2)).average().orElse(0.0));
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
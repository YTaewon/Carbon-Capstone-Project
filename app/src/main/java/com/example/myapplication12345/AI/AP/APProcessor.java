package com.example.myapplication12345.AI.AP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class APProcessor {
    private static final long ONE_MINUTE_MS = 60 * 1000;
    private static final long TEN_MINUTES_MS = 10 * ONE_MINUTE_MS;

    public static List<Map<String, Object>> processAP(List<Map<String, Object>> apData, long startTimestamp) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 데이터가 없으면 조기 반환
        if (apData == null || apData.isEmpty()) {
            Map<String, Object> emptyResult = new HashMap<>(2); // 초기 용량 지정
            emptyResult.put("timestamp", startTimestamp);
            emptyResult.put("wifi_cnt", 0);
            results.add(emptyResult);
            return results;
        }

        // 시간대별 고유 BSSID 미리 계산
        Map<Long, Map<String, Boolean>> timeWindowBssids = new HashMap<>();
        long endTimestamp = startTimestamp + TEN_MINUTES_MS;

        synchronized (apData) {
            // 먼저 각 시간대별 고유 BSSID 수집
            for (Map<String, Object> record : apData) {
                long timestamp = (long) record.get("timestamp");
                if (timestamp < startTimestamp || timestamp >= endTimestamp) {
                    continue; // 범위 밖의 데이터 스킵
                }

                // 시간대 식별
                long timeWindow = startTimestamp + ((timestamp - startTimestamp) / ONE_MINUTE_MS) * ONE_MINUTE_MS;

                // 해당 시간대 맵 조회 또는 생성
                Map<String, Boolean> bssids = timeWindowBssids.computeIfAbsent(timeWindow, k -> new HashMap<>());

                // BSSID 추가
                Object bssidObj = record.getOrDefault("bssid", "N/A");
                String bssid = bssidObj instanceof String ? (String) bssidObj : String.valueOf(bssidObj);
                bssids.put(bssid, Boolean.TRUE);
            }
        }

        // 시간대별 결과 생성
        boolean hasData = false;

        for (long curTime = startTimestamp; curTime < endTimestamp; curTime += ONE_MINUTE_MS) {
            Map<String, Boolean> bssids = timeWindowBssids.get(curTime);

            if (bssids != null && !bssids.isEmpty()) {
                hasData = true;
                Map<String, Object> result = new HashMap<>(2);
                result.put("timestamp", curTime);
                result.put("wifi_cnt", (float) bssids.size());
                results.add(result);
            }
        }

        // 데이터가 없으면 빈 결과 추가
        if (!hasData) {
            Map<String, Object> emptyResult = new HashMap<>(2);
            emptyResult.put("timestamp", startTimestamp);
            emptyResult.put("wifi_cnt", 0f);
            results.add(emptyResult);
        }

        return results;
    }
}
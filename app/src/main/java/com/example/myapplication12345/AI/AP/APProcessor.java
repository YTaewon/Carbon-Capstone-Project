package com.example.myapplication12345.AI.AP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class APProcessor {
    public static List<Map<String, Object>> processAP(List<Map<String, Object>> apData, long startTimestamp) {
        List<Map<String, Object>> results = new ArrayList<>();
        long step = 60 * 1000; // 60초(1분) 단위 처리
        boolean hasData = false; // wifi_cnt > 0인 데이터가 있는지 확인하는 변수


        for (long curTime = startTimestamp; curTime < startTimestamp + 10 * 60 * 1000; curTime += step) {
            long windowEnd = curTime + step;

            // ✅ 현재 간격 내 데이터 필터링
            Set<String> uniqueBSSIDs = new HashSet<>();
            for (Map<String, Object> record : apData) {
                long timestamp = (long) record.get("timestamp");
                if (timestamp >= curTime && timestamp < windowEnd) {
                    uniqueBSSIDs.add((String) record.getOrDefault("bssid", "N/A"));
                }
            }

            int wifiCount = uniqueBSSIDs.size();
            if (wifiCount > 0) {
                    hasData = true; // wifi_cnt가 0보다 큰 값이 하나라도 있으면 true 설정
                    Map<String, Object> result = new HashMap<>();
                    result.put("timestamp", curTime);
                    result.put("wifi_cnt", wifiCount); // 고유한 AP 개수
                    results.add(result);
            }
        }

        // ✅ 모든 결과가 비어 있으면 "wifi_cnt: 0" 한 번만 추가
        if (!hasData) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("timestamp", startTimestamp);
            emptyResult.put("wifi_cnt", 0);
            results.add(emptyResult);
        }

        return results;
    }
}

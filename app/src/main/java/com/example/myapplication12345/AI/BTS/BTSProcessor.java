package com.example.myapplication12345.AI.BTS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class BTSProcessor {
    private static final int MILLI = 5000;
    private static final int STEP = 1 * MILLI; // 5초 간격

    public static List<Map<String, Object>> processBTS(List<Map<String, Object>> btsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        for (long curTime = startTimestamp; curTime < startTimestamp + (60 * 1000); curTime += STEP) {
            final long currentTime = curTime;

            // ✅ 현재 간격 내 데이터 필터링 및 그룹화
            List<Set<String>> uniqs = btsData.stream()
                    .filter(record -> {
                        long timestamp = (long) record.get("timestamp");
                        return timestamp >= currentTime && timestamp < currentTime + STEP;
                    })
                    .collect(Collectors.groupingBy(
                            record -> (long) record.get("timestamp"),
                            Collectors.mapping(record -> record.get("ci") + "_" + record.get("pci"), Collectors.toSet())
                    ))
                    .values().stream().collect(Collectors.toList());

            // ✅ 데이터가 부족하면 기본값 추가
            if (uniqs.size() < 2) {
                processedData.add(createEmptyResult(currentTime));
                continue;
            }

            processedData.add(processJerkData(uniqs, currentTime));
        }

        return processedData;
    }

    // ✅ 빈 데이터 처리 (BTS 신호 없음) - LinkedHashMap 사용하여 순서 유지
    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("total", 0);
        result.put("jerk_min", -1);
        result.put("jerk_max", -1);
        result.put("jerk_mean", -1.0);
        result.put("jerk_std", -1.0);
        return result;
    }

    // ✅ jerk 값 계산 - LinkedHashMap 사용하여 순서 유지
    private static Map<String, Object> processJerkData(List<Set<String>> uniqs, long timestamp) {
        AtomicReference<Set<String>> before = new AtomicReference<>(null);
        List<Integer> jerkList = new ArrayList<>();

        for (Set<String> uniq : uniqs) {
            if (before.get() != null) {
                int jerkValue = (int) before.get().stream().filter(e -> !uniq.contains(e)).count() +
                        (int) uniq.stream().filter(e -> !before.get().contains(e)).count();
                jerkList.add(jerkValue);
            }
            before.set(uniq);
        }

        float total = uniqs.stream().flatMap(Set::stream).collect(Collectors.toSet()).size();
        float jerk_min = jerkList.isEmpty() ? -1 : Collections.min(jerkList);
        float jerk_max = jerkList.isEmpty() ? -1 : Collections.max(jerkList);
        float jerk_mean = (float) (jerkList.isEmpty() ? -1.0 : jerkList.stream().mapToInt(Integer::intValue).average().orElse(-1.0));
        float jerk_std = (float) (jerkList.isEmpty() ? -1.0 : Math.sqrt(jerkList.stream().mapToDouble(j -> Math.pow(j - jerk_mean, 2)).average().orElse(-1.0)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("total", total);
        result.put("jerk_min", jerk_min);
        result.put("jerk_max", jerk_max);
        result.put("jerk_mean", jerk_mean);
        result.put("jerk_std", jerk_std);

        return result;
    }
}
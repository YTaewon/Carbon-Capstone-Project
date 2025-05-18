package com.example.myapplication12345.AI.BTS; // 패키지명은 2번 코드 기준

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class BTSProcessor {
    private static final int MILLI_INTERVAL = 5000;
    private static final int STEP = MILLI_INTERVAL;
    private static final int PROCESSING_WINDOW = 60 * 1000;

    public static List<Map<String, Object>> processBTS(List<Map<String, Object>> btsData, long startTimestamp) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        if (btsData == null) {
            return processedData; // 빈 리스트 반환
        }

        for (long curTime = startTimestamp; curTime < startTimestamp + PROCESSING_WINDOW; curTime += STEP) {
            final long currentTime = curTime;

            List<Set<String>> uniqs = btsData.stream()
                    .filter(record -> {
                        Object tsObj = record.get("timestamp");
                        if (!(tsObj instanceof Long)) return false;
                        long timestampVal = (long) tsObj;
                        return timestampVal >= currentTime && timestampVal < currentTime + STEP;
                    })
                    .collect(Collectors.groupingBy(
                            record -> (long) record.get("timestamp"),
                            Collectors.mapping(record -> {
                                String ci = record.get("ci") == null ? "null_ci" : String.valueOf(record.get("ci"));
                                String pci = record.get("pci") == null ? "null_pci" : String.valueOf(record.get("pci"));
                                return ci + "_" + pci;
                            }, Collectors.toSet())
                    ))
                    .values().stream().collect(Collectors.toList());

            if (uniqs.size() < 2) {
                processedData.add(createEmptyResult(currentTime));
                continue;
            }

            processedData.add(processJerkDataInternal(uniqs, currentTime));
        }

        return processedData;
    }

    // 빈 결과 생성 시 float 타입 사용
    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("total", -1f); // float
        result.put("jerk_min", -1f); // float
        result.put("jerk_max", -1f); // float
        result.put("jerk_mean", -1.0f); // float
        result.put("jerk_std", -1.0f); // float
        return result;
    }

    // jerk 데이터 계산 후 결과 Map에 float 타입으로 저장
    private static Map<String, Object> processJerkDataInternal(List<Set<String>> uniqs, long timestamp) {
        AtomicReference<Set<String>> before = new AtomicReference<>(null);
        List<Integer> jerkList = new ArrayList<>();

        for (Set<String> uniq : uniqs) {
            if (before.get() != null) {
                long missingInUniq = before.get().stream().filter(e -> !uniq.contains(e)).count();
                long newInUniq = uniq.stream().filter(e -> !before.get().contains(e)).count();
                jerkList.add((int) (missingInUniq + newInUniq));
            }
            before.set(uniq);
        }

        int totalInt = uniqs.stream().flatMap(Set::stream).collect(Collectors.toSet()).size();

        int jerkMinInt = jerkList.isEmpty() ? -1 : Collections.min(jerkList);
        int jerkMaxInt = jerkList.isEmpty() ? -1 : Collections.max(jerkList);
        double jerkMeanDouble = jerkList.isEmpty() ? -1.0 : jerkList.stream().mapToInt(Integer::intValue).average().orElse(-1.0);

        double jerkStdDouble;
        if (jerkList.isEmpty() || jerkMeanDouble == -1.0) { // jerk_mean이 -1.0일때 std도 -1.0으로
            jerkStdDouble = -1.0;
        } else {
            final double finalJerkMean = jerkMeanDouble; // for lambda
            double variance = jerkList.stream()
                    .mapToDouble(j -> Math.pow(j - finalJerkMean, 2))
                    .average()
                    .orElse(0.0); // If list has one element, variance is 0
            jerkStdDouble = Math.sqrt(variance);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("total", (float) totalInt); // int to float
        result.put("jerk_min", (float) jerkMinInt); // int to float
        result.put("jerk_max", (float) jerkMaxInt); // int to float
        result.put("jerk_mean", (float) jerkMeanDouble); // double to float
        result.put("jerk_std", (float) jerkStdDouble); // double to float

        return result;
    }
}
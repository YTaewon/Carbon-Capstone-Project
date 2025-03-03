package com.example.myapplication12345.AI.BTS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BTSProcessor {
    private static final int MILLI = 5000; // 5초
    private static final int STEP = MILLI;
    private static final int PROCESSING_WINDOW = 60 * 1000; // 1분

    public static List<Map<String, Object>> processBTS(List<Map<String, Object>> btsData, long startTimestamp) {
        // 결과 저장할 리스트 - 예상 크기로 초기화
        int expectedSize = PROCESSING_WINDOW / STEP;
        List<Map<String, Object>> processedData = new ArrayList<>(expectedSize);

        // 입력 데이터가 없거나 비어 있는 경우 처리
        if (btsData == null || btsData.isEmpty()) {
            for (long curTime = startTimestamp; curTime < startTimestamp + PROCESSING_WINDOW; curTime += STEP) {
                processedData.add(createEmptyResult(curTime));
            }
            return processedData;
        }

        // 시간대별 BTS ID 그룹화 (미리 계산)
        long endTimestamp = startTimestamp + PROCESSING_WINDOW;
        Map<Long, Set<String>> timeWindowBtsIds = new HashMap<>(expectedSize);

        // 각 타임스탬프별 데이터 사전 계산
        for (Map<String, Object> record : btsData) {
            long timestamp = (long) record.get("timestamp");
            if (timestamp < startTimestamp || timestamp >= endTimestamp) {
                continue; // 범위 밖 데이터 스킵
            }

            // 타임스탬프를 STEP 간격으로 정규화
            long normalizedTime = startTimestamp + ((timestamp - startTimestamp) / STEP) * STEP;

            // 해당 시간대 BTS ID 세트 가져오기 또는 새로 생성
            Set<String> btsIds = timeWindowBtsIds.computeIfAbsent(normalizedTime, k -> new HashSet<>());

            // BTS ID 추가 (ci_pci 형식)
            String btsId = record.get("ci") + "_" + record.get("pci");
            btsIds.add(btsId);
        }

        // 각 시간 간격 처리
        for (long curTime = startTimestamp; curTime < endTimestamp; curTime += STEP) {
            Set<String> currentBtsIds = timeWindowBtsIds.get(curTime);
            Set<String> nextBtsIds = timeWindowBtsIds.get(curTime + STEP);

            // 두 개 이상의 데이터 그룹이 있는지 확인
            if (currentBtsIds == null || nextBtsIds == null) {
                processedData.add(createEmptyResult(curTime));
                continue;
            }

            // jerk 계산
            processedData.add(calculateJerkData(currentBtsIds, nextBtsIds, curTime));
        }

        return processedData;
    }

    // 빈 결과 생성
    private static Map<String, Object> createEmptyResult(long timestamp) {
        Map<String, Object> result = new HashMap<>(6); // 필요한 필드 수로 초기화
        result.put("timestamp", timestamp);
        result.put("total", 0f);
        result.put("jerk_min", -1f);
        result.put("jerk_max", -1f);
        result.put("jerk_mean", -1.0f);
        result.put("jerk_std", -1.0f);
        return result;
    }

    // jerk 데이터 계산 (최적화 버전)
    private static Map<String, Object> calculateJerkData(Set<String> currentBtsIds, Set<String> nextBtsIds, long timestamp) {
        List<Integer> jerkList = new ArrayList<>();

        // 현재와 다음 시간대의 BTS ID 비교
        int missingInNext = 0;
        for (String btsId : currentBtsIds) {
            if (!nextBtsIds.contains(btsId)) {
                missingInNext++;
            }
        }

        int newInNext = 0;
        for (String btsId : nextBtsIds) {
            if (!currentBtsIds.contains(btsId)) {
                newInNext++;
            }
        }

        int jerkValue = missingInNext + newInNext;
        jerkList.add(jerkValue);

        // 모든 고유 BTS ID 계산
        Set<String> allIds = new HashSet<>(currentBtsIds);
        allIds.addAll(nextBtsIds);
        float total = allIds.size();

        // 통계 계산
        float jerk_std = 0.0f; // 표준편차는 샘플이 하나뿐이므로 0

        Map<String, Object> result = new HashMap<>(6);
        result.put("timestamp", timestamp);
        result.put("total", total);
        result.put("jerk_min", (float) jerkValue);
        result.put("jerk_max", (float) jerkValue);
        result.put("jerk_mean", (float) jerkValue);
        result.put("jerk_std", jerk_std);

        return result;
    }
}
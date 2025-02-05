package com.example.myapplication12345.AI;

import android.content.Context;
import java.util.*;
import java.util.stream.Collectors;

public class Preprocessing {
    private static final int PER_MIN = 60 * 1000; // 1분 단위
    private static List<Map<String, Object>> apData = Collections.synchronizedList(new ArrayList<>());
    private WifiSensor wifiSensor;
    private String lastResult = "No Data Yet";

    public Preprocessing(Context context) {
        this.wifiSensor = new WifiSensor(context);
    }

    /**
     * WiFi AP 데이터를 수집하고 처리
     */
    public void processAPData() {
        synchronized (apData) {
            List<Map<String, Object>> newWifiData = wifiSensor.getWifiData();
            apData.addAll(newWifiData);

            long start = findStart(apData);
            List<Map<String, Object>> processedAP = pre_ap(apData, start);

            if (!processedAP.isEmpty()) {
                Map<String, Object> latestResult = processedAP.get(processedAP.size() - 1);
                lastResult = "Timestamp: " + latestResult.get("timestamp") + ", WiFi Count: " + latestResult.get("wifi_cnt");
            }
        }
    }

    /**
     * 가장 최근 WiFi AP 개수를 반환
     */
    public String getLastResult() {
        return lastResult;
    }

    /**
     * WiFi AP 개수를 1분 단위로 집계
     */
    public static List<Map<String, Object>> pre_ap(List<Map<String, Object>> ap, Long start) {
        if (start == null) {
            start = findStart(ap);
        }

        List<Map<String, Object>> processedData = new ArrayList<>();
        long step = PER_MIN; // 1분 단위

        for (long curTime = start; curTime < start + PER_MIN; curTime += step) {
            long finalCurTime = curTime;
            List<Map<String, Object>> curr = ap.stream()
                    .filter(row -> (long) row.get("timestamp") >= finalCurTime && (long) row.get("timestamp") < finalCurTime + step)
                    .collect(Collectors.toList());

            Set<Object> uniqueBSSIDs = curr.stream()
                    .map(row -> row.get("wifibssid"))
                    .collect(Collectors.toSet());

            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", curTime);
            result.put("wifi_cnt", uniqueBSSIDs.size());

            processedData.add(result);
        }

        return processedData;
    }

    /**
     * WiFi 데이터의 시작 시간을 찾는 함수
     */
    public static long findStart(List<Map<String, Object>> df) {
        return df.isEmpty() ? System.currentTimeMillis() : (long) df.get(0).get("timestamp");
    }
}

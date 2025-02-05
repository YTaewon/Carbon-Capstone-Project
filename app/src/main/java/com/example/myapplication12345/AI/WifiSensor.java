package com.example.myapplication12345.AI;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import java.util.*;

public class WifiSensor {
    private WifiManager wifiManager;

    public WifiSensor(Context context) {
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * 현재 감지된 WiFi BSSID 목록을 반환
     */
    public List<Map<String, Object>> getWifiData() {
        List<Map<String, Object>> wifiData = new ArrayList<>();

        // WiFi 스캔 실행
        wifiManager.startScan();
        List<ScanResult> results = wifiManager.getScanResults();

        long timestamp = System.currentTimeMillis();
        for (ScanResult result : results) {
            Map<String, Object> wifiEntry = new HashMap<>();
            wifiEntry.put("timestamp", timestamp);
            wifiEntry.put("wifibssid", result.BSSID); // AP의 고유 MAC 주소
            wifiData.add(wifiEntry);
        }

        return wifiData;
    }
}

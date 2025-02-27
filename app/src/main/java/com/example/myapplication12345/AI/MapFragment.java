package com.example.myapplication12345.AI;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

import com.example.myapplication12345.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapFragment extends Fragment {
    private static final String TAG = "MapFragment";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    private static final long ONE_MINUTE_MS = 60 * 1000;

    private MapView mapView;
    private TextView textDistanceInfo;
    private EditText dateInput;
    private Button loadButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getContext(), getActivity().getPreferences(Context.MODE_PRIVATE));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.map_view);
        textDistanceInfo = view.findViewById(R.id.text_distance_info);
        dateInput = view.findViewById(R.id.date_input);
        loadButton = view.findViewById(R.id.load_button);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        loadButton.setOnClickListener(v -> {
            String selectedDate = dateInput.getText().toString().trim();
            if (selectedDate.isEmpty()) {
                selectedDate = dateFormat.format(System.currentTimeMillis());
                dateInput.setText(selectedDate);
            }
            loadAndDisplayPredictionData(selectedDate);
        });

        String currentDate = dateFormat.format(System.currentTimeMillis());
        dateInput.setText(currentDate);
        loadAndDisplayPredictionData(currentDate);

        return view;
    }

    private void loadAndDisplayPredictionData(String date) {
        String fileName = date + "_predictions.csv";
        File file = new File(getContext().getExternalFilesDir(null), "SensorData/" + fileName);

        if (!file.exists()) {
            Log.e(TAG, "예측 데이터 CSV 파일이 존재하지 않음: " + fileName);
            textDistanceInfo.setText("데이터 없음: " + fileName);
            return;
        }

        List<Map<String, String>> predictionData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                Log.e(TAG, "CSV 헤더가 없음: " + fileName);
                textDistanceInfo.setText("CSV 헤더 없음: " + fileName);
                return;
            }
            String[] headers = headerLine.split(",");

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != headers.length) {
                    Log.w(TAG, "CSV 데이터 불일치: " + line);
                    continue;
                }
                Map<String, String> data = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    data.put(headers[i], values[i]);
                }
                predictionData.add(data);
            }
        } catch (IOException e) {
            Log.e(TAG, "CSV 로드 실패: " + e.getMessage(), e);
            textDistanceInfo.setText("CSV 로드 실패: " + e.getMessage());
            return;
        }

        displayPredictionOnMap(predictionData);
    }

    private void displayPredictionOnMap(List<Map<String, String>> predictionData) {
        mapView.getOverlays().clear();
        StringBuilder distanceInfo = new StringBuilder("이동 기록:\n");
        GeoPoint firstPoint = null;

        for (Map<String, String> data : predictionData) {
            String transportMode = data.get("transport_mode");
            double distance = Double.parseDouble(data.get("distance_meters"));
            long startTimestamp = Long.parseLong(data.get("start_timestamp"));

            List<GeoPoint> geoPoints = loadGeoPointsFromGPS(startTimestamp);
            if (!geoPoints.isEmpty()) {
                Polyline polyline = new Polyline();
                polyline.setPoints(geoPoints);
                polyline.setColor(getTransportColor(transportMode));
                polyline.setWidth(5.0f);
                polyline.setTitle(transportMode + " - 거리: " + String.format("%.2f m", distance));
                mapView.getOverlays().add(polyline);

                if (firstPoint == null) {
                    firstPoint = geoPoints.get(0);
                }
            }

            distanceInfo.append(String.format("시간: %s, 이동수단: %s, 거리: %.2f m\n",
                    new SimpleDateFormat("HH:mm:ss").format(startTimestamp), transportMode, distance));
        }

        if (firstPoint != null) {
            mapView.getController().setCenter(firstPoint);
        }

        mapView.invalidate();
        textDistanceInfo.setText(distanceInfo.toString());
    }

    private List<GeoPoint> loadGeoPointsFromGPS(long startTimestamp) {
        List<GeoPoint> geoPoints = new ArrayList<>();
        String date = dateFormat.format(startTimestamp);
        String fileName = date + "_GPS.csv";
        File file = new File(getContext().getExternalFilesDir(null), "SensorData/" + fileName);

        if (!file.exists()) {
            Log.e(TAG, "GPS CSV 파일이 존재하지 않음: " + fileName);
            return geoPoints;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String headerLine = br.readLine();
            if (headerLine == null) return geoPoints;
            String[] headers = headerLine.split(",");

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != headers.length) continue;

                Map<String, String> data = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    data.put(headers[i], values[i]);
                }

                long timestamp = Long.parseLong(data.get("timestamp"));
                if (timestamp >= startTimestamp && timestamp <= startTimestamp + ONE_MINUTE_MS) {
                    double latitude = Float.parseFloat(data.get("latitude"));
                    double longitude = Float.parseFloat(data.get("longitude"));
                    geoPoints.add(new GeoPoint(latitude, longitude));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "GPS CSV 로드 실패: " + e.getMessage(), e);
        }
        return geoPoints;
    }

    private int getTransportColor(String transportMode) {
        switch (transportMode) {
            case "WALK": return Color.GREEN;
            case "BIKE": return Color.BLUE;
            case "BUS": return Color.YELLOW;
            case "CAR": return Color.RED;
            case "SUBWAY": return Color.MAGENTA;
            case "ETC": return Color.GRAY;
            default: return Color.BLACK;
        }
    }
}
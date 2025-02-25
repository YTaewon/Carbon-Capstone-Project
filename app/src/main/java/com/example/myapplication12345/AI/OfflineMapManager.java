package com.example.myapplication12345.AI;

import android.content.Context;
import android.graphics.Color;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import java.util.ArrayList;
import java.util.List;

public class OfflineMapManager {
    private final MapView mapView;
    private final MyLocationNewOverlay myLocationOverlay;
    private final List<GeoPoint> routePoints = new ArrayList<>();
    private Polyline polyline;

    public OfflineMapManager(Context context, MapView mapView) {
        this.mapView = mapView;
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        polyline = new Polyline();
        polyline.setWidth(8.0f);
        polyline.setColor(Color.BLUE);
        mapView.getOverlays().add(polyline);
    }

    public void updateLocation(GeoPoint newPoint, float speed) {
        routePoints.add(newPoint);
        polyline.setPoints(routePoints);
        polyline.setColor(getSpeedColor(speed));
        mapView.invalidate();
        mapView.getController().animateTo(newPoint);
    }

    private int getSpeedColor(float speed) {
        if (speed < 1.5) return Color.BLUE;
        if (speed < 3.0) return Color.GREEN;
        if (speed < 5.0) return Color.YELLOW;
        return Color.RED;
    }
}

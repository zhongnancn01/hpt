package com.hoptrace.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.List;

/** Full-screen interactive map for the most recent traceroute. */
public final class HopMapActivity extends AppCompatActivity {
    public static final String EXTRA_HOPS_JSON = "com.hoptrace.app.extra.HOPS_JSON";

    private MapView mapView;
    private HopMapRenderer renderer;
    private List<HopResult> hops = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        hops = readHops(getIntent().getStringExtra(EXTRA_HOPS_JSON));
        buildScreen(savedInstanceState);
    }

    private void buildScreen(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247, 248, 252));
        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        root.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("HopTrace · route map");
        toolbar.setSubtitle(hops.size() + " observed hops · approximate IP locations");
        toolbar.setTitleTextColor(Color.rgb(23, 32, 51));
        toolbar.setSubtitleTextColor(Color.rgb(104, 115, 138));
        toolbar.setBackgroundColor(Color.argb(242, 255, 255, 255));
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationContentDescription("Close full-screen map");
        toolbar.setNavigationOnClickListener(v -> finish());
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.TOP);
        root.addView(toolbar, toolbarParams);

        TextView attribution = new TextView(this);
        attribution.setText("Approximate IP geolocation · Map data © OpenStreetMap contributors · MapLibre");
        attribution.setTextSize(10);
        attribution.setTextColor(Color.rgb(83, 94, 116));
        attribution.setGravity(Gravity.CENTER);
        attribution.setPadding(dp(10), dp(7), dp(10), dp(7));
        attribution.setBackgroundColor(Color.argb(228, 255, 255, 255));
        FrameLayout.LayoutParams attributionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        attributionParams.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(attribution, attributionParams);
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbarParams.topMargin = bars.top;
            toolbar.setLayoutParams(toolbarParams);
            attributionParams.bottomMargin = bars.bottom + dp(8);
            attribution.setLayoutParams(attributionParams);
            return windowInsets;
        });

        mapView.getMapAsync(map -> {
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(20.0, 0.0)).zoom(1.4).build());
            map.setStyle(new Style.Builder().fromUri(HopMapRenderer.MAP_STYLE), style -> {
                renderer = new HopMapRenderer(this, map);
                renderer.install(style);
                renderer.setOnHopClickListener((title, details) ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(title)
                                .setMessage(details)
                                .setPositiveButton("Close", null)
                                .show());
                renderer.render(hops, true);
            });
        });
    }

    private List<HopResult> readHops(String jsonText) {
        List<HopResult> result = new ArrayList<>();
        if (jsonText == null || jsonText.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(jsonText);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                HopResult hop = new HopResult(item.optInt("ttl"), item.optString("ip"),
                        item.optDouble("rtt", -1), item.optString("type", "TIMEOUT"));
                if (item.has("latitude") && item.has("longitude") &&
                        !item.isNull("latitude") && !item.isNull("longitude")) {
                    hop.geoInfo = new GeoInfo(item.optString("city"), item.optString("region"),
                            item.optString("country"), item.optString("organization"),
                            item.optDouble("latitude", Double.NaN), item.optDouble("longitude", Double.NaN));
                }
                result.add(hop);
            }
        } catch (Exception ignored) {
            // A malformed or missing snapshot opens the map at the world view rather than crashing.
        }
        return result;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }
}

package com.hoptrace.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;

import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared MapLibre route renderer used by the inline and full-screen maps. */
public final class HopMapRenderer {
    public static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final int ROUTE_BLUE = Color.rgb(42, 96, 224);
    private static final int COUNTRY_ORANGE = Color.rgb(235, 145, 48);

    private static final String ROUTE_SOURCE_ID = "hoptrace-route-source";
    private static final String CROSSING_SOURCE_ID = "hoptrace-crossing-source";
    private static final String POINTS_SOURCE_ID = "hoptrace-points-source";
    private static final String COUNTRY_SOURCE_ID = "hoptrace-country-source";

    private static final String MARKER_LAYER_ID = "hoptrace-hop-markers";
    private static final String NUMBER_LAYER_ID = "hoptrace-hop-numbers";
    private static final String COUNTRY_LAYER_ID = "hoptrace-country-labels";

    public interface OnHopClickListener {
        void onHopClicked(String title, String details);
    }

    private final MapLibreMap map;
    private GeoJsonSource routeSource;
    private GeoJsonSource crossingSource;
    private GeoJsonSource pointsSource;
    private GeoJsonSource countrySource;
    private OnHopClickListener clickListener;
    private boolean installed;

    public HopMapRenderer(Context context, MapLibreMap map) {
        this.map = map;
    }

    public void install(Style style) {
        if (installed) return;
        routeSource = new GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(new Feature[0]));
        crossingSource = new GeoJsonSource(CROSSING_SOURCE_ID, FeatureCollection.fromFeatures(new Feature[0]));
        pointsSource = new GeoJsonSource(POINTS_SOURCE_ID, FeatureCollection.fromFeatures(new Feature[0]));
        countrySource = new GeoJsonSource(COUNTRY_SOURCE_ID, FeatureCollection.fromFeatures(new Feature[0]));

        style.addSource(routeSource);
        style.addSource(crossingSource);
        style.addSource(pointsSource);
        style.addSource(countrySource);

        // A soft white casing keeps the route visible over both light and dark map features.
        style.addLayer(new LineLayer("hoptrace-route-casing", ROUTE_SOURCE_ID)
                .withProperties(
                        PropertyFactory.lineColor(Color.WHITE),
                        PropertyFactory.lineWidth(8f),
                        PropertyFactory.lineOpacity(0.88f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round")));
        style.addLayer(new LineLayer("hoptrace-route-line", ROUTE_SOURCE_ID)
                .withProperties(
                        PropertyFactory.lineColor(ROUTE_BLUE),
                        PropertyFactory.lineWidth(3.5f),
                        PropertyFactory.lineOpacity(0.95f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round")));
        style.addLayer(new LineLayer("hoptrace-country-crossings", CROSSING_SOURCE_ID)
                .withProperties(
                        PropertyFactory.lineColor(COUNTRY_ORANGE),
                        PropertyFactory.lineWidth(4.5f),
                        PropertyFactory.lineOpacity(0.95f),
                        PropertyFactory.lineCap("round"),
                        PropertyFactory.lineJoin("round")));

        style.addLayer(new CircleLayer("hoptrace-hop-halo", POINTS_SOURCE_ID)
                .withProperties(
                        PropertyFactory.circleRadius(12f),
                        PropertyFactory.circleColor(Color.WHITE),
                        PropertyFactory.circleOpacity(0.96f)));
        style.addLayer(new CircleLayer(MARKER_LAYER_ID, POINTS_SOURCE_ID)
                .withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(ROUTE_BLUE),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(1.5f)));
        style.addLayer(new SymbolLayer(NUMBER_LAYER_ID, POINTS_SOURCE_ID)
                .withProperties(
                        PropertyFactory.textField("{label}"),
                        PropertyFactory.textSize(9f),
                        PropertyFactory.textColor(Color.WHITE),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true)));
        style.addLayer(new SymbolLayer(COUNTRY_LAYER_ID, COUNTRY_SOURCE_ID)
                .withProperties(
                        PropertyFactory.textField("{country_label}"),
                        PropertyFactory.textSize(10.5f),
                        PropertyFactory.textColor(Color.rgb(31, 43, 65)),
                        PropertyFactory.textHaloColor(Color.WHITE),
                        PropertyFactory.textHaloWidth(2f),
                        PropertyFactory.textOffset(new Float[]{0f, 1.25f}),
                        PropertyFactory.textAnchor("top"),
                        PropertyFactory.textMaxWidth(18f),
                        PropertyFactory.textAllowOverlap(false),
                        PropertyFactory.textIgnorePlacement(false)));
        installed = true;
        map.addOnMapClickListener(point -> handleMapClick(point));
    }

    public void setOnHopClickListener(OnHopClickListener listener) {
        this.clickListener = listener;
    }

    public void render(List<HopResult> hops, boolean fitCamera) {
        if (!installed || routeSource == null) return;

        List<Feature> routeFeatures = new ArrayList<>();
        List<Feature> crossingFeatures = new ArrayList<>();
        List<Feature> pointFeatures = new ArrayList<>();
        List<Feature> countryFeatures = new ArrayList<>();
        List<Point> mappedPoints = new ArrayList<>();
        Set<String> seenCountries = new HashSet<>();
        Point previousPoint = null;
        String previousCountry = "";

        for (HopResult hop : hops) {
            if (hop.geoInfo == null || !hop.geoInfo.hasCoordinates()) continue;
            GeoInfo geo = hop.geoInfo;
            Point point = Point.fromLngLat(geo.longitude, geo.latitude);
            mappedPoints.add(point);

            String countryCode = geo.country == null ? "" : geo.country.trim().toUpperCase(Locale.ROOT);
            String countryName = geo.countryDisplayName();
            Feature marker = Feature.fromGeometry(point);
            marker.addNumberProperty("ttl", hop.ttl);
            marker.addStringProperty("label", String.valueOf(hop.ttl));
            marker.addStringProperty("ip", hop.ip);
            marker.addStringProperty("country", countryCode);
            marker.addStringProperty("country_name", countryName);
            marker.addStringProperty("city", geo.city);
            marker.addStringProperty("region", geo.region);
            marker.addStringProperty("organization", geo.organization);
            marker.addStringProperty("rtt", hop.rttMs < 0 ? "—" : String.format(Locale.US, "%.1f ms", hop.rttMs));
            marker.addStringProperty("type", hop.type);
            pointFeatures.add(marker);

            // Draw one segment per adjacent pair of mapped POPs instead of one opaque route blob.
            if (previousPoint != null) {
                Feature segment = Feature.fromGeometry(LineString.fromLngLats(pair(previousPoint, point)));
                segment.addStringProperty("from_country", previousCountry);
                segment.addStringProperty("to_country", countryCode);
                routeFeatures.add(segment);
                if (!previousCountry.isEmpty() && !countryCode.isEmpty() && !previousCountry.equals(countryCode)) {
                    crossingFeatures.add(Feature.fromGeometry(LineString.fromLngLats(pair(previousPoint, point))));
                }
            }
            previousPoint = point;
            previousCountry = countryCode;

            // Country annotation is attached to the first located POP where a country appears.
            if (!countryCode.isEmpty() && seenCountries.add(countryCode)) {
                Feature countryLabel = Feature.fromGeometry(point);
                countryLabel.addStringProperty("ip", hop.ip);
                countryLabel.addNumberProperty("ttl", hop.ttl);
                countryLabel.addStringProperty("country", countryCode);
                countryLabel.addStringProperty("country_name", countryName);
                countryLabel.addStringProperty("city", geo.city);
                countryLabel.addStringProperty("region", geo.region);
                countryLabel.addStringProperty("organization", geo.organization);
                countryLabel.addStringProperty("rtt", hop.rttMs < 0 ? "—" : String.format(Locale.US, "%.1f ms", hop.rttMs));
                countryLabel.addStringProperty("type", hop.type);
                countryLabel.addStringProperty("country_label", countryName + "\n" + hop.ip);
                countryFeatures.add(countryLabel);
            }
        }

        routeSource.setGeoJson(FeatureCollection.fromFeatures(routeFeatures.toArray(new Feature[0])));
        crossingSource.setGeoJson(FeatureCollection.fromFeatures(crossingFeatures.toArray(new Feature[0])));
        pointsSource.setGeoJson(FeatureCollection.fromFeatures(pointFeatures.toArray(new Feature[0])));
        countrySource.setGeoJson(FeatureCollection.fromFeatures(countryFeatures.toArray(new Feature[0])));

        if (fitCamera && !mappedPoints.isEmpty()) fitCamera(mappedPoints);
    }

    private List<Point> pair(Point first, Point second) {
        List<Point> pair = new ArrayList<>(2);
        pair.add(first);
        pair.add(second);
        return pair;
    }

    private void fitCamera(List<Point> points) {
        try {
            if (points.size() == 1) {
                Point only = points.get(0);
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(only.latitude(), only.longitude()), 4.2), 650);
                return;
            }
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Point point : points) builder.include(new LatLng(point.latitude(), point.longitude()));
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 56), 850);
        } catch (Exception ignored) {
            Point last = points.get(points.size() - 1);
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(last.latitude(), last.longitude()), 3.0), 650);
        }
    }

    private boolean handleMapClick(LatLng coordinate) {
        if (clickListener == null) return false;
        PointF screenPoint = map.getProjection().toScreenLocation(coordinate);
        List<Feature> hits = map.queryRenderedFeatures(screenPoint,
                new String[]{MARKER_LAYER_ID, NUMBER_LAYER_ID, COUNTRY_LAYER_ID});
        if (hits == null || hits.isEmpty()) return false;
        Feature feature = hits.get(0);
        String ip = safeProperty(feature, "ip");
        if (ip.isEmpty()) return false;
        int ttl = parseInt(safeProperty(feature, "ttl"), -1);
        String country = safeProperty(feature, "country_name");
        String city = safeProperty(feature, "city");
        String region = safeProperty(feature, "region");
        String organization = safeProperty(feature, "organization");
        String rtt = safeProperty(feature, "rtt");
        String type = safeProperty(feature, "type");

        StringBuilder details = new StringBuilder();
        appendLine(details, "POP address", ip);
        appendLine(details, "Country", country);
        appendLine(details, "City / region", joinLocation(city, region));
        appendLine(details, "Network", organization);
        appendLine(details, "Round-trip time", rtt);
        appendLine(details, "Probe result", type);
        clickListener.onHopClicked(ttl > 0 ? "Hop " + ttl : "POP details", details.toString());
        return true;
    }

    private static String safeProperty(Feature feature, String key) {
        try { return feature.getStringProperty(key); }
        catch (Exception ignored) { return ""; }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return fallback; }
    }

    private static String joinLocation(String city, String region) {
        if (city == null || city.isEmpty()) return region == null ? "" : region;
        if (region == null || region.isEmpty() || city.equals(region)) return city;
        return city + ", " + region;
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (out.length() > 0) out.append('\n');
        out.append(label).append(": ").append(value.trim());
    }
}

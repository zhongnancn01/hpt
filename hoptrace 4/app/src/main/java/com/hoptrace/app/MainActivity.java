package com.hoptrace.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final int BLUE = Color.rgb(36, 92, 219);
    private static final int DARK = Color.rgb(23, 32, 51);
    private static final int MUTED = Color.rgb(104, 115, 138);
    private static final int LINE = Color.rgb(230, 234, 241);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService traceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService geoExecutor = Executors.newFixedThreadPool(3);
    private final List<HopResult> hopResults = new ArrayList<>();
    private final Set<String> requestedGeoIps = new HashSet<>();

    private TextInputEditText targetInput;
    private ChipGroup familyChips;
    private ChipGroup probesChips;
    private ChipGroup dnsModeChips;
    private ChipGroup transportChips;
    private TextInputLayout dnsServerLayout;
    private TextInputEditText dnsServerInput;
    private Chip dnsSystemChip;
    private Chip dnsCustomChip;
    private Slider maxHopsSlider;
    private Slider timeoutSlider;
    private TextView maxHopsValue;
    private TextView timeoutValue;
    private TextView statusText;
    private TextView summaryText;
    private TextView resultsTitle;
    private LinearLayout hopsContainer;
    private FrameLayout mapContainer;
    private MapView mapView;
    private MapLibreMap map;
    private HopMapRenderer mapRenderer;
    private boolean mapReady = false;
    private boolean isRunning = false;
    private long traceStartedAt;
    private int finishedHopCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildScreen(savedInstanceState);
    }

    private void buildScreen(Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 252));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("HopTrace");
        toolbar.setSubtitle("ICMP / UDP route explorer · 1.2.0");
        toolbar.setTitleTextColor(DARK);
        toolbar.setSubtitleTextColor(MUTED);
        toolbar.setBackgroundColor(Color.rgb(247, 248, 252));
        toolbar.setElevation(0f);
        MenuItem ipinfoMenuItem = toolbar.getMenu().add(Menu.NONE, R.id.menu_ipinfo, Menu.NONE, "IPinfo API");
        ipinfoMenuItem.setIcon(android.R.drawable.ic_menu_manage);
        ipinfoMenuItem.setContentDescription("IPinfo API settings");
        ipinfoMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            showApiSettings();
            return true;
        });
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return windowInsets;
        });

        TextView headline = text("Trace the path. See every hop.", 23, DARK, true);
        content.addView(headline, lp(0, 0, 0, 5));
        TextView intro = text("Trace with ICMP or UDP over IPv4 / IPv6, optionally using your own DNS server.",
                13, MUTED, false);
        content.addView(intro, lp(0, 0, 0, 18));

        MaterialCardView inputCard = card();
        LinearLayout inputBody = vertical(16);
        inputBody.setPadding(dp(16), dp(16), dp(16), dp(16));
        inputCard.addView(inputBody);
        content.addView(inputCard, lp(0, 0, 0, 14));

        TextInputLayout targetLayout = new TextInputLayout(this);
        targetLayout.setHint("Hostname or IP address");
        targetLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        targetInput = new TextInputEditText(targetLayout.getContext());
        targetInput.setSingleLine(true);
        targetInput.setText("1.1.1.1");
        targetInput.setTextSize(16);
        targetInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        targetInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        targetLayout.addView(targetInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        inputBody.addView(targetLayout, matchWrap());

        TextView familyLabel = text("IP VERSION", 11, MUTED, true);
        familyBody(inputBody, familyLabel);

        familyChips = new ChipGroup(this);
        familyChips.setSingleSelection(true);
        familyChips.setSelectionRequired(true);
        familyChips.setChipSpacingHorizontal(dp(8));
        Chip auto = chip("Auto", true);
        Chip ipv4 = chip("IPv4", true);
        Chip ipv6 = chip("IPv6", true);
        familyChips.addView(auto);
        familyChips.addView(ipv4);
        familyChips.addView(ipv6);
        auto.setChecked(true);
        inputBody.addView(familyChips, matchWrap());

        TextView dnsLabel = text("DNS RESOLUTION", 11, MUTED, true);
        inputBody.addView(dnsLabel, lp(0, 12, 0, 2));
        dnsModeChips = new ChipGroup(this);
        dnsModeChips.setId(R.id.dns_mode_group);
        dnsModeChips.setSingleSelection(true);
        dnsModeChips.setSelectionRequired(true);
        dnsModeChips.setChipSpacingHorizontal(dp(8));
        dnsSystemChip = chip("System default", true);
        dnsCustomChip = chip("Custom DNS", true);
        dnsModeChips.addView(dnsSystemChip);
        dnsModeChips.addView(dnsCustomChip);
        inputBody.addView(dnsModeChips, matchWrap());

        dnsServerLayout = new TextInputLayout(this);
        dnsServerLayout.setHint("Custom DNS server (IPv4 or IPv6)");
        dnsServerLayout.setHelperText("DNS port 53 · e.g. 1.1.1.1 or 2606:4700:4700::1111");
        dnsServerLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        dnsServerInput = new TextInputEditText(dnsServerLayout.getContext());
        dnsServerInput.setId(R.id.dns_server_input);
        dnsServerInput.setSingleLine(true);
        dnsServerInput.setTextSize(14);
        dnsServerInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        dnsServerInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        dnsServerLayout.addView(dnsServerInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        inputBody.addView(dnsServerLayout, lp(0, 6, 0, 0));

        android.content.SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
        String savedDnsMode = settings.getString("dns_mode", "system");
        dnsCustomChip.setChecked("custom".equals(savedDnsMode));
        dnsSystemChip.setChecked(!"custom".equals(savedDnsMode));
        dnsServerInput.setText(settings.getString("custom_dns_server", ""));
        dnsServerLayout.setVisibility(dnsCustomChip.isChecked() ? View.VISIBLE : View.GONE);
        dnsSystemChip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                dnsServerLayout.setVisibility(View.GONE);
                settings.edit().putString("dns_mode", "system").apply();
            }
        });
        dnsCustomChip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                dnsServerLayout.setVisibility(View.VISIBLE);
                settings.edit().putString("dns_mode", "custom").apply();
            }
        });

        MaterialCardView optionsCard = card();
        LinearLayout optionsBody = vertical(12);
        optionsBody.setPadding(dp(16), dp(14), dp(16), dp(14));
        optionsCard.addView(optionsBody);
        content.addView(optionsCard, lp(0, 0, 0, 14));

        LinearLayout maxRow = new LinearLayout(this);
        maxRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView maxTitle = text("Maximum hops", 14, DARK, true);
        maxRow.addView(maxTitle, new LinearLayout.LayoutParams(0, dp(24), 1f));
        maxHopsValue = text("24", 13, BLUE, true);
        maxRow.addView(maxHopsValue);
        optionsBody.addView(maxRow, matchWrap());
        maxHopsSlider = new Slider(this);
        maxHopsSlider.setValueFrom(5f);
        maxHopsSlider.setValueTo(40f);
        maxHopsSlider.setStepSize(1f);
        maxHopsSlider.setValue(24f);
        maxHopsSlider.addOnChangeListener((slider, value, fromUser) -> maxHopsValue.setText(String.valueOf((int) value)));
        optionsBody.addView(maxHopsSlider, matchWrap());

        LinearLayout timeoutRow = new LinearLayout(this);
        timeoutRow.setGravity(Gravity.CENTER_VERTICAL);
        timeoutRow.addView(text("Timeout per probe", 14, DARK, true),
                new LinearLayout.LayoutParams(0, dp(24), 1f));
        timeoutValue = text("750 ms", 13, BLUE, true);
        timeoutRow.addView(timeoutValue);
        optionsBody.addView(timeoutRow, matchWrap());
        timeoutSlider = new Slider(this);
        timeoutSlider.setValueFrom(500f);
        timeoutSlider.setValueTo(2000f);
        timeoutSlider.setStepSize(250f);
        timeoutSlider.setValue(750f);
        timeoutSlider.addOnChangeListener((slider, value, fromUser) -> timeoutValue.setText((int) value + " ms"));
        optionsBody.addView(timeoutSlider, matchWrap());

        TextView transportLabel = text("TRACEROUTE MODE", 11, MUTED, true);
        optionsBody.addView(transportLabel, lp(0, 4, 0, 0));
        transportChips = new ChipGroup(this);
        transportChips.setId(R.id.transport_mode_group);
        transportChips.setSingleSelection(true);
        transportChips.setSelectionRequired(true);
        transportChips.setChipSpacingHorizontal(dp(8));
        Chip icmpChip = chip("ICMP", true);
        Chip udpChip = chip("UDP", true);
        transportChips.addView(icmpChip);
        transportChips.addView(udpChip);
        String savedTransport = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("traceroute_mode", "ICMP");
        ("UDP".equalsIgnoreCase(savedTransport) ? udpChip : icmpChip).setChecked(true);
        icmpChip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("traceroute_mode", "ICMP").apply();
        });
        udpChip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("traceroute_mode", "UDP").apply();
        });
        optionsBody.addView(transportChips, matchWrap());

        TextView probesLabel = text("PROBES PER HOP", 11, MUTED, true);
        optionsBody.addView(probesLabel, lp(0, 4, 0, 0));
        probesChips = new ChipGroup(this);
        probesChips.setSingleSelection(true);
        probesChips.setSelectionRequired(true);
        Chip oneProbe = chip("1 probe", true);
        Chip threeProbes = chip("3 probes", true);
        probesChips.addView(oneProbe);
        probesChips.addView(threeProbes);
        oneProbe.setChecked(true);
        optionsBody.addView(probesChips, matchWrap());

        MaterialButton traceButton = new MaterialButton(this);
        traceButton.setText("Start traceroute");
        traceButton.setTextSize(15);
        traceButton.setIconResource(android.R.drawable.ic_media_play);
        traceButton.setIconPadding(dp(8));
        traceButton.setCornerRadius(dp(14));
        content.addView(traceButton, lp(0, 2, 0, 10));
        traceButton.setOnClickListener(v -> startTrace(traceButton));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        CircularProgressIndicator progress = new CircularProgressIndicator(this);
        progress.setIndicatorSize(dp(18));
        progress.setTrackThickness(dp(2));
        progress.setVisibility(View.GONE);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(20), dp(20)));
        statusText = text("Ready when you are", 13, MUTED, false);
        LinearLayout.LayoutParams statusTextLp = new LinearLayout.LayoutParams(0, dp(24), 1f);
        statusTextLp.leftMargin = dp(8);
        statusRow.addView(statusText, statusTextLp);
        content.addView(statusRow, lp(2, 0, 0, 12));
        traceButton.setTag(progress);

        MaterialCardView summaryCard = card();
        LinearLayout summaryBody = vertical(4);
        summaryBody.setPadding(dp(16), dp(12), dp(16), dp(12));
        summaryText = text("No trace yet · Results will appear here", 13, MUTED, false);
        summaryBody.addView(summaryText, matchWrap());
        summaryCard.addView(summaryBody);
        content.addView(summaryCard, lp(0, 0, 0, 14));

        LinearLayout mapHeading = new LinearLayout(this);
        mapHeading.setGravity(Gravity.CENTER_VERTICAL);
        mapHeading.addView(text("Hop map", 17, DARK, true), new LinearLayout.LayoutParams(0, dp(36), 1f));
        MaterialButton expandMapButton = new MaterialButton(this);
        expandMapButton.setText("Full screen");
        expandMapButton.setTextSize(11);
        expandMapButton.setMinHeight(dp(36));
        expandMapButton.setInsetTop(0);
        expandMapButton.setInsetBottom(0);
        expandMapButton.setCornerRadius(dp(12));
        expandMapButton.setIconResource(android.R.drawable.ic_menu_view);
        expandMapButton.setIconPadding(dp(5));
        expandMapButton.setOnClickListener(v -> openFullScreenMap());
        mapHeading.addView(expandMapButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        content.addView(mapHeading, lp(2, 0, 0, 8));

        MaterialCardView mapCard = card();
        LinearLayout mapBody = vertical(0);
        mapBody.setPadding(dp(4), dp(4), dp(4), dp(4));
        mapContainer = new FrameLayout(this);
        mapContainer.setBackgroundColor(Color.rgb(237, 241, 247));
        TextView mapPlaceholder = text("Run a trace to plot public hops", 13, MUTED, false);
        mapPlaceholder.setGravity(Gravity.CENTER);
        mapContainer.addView(mapPlaceholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapBody.addView(mapContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(248)));
        mapCard.addView(mapBody);
        content.addView(mapCard, lp(0, 0, 0, 10));

        TextView mapNote = text("Pins use city-level IP data, not the router's verified physical address. Some networks hide hops or return no reply.",
                11, MUTED, false);
        content.addView(mapNote, lp(2, 0, 0, 18));

        LinearLayout hopsHeading = new LinearLayout(this);
        hopsHeading.setGravity(Gravity.CENTER_VERTICAL);
        hopsHeading.addView(text("Hop details", 17, DARK, true), new LinearLayout.LayoutParams(0, dp(28), 1f));
        resultsTitle = text("0 hops", 12, MUTED, true);
        hopsHeading.addView(resultsTitle);
        content.addView(hopsHeading, lp(0, 0, 0, 8));

        hopsContainer = vertical(8);
        content.addView(hopsContainer, matchWrap());

        TextView footer = text("Geolocation by IPinfo · Map data © OpenStreetMap contributors · Rendered with MapLibre",
                10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        content.addView(footer, lp(0, 20, 0, 0));

        targetInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                traceButton.performClick();
                return true;
            }
            return false;
        });

        setupMap(savedInstanceState);
    }

    private void setupMap(Bundle state) {
        mapView = new MapView(this);
        mapView.onCreate(state);
        mapContainer.removeAllViews();
        mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(20.0, 0.0)).zoom(1.4).build());
            map.setStyle(new Style.Builder().fromUri(HopMapRenderer.MAP_STYLE), style -> {
                mapRenderer = new HopMapRenderer(this, map);
                mapRenderer.install(style);
                mapRenderer.setOnHopClickListener(this::showHopDetails);
                mapReady = true;
                updateMap(true); // If geolocation finished while the map style was loading, fit now.
            });
        });
    }

    private void startTrace(MaterialButton traceButton) {
        if (isRunning) return;
        String host = targetInput.getText() == null ? "" : targetInput.getText().toString().trim();
        if (host.isEmpty()) {
            targetInput.setError("Enter a hostname or IP address");
            return;
        }
        targetInput.setError(null);

        int familyChipId = familyChips.getCheckedChipId();
        Chip selectedFamilyChip = familyChips.findViewById(familyChipId);
        String selectedFamily = selectedFamilyChip == null ? "Auto" : selectedFamilyChip.getText().toString();
        int dnsFamily = "IPv4".equals(selectedFamily) ? CustomDnsResolver.FAMILY_IPV4 :
                ("IPv6".equals(selectedFamily) ? CustomDnsResolver.FAMILY_IPV6 : CustomDnsResolver.FAMILY_AUTO);

        boolean useCustomDns = dnsCustomChip != null && dnsCustomChip.isChecked();
        String customDnsServer = useCustomDns && dnsServerInput.getText() != null
                ? dnsServerInput.getText().toString().trim() : "";
        dnsServerLayout.setError(null);
        if (useCustomDns) {
            try {
                CustomDnsResolver.validateDnsServer(customDnsServer);
            } catch (Exception e) {
                dnsServerLayout.setError(e.getMessage());
                dnsServerInput.requestFocus();
                return;
            }
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putString("dns_mode", useCustomDns ? "custom" : "system")
                .putString("custom_dns_server", customDnsServer)
                .apply();

        Chip transportChip = transportChips.findViewById(transportChips.getCheckedChipId());
        String transportLabel = transportChip == null ? "ICMP" : transportChip.getText().toString().toUpperCase(Locale.ROOT);
        int transportMode = "UDP".equals(transportLabel) ? NativeTraceroute.MODE_UDP : NativeTraceroute.MODE_ICMP;
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putString("traceroute_mode", transportLabel).apply();

        int maxHops = (int) maxHopsSlider.getValue();
        int timeoutMs = (int) timeoutSlider.getValue();
        Chip probesChip = probesChips.findViewById(probesChips.getCheckedChipId());
        int probes = probesChip != null && probesChip.getText().toString().startsWith("3") ? 3 : 1;
        String ipinfoToken = getSharedPreferences("settings", MODE_PRIVATE).getString("ipinfo_token", "");

        isRunning = true;
        traceStartedAt = System.currentTimeMillis();
        hopResults.clear();
        requestedGeoIps.clear();
        finishedHopCount = 0;
        updateMap(false); // Clear the previous route immediately; keep the current camera position.
        renderHops();
        statusText.setText("Resolving " + host + " using " + (useCustomDns ? "custom DNS" : "system DNS") + "…");
        summaryText.setText("Preparing " + transportLabel + " probes · Max " + maxHops + " hops");
        traceButton.setEnabled(false);
        traceButton.setText("Tracing…");
        CircularProgressIndicator progress = (CircularProgressIndicator) traceButton.getTag();
        progress.setVisibility(View.VISIBLE);

        traceExecutor.execute(() -> {
            try {
                InetAddress resolvedAddress = CustomDnsResolver.resolve(
                        host, dnsFamily, useCustomDns ? customDnsServer : null);
                boolean useIpv6 = resolvedAddress instanceof Inet6Address;
                String numericTarget = resolvedAddress.getHostAddress();
                String resolvedFamily = useIpv6 ? "IPv6" : "IPv4";
                String dnsDescription = useCustomDns ? "custom DNS " + customDnsServer : "system DNS";
                mainHandler.post(() -> statusText.setText("Tracing " + numericTarget + " via " +
                        dnsDescription + " · " + resolvedFamily + " · " + transportLabel + "…"));

                // Pass the numeric result to JNI so native code never performs a second, implicit DNS lookup.
                String raw = NativeTraceroute.run(numericTarget, useIpv6, transportMode,
                        maxHops, timeoutMs, probes);
                JSONObject result = new JSONObject(raw);
                if (result.has("error")) throw new IllegalStateException(result.optString("error"));
                JSONArray hops = result.optJSONArray("hops");
                List<HopResult> parsed = new ArrayList<>();
                if (hops != null) {
                    for (int i = 0; i < hops.length(); i++) {
                        JSONObject item = hops.getJSONObject(i);
                        parsed.add(new HopResult(item.optInt("ttl"), item.optString("ip"),
                                item.isNull("rtt_ms") ? -1 : item.optDouble("rtt_ms", -1),
                                item.optString("type", "TIMEOUT")));
                    }
                }
                boolean reached = result.optBoolean("reached", false);
                mainHandler.post(() -> {
                    hopResults.clear();
                    hopResults.addAll(parsed);
                    isRunning = false;
                    finishedHopCount = parsed.size();
                    long elapsed = System.currentTimeMillis() - traceStartedAt;
                    statusText.setText(reached ? "Destination reached" : "Trace complete · destination not confirmed");
                    summaryText.setText(parsed.size() + " hops · " + transportLabel + " · " +
                            (reached ? "destination reached" : "not reached") + " · " + formatElapsed(elapsed));
                    traceButton.setEnabled(true);
                    traceButton.setText("Run again");
                    progress.setVisibility(View.GONE);
                    resultsTitle.setText(parsed.size() + " hops");
                    renderHops();
                    updateMap(false);
                    queueGeoLookups(ipinfoToken);
                });
            } catch (Exception | LinkageError e) {
                mainHandler.post(() -> {
                    isRunning = false;
                    traceButton.setEnabled(true);
                    traceButton.setText("Start traceroute");
                    progress.setVisibility(View.GONE);
                    statusText.setText("Trace failed");
                    summaryText.setText(e.getMessage() == null ? "Traceroute failed." : e.getMessage());
                    Toast.makeText(this, summaryText.getText(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void queueGeoLookups(String token) {
        List<HopResult> pending = new ArrayList<>();
        for (HopResult hop : hopResults) {
            if (hop.ip.isEmpty() || !GeoIpClient.isPublicAddress(hop.ip) || !requestedGeoIps.add(hop.ip)) continue;
            pending.add(hop);
        }
        if (pending.isEmpty()) {
            updateMap(true);
            return;
        }

        // Draw each newly located POP immediately, but do not repeatedly animate the camera
        // while three lookup workers are finishing out of order. Fit the complete route once.
        AtomicInteger remaining = new AtomicInteger(pending.size());
        for (HopResult hop : pending) {
            geoExecutor.execute(() -> {
                try {
                    GeoInfo info = GeoIpClient.lookup(getApplicationContext(), hop.ip, token);
                    hop.geoInfo = info;
                } catch (Exception e) {
                    hop.geoError = e.getMessage();
                }
                mainHandler.post(() -> {
                    // The same router can appear at multiple TTLs. Reuse the one
                    // lookup result for every occurrence without extra API calls.
                    for (HopResult candidate : hopResults) {
                        if (hop.ip.equals(candidate.ip)) {
                            candidate.geoInfo = hop.geoInfo;
                            candidate.geoError = hop.geoError;
                        }
                    }
                    renderHops();
                    boolean allLookupsFinished = remaining.decrementAndGet() == 0;
                    updateMap(allLookupsFinished);
                });
            });
        }
    }

    private void renderHops() {
        if (hopsContainer == null) return;
        hopsContainer.removeAllViews();
        if (hopResults.isEmpty()) {
            TextView empty = text("No hop data yet. Enter a destination above and start a trace.", 13, MUTED, false);
            empty.setPadding(dp(4), dp(10), dp(4), dp(10));
            hopsContainer.addView(empty, matchWrap());
            resultsTitle.setText("0 hops");
            return;
        }
        resultsTitle.setText(hopResults.size() + " hops");
        for (HopResult hop : hopResults) {
            MaterialCardView itemCard = card();
            itemCard.setCardElevation(0f);
            itemCard.setStrokeColor(LINE);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));

            TextView number = text(String.format(Locale.US, "%02d", hop.ttl), 12, BLUE, true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(roundRect(Color.rgb(235, 241, 255), dp(10)));
            row.addView(number, new LinearLayout.LayoutParams(dp(38), dp(36)));

            LinearLayout details = vertical(3);
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            detailsLp.leftMargin = dp(12);
            if (hop.timedOut()) {
                details.addView(text("*  Request timed out", 13, MUTED, true), matchWrap());
                details.addView(text("No response from this hop", 11, MUTED, false), matchWrap());
            } else {
                details.addView(text(hop.ip, 13, DARK, true), matchWrap());
                if (hop.geoInfo != null) {
                    details.addView(text(hop.geoInfo.displayLocation(), 11, MUTED, false), matchWrap());
                    if (!hop.geoInfo.organization.isEmpty()) {
                        details.addView(text(hop.geoInfo.organization, 10, MUTED, false), matchWrap());
                    }
                } else if (hop.geoError != null) {
                    String message = hop.geoError.contains("IPinfo token") ? "Add IPinfo token to map public hops" : "Location lookup unavailable";
                    details.addView(text(message, 11, MUTED, false), matchWrap());
                } else if (!GeoIpClient.isPublicAddress(hop.ip)) {
                    details.addView(text("Private / non-global address · not queried", 11, MUTED, false), matchWrap());
                } else {
                    details.addView(text("Looking up approximate IP location…", 11, MUTED, false), matchWrap());
                }
            }
            row.addView(details, detailsLp);

            String rtt = hop.rttMs < 0 ? "—" : new DecimalFormat("0.0").format(hop.rttMs) + " ms";
            TextView timing = text(rtt, 11, hop.timedOut() ? MUTED : BLUE, true);
            timing.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(timing, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            itemCard.addView(row);
            itemCard.setOnClickListener(v -> focusHop(hop));
            hopsContainer.addView(itemCard, matchWrap());
        }
    }

    private void updateMap() {
        updateMap(false);
    }

    private void updateMap(boolean fitCamera) {
        if (!mapReady || mapRenderer == null) return;
        mapRenderer.render(hopResults, fitCamera);
    }

    private void focusHop(HopResult hop) {
        if (map == null || hop.geoInfo == null || !hop.geoInfo.hasCoordinates()) return;
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(hop.geoInfo.latitude, hop.geoInfo.longitude), 5.0), 500);
    }

    private void showHopDetails(String title, String details) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    private void openFullScreenMap() {
        JSONArray array = new JSONArray();
        for (HopResult hop : hopResults) {
            JSONObject item = new JSONObject();
            try {
                item.put("ttl", hop.ttl);
                item.put("ip", hop.ip);
                item.put("rtt", hop.rttMs);
                item.put("type", hop.type);
                if (hop.geoInfo != null && hop.geoInfo.hasCoordinates()) {
                    item.put("latitude", hop.geoInfo.latitude);
                    item.put("longitude", hop.geoInfo.longitude);
                    item.put("city", hop.geoInfo.city);
                    item.put("region", hop.geoInfo.region);
                    item.put("country", hop.geoInfo.country);
                    item.put("organization", hop.geoInfo.organization);
                }
                array.put(item);
            } catch (Exception ignored) {
                // Continue serializing the remaining hop records.
            }
        }
        Intent intent = new Intent(this, HopMapActivity.class);
        intent.putExtra(HopMapActivity.EXTRA_HOPS_JSON, array.toString());
        startActivity(intent);
    }

    private void showApiSettings() {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint("IPinfo access token");
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String existing = getSharedPreferences("settings", MODE_PRIVATE).getString("ipinfo_token", "");
        input.setText(existing);
        layout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout wrapper = vertical(10);
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);
        wrapper.addView(layout, matchWrap());
        TextView info = text("Use an IPinfo City-level token. The app caches each public IP lookup for 7 days. The token is stored only on this device.",
                12, MUTED, false);
        wrapper.addView(info, matchWrap());
        new MaterialAlertDialogBuilder(this)
                .setTitle("IP geolocation")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear token", (dialog, which) -> {
                    getSharedPreferences("settings", MODE_PRIVATE).edit().remove("ipinfo_token").apply();
                    Toast.makeText(this, "IPinfo token cleared", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putString("ipinfo_token", value).apply();
                    Toast.makeText(this, value.isEmpty() ? "Token removed" : "IPinfo token saved", Toast.LENGTH_SHORT).show();
                    requestedGeoIps.clear();
                    if (!hopResults.isEmpty()) queueGeoLookups(value);
                })
                .show();
    }

    private void familyBody(LinearLayout parent, TextView label) {
        parent.addView(label, lp(0, 10, 0, 2));
    }

    private Chip chip(String label, boolean checkable) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(checkable);
        chip.setChipCornerRadius(dp(18));
        chip.setTextSize(13);
        chip.setChipStrokeWidth(dp(1));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(LINE));
        chip.setChipBackgroundColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(231, 238, 255), Color.WHITE}));
        chip.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BLUE, DARK}));
        return chip;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(LINE);
        return card;
    }

    private LinearLayout vertical(int spacingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams lp(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatElapsed(long ms) {
        if (ms < 1000) return ms + " ms";
        return new DecimalFormat("0.0").format(ms / 1000.0) + " s";
    }

    // Package-private runtime-test hook. It reports style readiness, not merely MapView creation.
    boolean isMapStyleReadyForTesting() { return mapReady; }

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) mapView.onDestroy();
        traceExecutor.shutdownNow();
        geoExecutor.shutdown();
        super.onDestroy();
    }
}

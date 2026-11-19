package com.hoptrace.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Android instrumentation checks for the UI, resolver parser, map activity and native probes. */
@RunWith(AndroidJUnit4.class)
public final class HopTraceRuntimeTest {

    @Test
    public void mainActivityStartsAndSettingsAndModeControlsWork() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("Trace the path. See every hop.")).check(matches(isDisplayed()));
            onView(withText("Start traceroute")).check(matches(isDisplayed()));
            onView(withId(R.id.dns_mode_group)).check(matches(isDisplayed()));
            onView(withId(R.id.transport_mode_group)).check(matches(isDisplayed()));

            onView(withText("Custom DNS")).perform(click());
            onView(withId(R.id.dns_server_input)).check(matches(isDisplayed()));
            onView(withText("System default")).perform(click());

            onView(withId(R.id.menu_ipinfo)).check(matches(isDisplayed())).perform(click());
            onView(withText("IP geolocation")).check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).perform(click());
            onView(withText("Start traceroute")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void fullScreenMapActivityOpens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("Full screen")).perform(click());
            onView(withText("HopTrace · route map")).check(matches(isDisplayed()));
        }
    }

    @Test
    public void mapLibreStyleLoads() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            long deadline = SystemClock.elapsedRealtime() + 45000L;
            boolean[] ready = {false};
            while (SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> ready[0] = activity.isMapStyleReadyForTesting());
                if (ready[0]) break;
                SystemClock.sleep(250L);
            }
            assertTrue("MapLibre style did not finish loading within 45 seconds", ready[0]);
        }
    }

    @Test
    public void geoIpLookupRejectsPrivateAndAcceptsPublicAddresses() {
        assertFalse("Loopback must never be sent to IPinfo", GeoIpClient.isPublicAddress("127.0.0.1"));
        assertFalse("RFC1918 address must never be sent to IPinfo", GeoIpClient.isPublicAddress("192.168.1.1"));
        assertFalse("IPv6 ULA must never be sent to IPinfo", GeoIpClient.isPublicAddress("fd00::1"));
        assertTrue("Public IPv4 should be eligible for IPinfo", GeoIpClient.isPublicAddress("1.1.1.1"));
        assertTrue("Public IPv6 should be eligible for IPinfo", GeoIpClient.isPublicAddress("2606:4700:4700::1111"));
    }

    @Test
    public void customDnsAcceptsIpv4AndIpv6ServersAndParsesAAndAaaa() throws Exception {
        CustomDnsResolver.validateDnsServer("1.1.1.1");
        CustomDnsResolver.validateDnsServer("2606:4700:4700::1111");
        assertEquals("1.2.3.4", CustomDnsResolver.parseResponse(
                dnsAnswer(0x1234, "example.com", 1, new byte[]{1, 2, 3, 4}), 0x1234, 1).getHostAddress());
        byte[] ipv6 = new byte[16];
        ipv6[0] = 0x20; ipv6[1] = 0x01; ipv6[2] = 0x0d; ipv6[3] = (byte) 0xb8;
        ipv6[15] = 1;
        assertEquals("2001:db8:0:0:0:0:0:1", CustomDnsResolver.parseResponse(
                dnsAnswer(0x5678, "example.com", 28, ipv6), 0x5678, 28).getHostAddress());
    }

    @Test
    public void nativeIpv4IcmpTracerouteReachesLoopback() throws Exception {
        JSONObject result = new JSONObject(NativeTraceroute.run(
                "127.0.0.1", false, NativeTraceroute.MODE_ICMP, 3, 1200, 1));
        assertFalse("IPv4 ICMP traceroute returned an error: " + result.optString("error"), result.has("error"));
        assertTrue("IPv4 ICMP traceroute did not reach loopback: " + result, result.optBoolean("reached", false));
        JSONArray hops = result.optJSONArray("hops");
        assertTrue("IPv4 trace should include at least one hop: " + result, hops != null && hops.length() >= 1);
    }

    @Test
    public void nativeIpv6IcmpTracerouteReachesLoopback() throws Exception {
        JSONObject result = new JSONObject(NativeTraceroute.run(
                "::1", true, NativeTraceroute.MODE_ICMP, 3, 1200, 1));
        assertFalse("IPv6 ICMP traceroute returned an error: " + result.optString("error"), result.has("error"));
        assertTrue("IPv6 ICMP traceroute did not reach loopback: " + result, result.optBoolean("reached", false));
        JSONArray hops = result.optJSONArray("hops");
        assertTrue("IPv6 trace should include at least one hop: " + result, hops != null && hops.length() >= 1);
    }

    @Test
    public void nativeIpv4UdpTracerouteReachesLoopback() throws Exception {
        JSONObject result = new JSONObject(NativeTraceroute.run(
                "127.0.0.1", false, NativeTraceroute.MODE_UDP, 3, 1200, 1));
        assertFalse("IPv4 UDP traceroute returned an error: " + result.optString("error"), result.has("error"));
        assertTrue("IPv4 UDP traceroute did not reach loopback: " + result, result.optBoolean("reached", false));
        assertEquals("UDP", result.optString("mode"));
    }

    @Test
    public void nativeIpv6UdpTracerouteReachesLoopback() throws Exception {
        JSONObject result = new JSONObject(NativeTraceroute.run(
                "::1", true, NativeTraceroute.MODE_UDP, 3, 1200, 1));
        assertFalse("IPv6 UDP traceroute returned an error: " + result.optString("error"), result.has("error"));
        assertTrue("IPv6 UDP traceroute did not reach loopback: " + result, result.optBoolean("reached", false));
        assertEquals("UDP", result.optString("mode"));
    }

    private static byte[] dnsAnswer(int id, String host, int type, byte[] address) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, id);
        write16(out, 0x8180); // response, recursion available, no error
        write16(out, 1); // one question
        write16(out, 1); // one answer
        write16(out, 0);
        write16(out, 0);
        for (String label : host.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.write(bytes);
        }
        out.write(0);
        write16(out, type);
        write16(out, 1); // IN
        out.write(0xc0); out.write(0x0c); // answer owner points to qname
        write16(out, type);
        write16(out, 1);
        out.write(new byte[]{0, 0, 0, 60}); // TTL 60
        write16(out, address.length);
        out.write(address);
        return out.toByteArray();
    }

    private static void write16(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }
}

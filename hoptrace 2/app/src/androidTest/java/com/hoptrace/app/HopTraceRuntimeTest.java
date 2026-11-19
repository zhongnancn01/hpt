package com.hoptrace.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs on an Android emulator/device, exercising app startup and the native ICMP stack. */
@RunWith(AndroidJUnit4.class)
public final class HopTraceRuntimeTest {

    @Test
    public void mainActivityStartsAndIpinfoDialogOpens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("Trace the path. See every hop.")).check(matches(isDisplayed()));
            onView(withText("Start traceroute")).check(matches(isDisplayed()));
            onView(withId(R.id.menu_ipinfo)).check(matches(isDisplayed())).perform(click());
            onView(withText("IP geolocation")).check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).perform(click());
            onView(withText("Start traceroute")).check(matches(isDisplayed()));
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
        assertTrue("Loopback must never be sent to IPinfo", !GeoIpClient.isPublicAddress("127.0.0.1"));
        assertTrue("RFC1918 address must never be sent to IPinfo", !GeoIpClient.isPublicAddress("192.168.1.1"));
        assertTrue("IPv6 ULA must never be sent to IPinfo", !GeoIpClient.isPublicAddress("fd00::1"));
        assertTrue("Public IPv4 should be eligible for IPinfo", GeoIpClient.isPublicAddress("1.1.1.1"));
        assertTrue("Public IPv6 should be eligible for IPinfo", GeoIpClient.isPublicAddress("2606:4700:4700::1111"));
    }

    @Test
    public void nativeIpv4IcmpTracerouteReachesLoopback() throws Exception {
        JSONObject result = new JSONObject(NativeTraceroute.run("127.0.0.1", false, 3, 1200, 1));
        assertFalse("IPv4 native traceroute returned an error: " + result.optString("error"),
                result.has("error"));
        assertTrue("IPv4 ICMP traceroute did not reach loopback: " + result,
                result.optBoolean("reached", false));
        JSONArray hops = result.optJSONArray("hops");
        assertTrue("IPv4 trace should include at least one hop: " + result,
                hops != null && hops.length() >= 1);
    }

    @Test
    public void nativeIpv6IcmpTracerouteReachesLoopback() throws Exception {
        JSONObject result = new JSONObject(NativeTraceroute.run("::1", true, 3, 1200, 1));
        assertFalse("IPv6 native traceroute returned an error: " + result.optString("error"),
                result.has("error"));
        assertTrue("IPv6 ICMP traceroute did not reach loopback: " + result,
                result.optBoolean("reached", false));
        JSONArray hops = result.optJSONArray("hops");
        assertTrue("IPv6 trace should include at least one hop: " + result,
                hops != null && hops.length() >= 1);
    }
}

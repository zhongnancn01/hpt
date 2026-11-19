# HopTrace for Android

HopTrace is a native Android network diagnostic app for ICMP traceroute over IPv4 and IPv6. It enriches public router-hop IPs with IPinfo City-level records and plots available coordinates on an OpenFreeMap basemap rendered by MapLibre Native.

## App and Android versions

- App version: **1.1.1** (versionCode 7)
- `minSdk`: 26 (Android 8.0)
- `compileSdk` / `targetSdk`: 37 (Android 17)
- Supported target devices: Android 15 (API 35), Android 16 (API 36), Android 17 (API 37)
- Java 17 + Android NDK C++17
- MapLibre Native OpenGL 13.6.1
- Material Components 1.13.0

## Features

- ICMP Echo traceroute with IPv4 TTL / IPv6 Hop Limit stepping.
- IPv4, IPv6 and Auto family selection.
- Linux ping sockets (`SOCK_DGRAM`) with the kernel extended error queue (`IP_RECVERR` / `IPV6_RECVERR`); no root permission is requested.
- Configurable maximum hops, per-probe timeout and one or three probes per hop.
- Live hop list with router IP, response type and round-trip time.
- IPinfo City-level city, region, country and ASN organization lookup for public hop IPs.
- Seven-day local cache and duplicate-IP de-duplication.
- Private, loopback, link-local, multicast and documentation IP ranges are excluded from external geolocation lookup.
- MapLibre + OpenFreeMap `liberty` map style; no map API key is required.

## IP geolocation setup

1. Create an IPinfo account and obtain an access token for a **City-level** product/API. Do not use IPinfo Lite if city-level coordinates are required.
2. Install HopTrace and open **IPinfo API** in the top toolbar.
3. Paste the token and choose **Save**.
4. Run a trace. Public hop IPs are looked up and cached locally for seven days.

The app deliberately does not contain a shared developer token. The token is stored in the app's private SharedPreferences. For a public multi-user production release, place the IPinfo token behind a backend proxy rather than shipping a shared key in an APK.

## Map and location accuracy

The map plots the `loc` coordinates returned by the IP geolocation provider. These represent an IP database's approximate city/network location, not the router's verified physical address or GPS position. Private hops and addresses without usable city coordinates are not plotted. Internet access is required to load the OpenFreeMap style and tiles.

## ICMP limitations

The implementation uses unprivileged Linux ping sockets rather than privileged raw sockets. Android/AOSP builds normally permit ping sockets for app groups, but OEM kernels, enterprise policies, VPNs, mobile carriers and firewalls can restrict or rate-limit ICMP. Some routers do not return TTL Exceeded messages. Consequently, timeouts (`*`) are normal and a complete route cannot be guaranteed on every network/device.

## Build and runtime validation (GitHub Actions)

The repository includes `.github/workflows/build-apk.yml`. Push to `main`/`master`, or open **Actions → HopTrace - build, device-test, and package APK → Run workflow**.

The workflow:

1. Installs JDK 17, Android API 37, Build Tools, NDK and CMake.
2. Locates the Gradle project even if it is nested in a directory.
3. Verifies the two TextInputLayout children use `LinearLayout.LayoutParams` (the exact startup crash found in the attached Firebase Logcat).
4. Applies the Maven Central mirror used for Gradle dependency resolution and checks the Kotlin Gradle Plugin artifacts that failed in an earlier run.
5. Runs Android instrumentation tests on API 35, 36 and 37 emulators. Tests cover Activity launch, IPinfo settings dialog, MapLibre style loading, IPv4/IPv6 ICMP loopback and public/private IP filtering.
6. Checks the API 37 APK signature, ZIP alignment, package/version/SDK metadata and native libraries for ARM64, ARMv7 and x86_64.
7. Publishes the raw `.apk` file only if all matrix tests and APK checks pass.

Download `HopTrace-1.1.1-debug.apk` directly from the completed Actions run. It is published as an unarchived single-file artifact, so no ZIP extraction is required.

### Replacing an earlier APK

GitHub-hosted runners generate a debug signing key per runner. If Android reports a signature conflict when installing this new APK over an earlier build, uninstall the old HopTrace app first, then install the new APK.

## Build locally

Open the project root in Android Studio or run:

```bash
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The included GitHub Actions workflow installs the required toolchain and also performs emulator tests.

## Runtime test boundary

The source archive includes emulator instrumentation tests, but this archive has not itself been built or run on an Android emulator in the current authoring environment. The final GitHub Actions artifact is gated on successful tests for Android API 35/36/37. A passing emulator suite is a device-level smoke test; it does not guarantee ICMP behaviour on every physical OEM/carrier network.

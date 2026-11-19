# HopTrace Android

HopTrace is a native Android network-diagnostics app. It traces routes over IPv4 or IPv6 using ICMP Echo or UDP probes, enriches public hop IPs with IPinfo City-level location data, and displays the resulting POPs on an interactive MapLibre map.

## App / Android versions

- App version: **1.2.0** (versionCode 8)
- `minSdk`: 26 (Android 8.0)
- `compileSdk` / `targetSdk`: 37 (Android 17)
- Intended devices: Android 15 (API 35), Android 16 (API 36), Android 17 (API 37)
- Java 17, Android NDK `28.2.13676358`, CMake `3.22.1`
- MapLibre Native OpenGL `13.6.1`

## Features

- IPv4, IPv6, and automatic address-family selection.
- **System DNS by default**, with an optional custom numeric DNS resolver address. The custom resolver can be IPv4 or IPv6 and uses DNS over UDP port 53, with TCP fallback when the DNS reply is truncated. A/AAAA records are selected to match IPv4/IPv6 mode. The hostname is resolved before entering JNI, and the native traceroute receives only the numeric result.
- **ICMP or UDP traceroute**, selectable in the UI. ICMP mode retains the original Linux ping-socket/error-queue implementation. UDP mode sends probes to incrementing destination ports starting at 33434 and reads the kernel extended error queue for TTL-exceeded and destination-port-unreachable messages.
- Configurable maximum hops, per-probe timeout, and one or three probes per hop.
- IPinfo City-level lookup for public hop addresses only; city, region, country, ASN organization, and coordinates are retained. The user's own IPinfo token is stored locally in app-private preferences; the app does not embed a shared token.
- Seven-day on-device geolocation cache and de-duplication when the same hop IP appears more than once.
- Layered route rendering: white route casing plus blue segments, individual POP markers and hop numbers, orange cross-country segments, and a country label at the first located POP in each newly encountered country. A country label contains the country name and that POP IP.
- Tap a POP marker or country label to open its details. The **Full screen** control opens the same route in an interactive full-screen map.
- MapLibre Native OpenGL with the OpenFreeMap Liberty style. No map API key is configured in the app.

## IP geolocation setup

1. Obtain an IPinfo City-level token for a plan/API that includes city coordinates.
2. Install HopTrace and open **IPinfo API** in the top toolbar.
3. Paste the token and save it.
4. Run a trace. Public hop IPs are looked up and cached locally for seven days.

IPinfo Lite is not interchangeable with the City-level endpoint when city coordinates are needed. The token is not bundled in the APK. For a public multi-user release, place a shared provider token behind a backend proxy rather than embedding it in the application.

## DNS behavior

- **System default** uses Android's configured resolver, including the device's private-DNS/network configuration.
- **Custom DNS** accepts a numeric IPv4 or IPv6 resolver address. DNS is sent to port 53. The field does not accept a hostname or URL as the resolver address, because resolving the resolver hostname would reintroduce a dependency on the system DNS.
- Selecting IPv4 sends an A query; selecting IPv6 sends an AAAA query; Auto tries A first and falls back to AAAA if no IPv4 answer is available.
- DNS over TCP is used if the UDP response sets the truncation flag.

## Traceroute behavior and limitations

- ICMP uses Linux ping sockets (`SOCK_DGRAM`, `IP_RECVERR` / `IPV6_RECVERR`) and does not request Root permission. The original ICMP receive paths are retained.
- UDP uses ordinary UDP datagrams and Linux extended error queues. The target is considered reached when it responds with the appropriate ICMP Port Unreachable (IPv4) or ICMPv6 Port Unreachable (IPv6) for the probe port.
- Routers, VPNs, carriers, firewalls, or OEM kernels may filter ICMP, UDP, or the returned error messages. Missing hops and `*` timeouts are possible and do not necessarily mean the route is broken.
- A hostname is resolved once in Java. The numeric address is passed to native code with `AI_NUMERICHOST`, so native `getaddrinfo()` cannot silently use a second DNS resolver.

## Map and location accuracy

MapLibre plots the IPinfo `loc` coordinates for public hops with a usable location. These are database estimates (often an ISP/POP/city location), not GPS-verified router coordinates. Private, CGNAT, link-local, loopback, multicast, and documentation addresses are not sent to IPinfo and therefore are not plotted. Route segments connect the observed hops that do have coordinates; they do not assert the exact physical fiber path. Country labels identify the first located POP for each country encountered along the trace. Internet access is needed to load the map style and tiles.

## Build APK with GitHub Actions

The repository includes `.github/workflows/build-apk.yml`. It is deliberately a **build-only** workflow: it does not start an Android emulator and does not run `connectedDebugAndroidTest`. The user chose this workflow so the Action finishes by producing an installable APK without depending on KVM/emulator availability.

The workflow:

1. Checks out the source and installs JDK 17.
2. Installs Android API 37, Build Tools 36.0.0, NDK `28.2.13676358`, and CMake `3.22.1`.
3. Locates the Gradle project root and checks that the DNS, UDP, map, manifest, and runtime-fix source files are present.
4. Verifies the Kotlin Gradle plugin artifacts via the configured Google-hosted Maven Central mirror.
5. Runs `gradle clean assembleDebug`.
6. Verifies APK ZIP alignment, signature, application ID, min/target SDK, version, and the expected native libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
7. Publishes the APK as a direct, unarchived Artifact named `HopTrace-1.2.0-debug.apk`.

Run **Actions → HopTrace - build APK only → Run workflow**. Download `HopTrace-1.2.0-debug.apk` from the run's Artifacts section. The file is the APK itself, not a ZIP. If Android refuses to update an older debug build because the ephemeral GitHub runner used a different signing key, uninstall the older HopTrace app before installing the new APK.

The workflow verifies that the APK was built and signed; it does **not** claim runtime/device-test success. For cloud-device testing, upload the APK to Firebase Test Lab and run Robo or instrumentation tests there. Firebase Test Lab tests an already-built APK; it does not compile source code.

## Local build

Open the project root in Android Studio or install the toolchain listed above, then run:

```bash
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Validation boundary for this source update

The current authoring environment has no Android SDK/NDK and cannot run an Android Gradle build or install the app on an Android device. The following checks have been completed here: strict host C++ compilation of the JNI source; host Linux UDP IPv4 and UDP IPv6 loopback traceroute; custom DNS parser tests for A/AAAA and transaction-ID handling; an end-to-end local UDP DNS-server test for both A and AAAA via an explicit resolver address; XML/YAML/Shell/source preflight checks; and ZIP integrity checks. The container denies unprivileged ICMP ping sockets, so the host environment could not execute ICMP loopback tests. The next Android build must still be run in GitHub Actions, and the new behaviors should be verified with Firebase Test Lab on the resulting APK.

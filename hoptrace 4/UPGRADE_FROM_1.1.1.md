# Upgrade HopTrace 1.1.1 to 1.2.0

This is a source overlay, not an APK. It contains only the files changed or added
for the v1.2.0 feature upgrade and the updated build-only GitHub Actions workflow.

## Apply the patch

1. Extract this ZIP.
2. Copy the extracted contents into the root of the existing Git repository.
3. Allow the file manager to replace files with the same names. Keep all other
   existing files.
4. Make sure `.github/workflows/build-apk.yml` is included. On macOS Finder,
   press Command+Shift+Period to reveal `.github` before copying, or use GitHub
   Desktop / a terminal copy command.
5. Review the changed files in GitHub Desktop, commit, and push.
6. Run `HopTrace - build APK only` in GitHub Actions. No emulator is started.
7. Download `HopTrace-1.2.0-debug.apk` from the successful run's Artifacts.

## What changes

- Adds optional custom DNS over UDP/TCP port 53 using an IPv4 or IPv6 resolver.
- Adds a user-selectable ICMP / UDP traceroute mode.
- Adds a shared MapLibre renderer with POP points, route segments, country-crossing
  highlights, first-POP country/IP labels, tap details, and a full-screen map.
- Keeps the IPinfo City API/token/cache implementation unchanged.
- Keeps the original IPv4 and IPv6 ICMP error-queue receive functions unchanged.
- Keeps the workflow build-only. Runtime tests remain a separate Firebase Test Lab step.

## Important

The ZIP contains source and workflow files, not a compiled APK. The GitHub Actions
runner must complete the build and publish the APK Artifact before it is installable.

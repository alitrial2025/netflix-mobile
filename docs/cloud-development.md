# Cloud Android development

Use the existing checkout; do not create a worktree during environment onboarding. The prepared cloud toolchain is Android SDK 35, build tools 34/35, full Temurin Java 21 and checksum-verified Gradle 8.9.

```bash
source /workspace/cloud-setup/env.sh
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Run the two repositories sequentially. Do not run Gradle and a software emulator together: this environment has four CPUs and no `/dev/kvm`. Cloud-only proxy, certificate authority, Maven mirror and Robolectric configuration live under `/workspace/cloud-setup`, outside Git. GitHub Actions validates debug builds, unit tests and lint and uploads APKs/reports for review.

Release builds require the original signing keystore and ignored signing properties. Never substitute the cloud debug key for the released app key. A blank `app/src/main/assets/update-gate.json` disables the retired update endpoint until a replacement HTTPS site is selected. Local update fixtures must override debug assets outside the checkout; do not commit local certificate keys or publish debug APKs through the release publisher.

The mobile regression suite covers independent offline catalog failures, cancellation, catalog cache recovery, episode progress/intro boundaries, HTTP Range validation, truncated transfers, unsafe HLS rejection and profile download references. Native Android UI tests exercise the redesigned Home/profile picker and the offline downloads action using deterministic TMDB poster/logo fixtures, which are excluded from the APK.

Release verification still needs physical-device offline startup and playback checks, a real membership/account, notification and unknown-source installation permissions, release signing, and a replacement HTTPS update URL. Download transfers still need a durable background execution/restore design before promising survival across process termination. Encrypted HLS, fragmented MP4, discontinuities and byte-range HLS currently fail explicitly rather than produce a corrupted download; offline multi-track audio/subtitles need a separate supported-format implementation. Remaining lint warnings and device performance are not resolved by a passing build.

Cloud validation: all 41 tests passed, debug APK built and Android lint passed. The host-rendered previews are in `docs/design-preview`.

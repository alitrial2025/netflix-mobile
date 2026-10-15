# Cloud Android development

Use the existing `/workspace/netflix-mobile` checkout. The retained cloud toolchain is Oracle JDK 21, the project's Gradle 8.9 wrapper, Android platform 35 and build tools 34/35. Activate it in each shell:

```bash
source /workspace/.netflix-tv-tools/activate.sh
cd /workspace/netflix-mobile
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
node --test tools/public-playback/*.test.mjs tools/cdn-audit/*.test.mjs
```

Run Gradle builds for the TV and mobile checkouts sequentially. This machine has four CPUs and no `/dev/kvm`; stop the software Android emulator before building. Tooling, caches, proxy trust configuration and runtime logs belong outside Git under `/workspace/.netflix-tv-tools`. Keep TLS and artifact verification enabled. Robolectric/Compose tests exercise Android behavior headlessly; they do not establish physical-device decoding or performance.

GitHub Actions runs debug builds, unit tests, lint and isolated Firestore membership/discovery rules tests before producing a signed release. Release signing uses the existing repository secrets. Never substitute a debug key or commit signing files. Production APKs must retain certificate SHA-256 `3cea049428d723e0fd69d7c9c00cdb35f08b3661468c1d4679a8ebefae034255` so existing users can upgrade while preserving accounts and downloads. Uninstalling is unnecessary and would remove app-private data.

Release publication uses the TV repository's `tools/update-gate/publish-update.mjs` and the existing website at `https://npro-app.vercel.app/`. Follow `docs/update-gate.md`; retain every previous immutable APK and the site's payment backend. Update checks run at entry, defer offline and do not interrupt an admitted player. See `docs/mobile-offline-recovery.md` for verified entitlement recovery and its account, device, revocation and expiry rules.

The download regression suite covers durable background work, account/profile ownership, transfer checkpoints, renewed links, integrity checks, local manifests and supported audio/subtitle formats. Generated local media lives in `app/src/test/resources/offline-hls`; it contains no provider content. Consult current code and tests for supported formats rather than assuming an arbitrary HLS playlist can be downloaded.

The older screenshots in `docs/emulator-checks` verify offline guest onboarding/Home/download navigation only. They do not verify an authenticated paid account, downloaded playback, upgrade retention or process restart. Any newer runtime checks must record their exact APK, fixtures, connectivity state and results separately. A component test of proof restoration or local decoding is not a full signed-in application test.

Before publishing a new release, run the relevant Node and Android checks, verify signed CI output and the original certificate, and increment the version. After publication verify the actual public APK bytes, manifest, signature and preservation of older downloads. Keep physical-phone upgrade, offline cold launch, extended offline playback and Wi-Fi loss/recovery distinct from automated checks until those scenarios have been exercised on a real device.

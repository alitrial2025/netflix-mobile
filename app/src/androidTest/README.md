# Android offline runtime fixture

`OfflinePlaybackRuntimeTest` complements JVM tests with Android's real AtomicFile,
installation ID, process lifecycle, and Media3 video/audio decoders. Its synthetic
Premium record lives under `noBackupFilesDir/runtime-offline-fixture`, never in
the production membership store. It does not sign in, contact Firebase, or grant
rights to a real account.

Build `:app:assembleDebug :app:assembleDebugAndroidTest` before starting a software
emulator in the four-CPU cloud environment. Install both APKs, then execute these
as **separate** instrumentation invocations:

```sh
adb shell am instrument -w -e class \
  'com.example.data.OfflinePlaybackRuntimeTest#prepareOfflineRestartFixture' \
  com.netflixpro.apk.test/androidx.test.runner.AndroidJUnitRunner
adb shell am force-stop com.netflixpro.apk
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w -e class \
  'com.example.data.OfflinePlaybackRuntimeTest#persistedMembershipAndLocalVideoSurviveAnOfflineProcessRestart' \
  com.netflixpro.apk.test/androidx.test.runner.AndroidJUnitRunner
```

The second invocation checks there is no active network and a different process,
restores the original account/installation-scoped proof with its original expiry,
rejects other accounts/installations and expired access, renders a real video
frame, initializes an audio decoder, and plays the complete local fixture. It
also runs the actual UpdateGateViewModel against Android's offline connectivity:
a persisted higher mandatory release/download/install session must remain saved
while entry and playback resume stay in CONTINUE.

This is a component runtime check. It does not establish authenticated splash/UI
behavior, a production account's membership, whole-film playback, physical-phone
performance, or audible audio output. Those need separate device checks.

The three-second synthetic MP4 was generated locally, with no external media:

```sh
ffmpeg -f lavfi -i testsrc2=size=160x90:rate=12 \
  -f lavfi -i sine=frequency=440:sample_rate=44100 -t 3 \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset ultrafast \
  -b:v 48k -c:a aac -b:a 32k -movflags +faststart \
  app/src/androidTest/assets/offline-runtime-fixture.mp4
```

All fixture files are test-only and excluded from the shipped APK.

# Mobile design previews

Captured from the actual Compose screens using Robolectric native Android 34 graphics (412 × 895). Production artwork comes from the current TMDB trending catalog, filtered for the active profile. These deterministic tests use TMDB poster/title-logo fixtures and extract the footer/background colors from the actual image. The image fixtures are test resources and are excluded from the APK. These previews do not establish emulator startup or physical-device performance. Home uses 52 dp translucent category chips with 16 dp corners, a muted poster-derived ambient color, reference-aligned header/hero spacing, a darker hero footer and separated actions, and an outlined Home icon in the wider bottom capsule. The selected trending artwork changes independently of the reference image; the reference poster is not bundled into production.

![Profile picker](profile-picker.png)
![Home](home.png)
![Offline downloads entry](offline-home.png)

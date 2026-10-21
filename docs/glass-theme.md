# Mobile glassy theme

The owner's final direction is **simulated frosted glass**, avoiding live blur and refraction costs on Android. Home, Clips and Search share a floating capsule; My Netflix sits in a separate circle. Headers use separate title/profile and action groups. The Npro/Home capsule wraps its contents instead of expanding. Normal mode keeps the same split layout with a plain material.

One persisted App Settings → Appearance → **Use Glassy Theme** toggle controls both header and navigation. It defaults on and is independent of membership; existing Clips entitlement remains unchanged.

## Material and performance

The frosted appearance uses a nearly opaque translucent fill, soft color blooms, a milky sheen, fine cached grain, a light rim and an ordinary cached elevation shadow. Existing hero/clip palette colors tint the material. It intentionally does not sample or blur the pixels behind it and is not Apple's Liquid Glass renderer.

There is no blur/refraction library, RuntimeShader, RenderEffect, screen capture, bitmap readback, frame polling or permanent animation loop. Main and Clips video surfaces remain unchanged. Cached brushes, grain and geometry are recalculated only for size/theme/palette changes. Palette state is read in the drawing cache, so its updates do not recompose navigation content. A tracked composition local confines appearance updates to consumers.

The tab indicator uses a bounded spring and layer translation, retaining system animation-duration behavior. Targets are at least 48dp and grow for large fonts. Centered maximum widths support tablets/foldables; scroll clearance accounts for control height and actual navigation insets. Tints are bounded and a white-scene pixel test checks small white-label contrast.

## Research and decisions

The user's examples define the visual layout. The following material/performance guidance was reviewed before the owner chose the cheaper simulation:

- [Haze usage](https://github.com/chrisbanes/haze/blob/1.6.10/docs/usage.md), especially real backdrop versus foreground blur and tint/noise handling.
- [Haze performance](https://github.com/chrisbanes/haze/blob/1.6.10/docs/performance.md), including affected-area/sampling costs and physical-device measurement limits.
- [Glass design and fallback guidance](https://github.com/chrisbanes/haze/blob/2.0.1/docs/effects/glass.md), covering shape boundaries, foreground contrast, fallback and interaction.
- [Maintainer research citing Apple design guidance](https://github.com/chrisbanes/haze/blob/2.0.1/docs/research/2026-08-31-ios-27-liquid-glass.md), covering navigation-only use, legibility and accessibility. This is secondary research.

Direct Apple HIG / Liquid Glass and Android RenderEffect documentation requests were rejected by the environment proxy (403); they were not accessed through a bypass. Numeric styling values are this app's choices, not Apple constants. The experimental live-blur implementation was removed before publication at the owner's request.

## Verification

Native Compose tests cover split navigation geometry, compact Home branding, normal/frosted rendering, small/wide windows, large fonts, tab callbacks and Clips entitlement. Pixel checks cover contrast over white imagery. Preference tests cover persistence, and settings tests exercise the shared toggle. CI runs the full app/rules/lint/release checks and signs with the original distribution key.

No physical device is attached. Frame pacing and the final visual appearance still need acceptance on representative phones; no measured device FPS improvement is claimed.

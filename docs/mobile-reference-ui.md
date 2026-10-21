# Mobile reference UI

The five Play Store promotional images supplied on16October2026 are references
for the UI inside the phone. Red backgrounds, marketing headlines, outer image
viewer chrome and touch indicators do not belong in the application.

## Reference details implemented

- Home has a compact N/Home header, Cast/Downloads/notification actions, separate
  translucent rounded Series/Films/Games/New & hot/Categories chips, and a
  floating four-destination navigation pill when membership includes Clips.
- The two outer chip ends use capsule curves. Chips and Clips action circles
  share the same translucent surface. Portrait phones retain the card layout.
  At an actual window width of 600dp or in landscape, Home fills the window with
  a cinematic backdrop, left-aligned title/actions and denser poster rails.
  Fold/unfold and multi-window resizing switch layouts without resetting Home.
- On portrait phones, header plus hero fill approximately85% of the actual viewport. The first
  content rail peeks below. Sizing follows available height and font scale;
  short windows can scroll rather than clipping the controls.
- The portrait hero uses textless portrait artwork with the real TMDB title logo
  when available (landscape artwork cropped to the card is the next fallback).
  Regular poster artwork remains in rails and as the no-logo fallback. It keeps
  a small original mark, real title art,
  three dot-separated genres, a white Play button and translucent My List.
  Its inner scrim stays transparent through62% of the image and shades the
  footer gently. The surrounding ambient background follows the artwork.
- Home rails use compact headings, portrait cards and tight gutters. Actual
  releases and personalized recommendations lead. A games rail uses real
  catalog game records and working details/navigation. A podcast rail only
  appears for explicit podcast metadata; talk shows are not relabelled podcasts.
- Clips uses portrait video, compact mute/add/share controls, circular title
  artwork, and lower-left title/genre/description with an expandable more action.
  Backgrounding pauses playback; manually paused video stays paused on resume.
  Discovery lists remain available through Home's New & hot destination.
- My Netflix uses the profile selector and compact action header, saved-moment
  landscape cards, My List, then genuinely liked titles. See all opens working
  collections. Downloads, settings, notifications, profile and account actions
  remain available. Empty personal data is not replaced with catalog examples.

## Saved playback moments

Save moment in the player stores the actual title, episode, position and a
bounded15-second moment window (shorter at the end). This is a timestamp
bookmark, not a recording or exported video. Playback opens the original title
at that position using the existing admission, offline-download and resolver
paths. Sharing sends the title and timestamp. Thumbnail artwork is the title's
real catalog artwork, not a frame extracted from that exact timestamp.

Storage is atomic, private, excluded from Android backup and scoped to both
Firebase UID and profile. Only safe metadata is retained; no stream URLs,
authentication headers or subscription authority are stored in a bookmark.
Up to100 newest moments are retained. Saving/deleting failures leave the last
durable list intact. Original subscription/device/expiry and offline update
policies remain enforced.

## Verification and limits

The capture tests use the actual Compose screens and local artwork fixtures
without Firebase accounts, a player or network requests. They check navigation,
hero actions, category targets, caption clearance, personal collection actions,
and a smaller360dp window. Their captures are generated under
`app/build/reports/reference-screenshots` and are reviewed separately from test
assertions. `MobileDesignScreenshotTest` also retains its existing captures.

Artwork, profiles, catalogs and real saved data determine screen content. These
small, promotional, perspective-scaled images cannot establish pixel identity
for every phone, stream or content edition. No red marketing background or
fictitious podcast, personal list, saved clip or installed game is added to
force an apparent match.

## Cinematic Home

The 21 October wide-screen reference supersedes the earlier centered-column
requirement. Wide Home uses the catalog's landscape backdrop (poster fallback
on failure), separate title logo (text fallback), and localized side/bottom
scrims. The image is display-sized and crossfade-free; there is no new player,
video decode, frame polling or live blur. Play, My List and artwork details
use the same callbacks and subscription rules as portrait Home. Existing
header/category controls and the glass preference remain shared.

Native screen tests cover 840dp unfolded, 840×412 and 560×360 landscape windows,
live narrow→wide→narrow resizing, action callbacks, navigation clearance and
portrait regression captures. Physical foldable/tablet verification remains
necessary for device-specific cutouts and performance; the Android APK does
not install on iPadOS.

## TMDB hero logos

Portrait Home no longer suppresses logos whenever a poster exists. Both hero
layouts use the actual TMDB wordmark when one is available; cinematic title
text and ordinary poster artwork remain useful fallbacks. English logos take
priority over language-neutral logos; unrelated foreign wordmarks are skipped.

The visible hero drives one cancellable artwork lookup, replacing the old
first-15 catalog batch that missed TV/genre heroes. Requests use typed movie/TV
keys, coalesce concurrent requests, have bounded concurrency/timeouts, and cache
successful responses. Empty logo responses are cached briefly; failed requests
are retryable after reconnect. Both renderers also clear failed-logo state on
connectivity changes so an unchanged image URL can load after going online. Leaving a hero or going offline cancels its
lookup. This never gates splash, subscriptions or playback.

Logo/textless-poster metadata survives catalog refreshes and disk-cache restarts.
A bounded artwork cache enriches the final displayed catalog as well as
discovery-only heroes; their newer synopsis/title metadata is retained.
Artwork updates merge into the latest catalog instead of replacing it with an
old full-list snapshot. Disk writes are serialized. Native captures assert the
logo image on portrait and cinematic heroes, a series beyond the old batch,
and offline/reconnect lookup lifecycle. Repository tests cover selection,
typed keys, duplicate calls, negative caching, failure recovery and cancellation.

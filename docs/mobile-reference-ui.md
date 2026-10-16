# Mobile reference UI

The five Play Store promotional images supplied on16October2026 are references
for the UI inside the phone. Red backgrounds, marketing headlines, outer image
viewer chrome and touch indicators do not belong in the application.

## Reference details implemented

- Home has a compact N/Home header, Cast/Downloads/notification actions, separate
  translucent rounded Series/Films/Games/New & hot/Categories chips, and a
  floating four-destination navigation pill when membership includes Clips.
- The two outer chip ends use capsule curves. Chips and Clips action circles
  share the same translucent surface. Home retains a centered portrait column
  on wide tablet/foldable windows; rails measure the actual column width.
- Header plus hero fill approximately85% of the actual viewport. The first
  content rail peeks below. Sizing follows available height and font scale;
  short windows can scroll rather than clipping the controls.
- The hero preserves portrait artwork, a small original mark, real title art,
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

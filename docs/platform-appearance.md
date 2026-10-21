# Platforms appearance

Mobile 1.36 / code 37 adds App Settings → Appearance → **Platforms**. It defaults to off. Off uses the existing NetflixPro layouts, colors and glass controls. Enabling it while a service is selected in Home Categories immediately applies that service's appearance. Selecting another service changes the appearance app-wide. Genre selections, tabs and clearing the content filter retain the last chosen appearance. Turning Platforms off restores the original design; re-enabling restores the saved choice. Netflix uses the existing Netflix-style UI.

The choice is stored with device appearance preferences, independently of accounts, profiles, plans, entitlements, payment receipts, catalog availability and playback sources. Existing plan checks and profile maturity restrictions still apply. UI changes do not grant a third-party subscription. The setting is available on every plan.

## Design and references

The implementation uses the current Android screenshots on these official store listings, retrieved 21 October 2026:

- [Prime Video](https://play.google.com/store/apps/details?id=com.amazon.avod.thirdpartyclient): dark navy/black surfaces, compact header, outlined category controls, portrait poster rows, white play buttons, blue selection indicators, compact detail actions.
- [Disney+](https://play.google.com/store/apps/details?id=com.disney.disneyplus): dark charcoal/violet surfaces, turquoise accents, centered featured titles, compact poster grids and a flat bottom dock.
- [JioHotstar](https://play.google.com/store/apps/details?id=in.startv.hotstar&gl=IN): rounded featured cards, circular play/list actions, white playback buttons, dark surfaces, profile/library and settings emphasis. The category remains named Hotstar; the themed header uses JioHotstar.
- [Apple TV](https://play.google.com/store/apps/details?id=com.apple.atve.androidtv.appletv): tall phone feature, white pill playback control, compact list action, landscape library artwork, black surfaces, selected-tab capsule.
- [Peacock](https://play.google.com/store/apps/details?id=com.peacocktv.peacockandroid): rounded feature, yellow pill actions, charcoal canvas, portrait posters and a yellow selected-tab underline.
- [Netflix](https://play.google.com/store/apps/details?id=com.netflix.mediaclient): preserves the app's existing reference-based Netflix-style design.

These are adaptations for NetflixPro's actual screens and features, not copies of proprietary applications or a claim that every region/version has an identical UI. Store promotion backgrounds are excluded. Platform branding uses text; the account, plan, downloads, Clips, search and library features remain NetflixPro's. Where public screenshots omit settings or playback screens, the same design system is applied to the existing controls using known interface conventions. The user's later instruction permits completing those screens without waiting for further reference-site access.

Home, search, details, downloads, personal library, settings, profiles, authentication, membership/payment UI, notifications, pairing, Smart Downloads, game screens, dialogs, loading and playback controls inherit the shared composition-local design. Platform Home/header/dock/hero compositions change structurally; cards, forms, dialogs and secondary controls use shared surface, shape, type and contrast tokens. The existing glass preference remains stored separately and controls applicable panel treatments.

The Home carousel has up to five real titles from the currently filtered category. Its dots select actual pages; play, list and details callbacks use that page's typed media identity. Only the visible hero requests additional title artwork. Phone, unfolded and landscape layouts use actual window bounds. Normal Home retains its existing layout.

## Performance and validation

A single SharedPreferences listener at the root updates the theme in place. There is no activity restart, second player, screen capture, runtime blur shader or idle carousel animation. The video surface remains black; themed controls invoke the existing playback handlers. Appearance changes do not reset remembered navigation, forms, downloads or playback state.

Validation includes disabled-by-default behavior, persistence/reconstruction, service-only changes, restoring the original appearance, invalid saved values, text contrast, native Settings interactions, preserving mounted UI state, all service Home layouts and actions, visible-page playback, wide-screen carousel use, and secondary-screen/player-control callbacks. Native captures use local fixture art and do not prove physical-device decoded playback or measured frame rates.

Downloaded official reference images and the source manifest are kept as local review material under `/workspace/.netflix-tv-tools/review/platforms-20261021/references`, rather than shipping promotional assets in the APK.

Prime details place the real title logo over the artwork, Play beside Download, compact watchlist/rating/trailer actions, truthful TMDB ratings and release/season facts, a season selector, and Episodes/Details/Explore/Related tabs. Downloads group saved episodes by their actual season, show verified saved counts/size (and runtime only when every duration is known), and retain real edit/delete, playback, queue, and Smart Downloads management. The reference's promotional backgrounds and sample statistics are not included.

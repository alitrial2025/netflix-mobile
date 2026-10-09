# Mobile screen and Home performance audit — 2026-10-08

## Scope

Reviewed navigation and state ownership, Home catalog startup and composition,
Search, New & Hot, Clips, My Netflix, Details and episode selection, Downloads and
Smart Downloads, authentication, profile selection/editing/onboarding/avatars,
settings, notifications, casting/TV pairing, subscription navigation, the video
player/post-play flow, and the update gate. Android lint scans the application
sources and the regression suite covers these supporting policies and flows.
This is a source audit and automated Android validation, not a claim that every
screen is bug-free or that live services and physical-device performance were tested.

## Changes

| Area | Verified issue | Fix |
| --- | --- | --- |
| Home startup | All 18 TMDB requests had to finish before fresh titles appeared. | Publish the four primary browsing feeds before the 14 secondary requests. Preserve cached metadata during that stage and keep at most four requests active. |
| Catalog ranking | A trending duplicate could discard the same title's Top 10 rank; combining movie/series rankings could render 20 cards in a Top 10 row. | Merge ranking and trending flags when endpoint results overlap and limit each Top 10 row to ten cards. |
| Hero and rows | Hero filtering ran on Main separately from row construction; empty genres fell back to unrelated titles. | Build hero and rows together on Default, use a shared type/genre/profile filter, and show an actionable empty state. Continue Watching follows that filter too. |
| Upcoming titles | An upcoming-only catalog could create a playable hero or hide its reminder rows. | Keep upcoming titles in the rows without assigning a playable hero; retain header spacing. |
| Home My List | The stable lookup callback could leave the hero action stale after a saved-list update. | Pass an observed, remembered set of watchlist IDs to Home. |
| Logo refresh | A slow logo pass could overwrite the catalog with an old list; requests were sequential and cancellation was swallowed. | Cancel superseded passes, fetch with three bounded slots, merge logos into the current catalog, and serialize cache writes. |
| Tab navigation | Disposing a destination lost browse positions and local filter selections. | Save each tab's state with account/profile ownership; clear it when ownership changes. New & Hot and My List selections use saveable state. |
| Clips content | A remembered process-wide catalog stayed stale and bypassed profile-scoped inputs. | Derive every mode from observable catalog/release inputs and filter by the active profile. Deduplicate by media type and ID. |
| Clips playback | The previous clip could keep playing during resolution, in the background, or behind another screen. A URL-only cache lost headers/type and expired independently of the resolver. | Stop the previous stream, cancel obsolete callbacks, observe the activity lifecycle and screen visibility, and use the resolver's bounded cache and complete stream metadata. Select HLS/DASH/MP4 through Media3's source factory. |
| My Netflix | Empty Likes/My List displayed unrelated catalog suggestions; saved Likes did not react to catalog arrival. | Show actual personal lists and an empty-list message; join Likes reactively with catalog metadata and apply profile restrictions to saved lists and Continue Watching. |
| UI identities | Movies and series with the same numeric ID could collide in row/pager keys and detail preview state. | Include media type in those UI keys and preview state/effect keys. |
| Empty feeds | Games, Clips, and New & Hot could present a blank screen or use an unfiltered fallback. | Show empty states and keep profile-filtered inputs. Home renders its empty offline state immediately without scheduling empty-catalog processing. |
| Progress | My Netflix's progress bar forced at least 15% and divided by a possibly unknown duration. | Display the actual bounded fraction, using zero when duration is unknown. |
| Player ownership | Changing profiles retained active playback; switching to a trailer left next-episode work and stream heartbeat active. A late heartbeat callback could close newer playback. | Close playback and Details before profile changes, save progress under the captured profile, clean up content playback when launching a trailer, and reject superseded trailer/heartbeat results. Release stream heartbeats on errors and local playback. Locked Play requests keep the existing stream intact. |
| Player format | The player guessed HLS from arbitrary URL substrings, lost explicit trailer format, and did not distinguish movie/series playback callbacks with the same ID. | Keep trailer format metadata, infer formats from paths without signed query parameters, and include media type in player identity and effects. |
| Details loading | Title lookups could remain pending beyond a useful wait; invalid IDs silently finished without a retry explanation. | Bound title metadata requests to 12 seconds and expose the existing retry UI on failures. Trailer resolution is bounded to 35 seconds. |
| Related titles | Excluding the current numeric ID also excluded an unrelated movie/series sharing that ID. | Compare media type and ID for Details and post-play exclusions. |
| Downloads navigation | System Back left edit mode by closing the screen or leaving an episode group; pending selections/dialogs survived a profile change. | Leave editing first, then the group; reset edit, dialog, setup, and group state with profile ownership, and hide stale rows while the new profile loads. |
| Download title metadata | A missing episode label could identify a saved episode as a movie; numeric catalog lookups could return the movie for a series download. | Infer type from the episode download key and select matching typed catalog metadata. Keep saved episode coordinates when metadata is absent. |
| Download cleanup | Clearing one profile's downloaded title could cancel another profile's queued transfer for the same shared key and remove its files. | Cancel and clear transfer state only for its owning profile; retain files while any durable transfer request still needs them. |
| Subscription | System Back could navigate the underlying tab instead of closing the subscription screen. | Register Back handling on the subscription screen. |

## Regression coverage

New checks cover publication before blocked genre requests, independent network
failures, cancellation, bounded request concurrency, retained Top 10 flags,
empty/type/profile Home filters, upcoming-only rows, all Clips modes, duplicate
movie/series IDs, actual empty personal lists, subscription Back navigation, and
Home scroll restoration across tab disposal and saved-state recreation. Changing
profile ownership resets the restored scroll state. Additional tests exercise
Downloads edit/group Back handling, delete cancellation and exact selected keys,
profile changes with a pending delete, missing episode labels and colliding title
IDs, Details retry and same-ID type changes, signed/explicit stream formats,
player identities, and preservation of another profile's WorkManager transfer,
durable request, and file during download cleanup.

The existing Home checks also cover draw-phase gradient isolation, image request
sizing/cache reuse, and horizontal row restoration after lazy disposal. Existing
tests exercise Search, profiles, settings, playback policies, downloads and local
HLS/caption handling, subscriptions, and update verification.

## Validation

Run with the prepared Android toolchain:

```sh
source /workspace/cloud-setup/env.sh
gradle --no-daemon --max-workers=2 --continue :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Final result: **BUILD SUCCESSFUL** (4m 25s).

- `assembleDebug`: passed; debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest`: 230 tests discovered, **220 passed**, zero failures/errors,
  and 10 optional film-export tests skipped. These require `NETFLIXPRO_CAPTURE=1`;
  the normal screenshot and screen regression tests ran successfully.
- Added 26 regression tests compared with the initial 204-test suite.
- `lintDebug`: passed with **zero errors**, 166 nonfatal warnings and one hint.
- `git diff --check`: passed.
- Final command log: `/workspace/cloud-setup/mobile-deep-scan-final-verified.log`.
- Lint report: `app/build/reports/lint-results-debug.html`.
- Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.

A full run caught a timing failure in the offline Home test after moving catalog
work off Main. Empty catalogs now render their completed empty state immediately;
the final full run passed that test and all other executed checks. Downloads
fixtures now carry the correct profile IDs, and the profile-switch regression
checks that stale rows are hidden while the new profile loads.

## Remaining limits

- Hardware frame times, authenticated playback, billing, and device-specific
  permission/install behavior were not exercised in this pass.
- Legacy Room and some catalog lookups still identify media by numeric ID alone.
  UI collision fixes do not replace a versioned data migration for movie/series
  identities in saved lists, history, and downloads.
- Some TMDB metadata still infers a maturity label from genres. Applying the
  existing profile filter consistently does not verify those labels against
  regional certificates; certificate enrichment remains separate work.
- Casting/pairing and some discovery presentation still depend on existing service
  behavior and placeholders. This pass does not implement an external cast service.
- Dependency, resource, API-style, and other nonfatal compiler/lint warnings remain.

No release deployment or production account changes are part of this audit.

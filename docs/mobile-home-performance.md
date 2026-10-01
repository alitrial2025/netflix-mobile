# Mobile Home scrolling and automatic download follow-through

## Home changes

The vertical list keeps Android's existing fling behavior. No scroll speed or bottom
navigation design changed. The audit found the following avoidable work:

- The scrolling gradient was drawn on Home's parent, together with its foreground
  subtree. A sibling graphics layer now owns the gradient and reads scroll state in
  the draw phase. Measured lazy item heights preserve the gradient after the hero
  leaves composition; the old first-index switch jumped straight to black.
- Hero parallax continued observing pixel offsets after its actual 12 px visual
  limit. Its derived state now stops changing at that limit.
- Continue Watching constructed new image requests and crossfades on composition.
  Hero, logo and row posters now use remembered, display-sized requests without
  crossfade. Inexact cache precision permits a sufficiently large cached poster
  to serve smaller cards. Bitmap decoding concurrency is bounded to two.
- Clay borders created shape outlines and stroke objects in every draw. These now
  live in the draw cache with their gradient brushes.
- Startup TMDB result mapping, restricted-profile catalog filtering, Watchlist
  joins and Continue Watching joins ran in the UI coroutine scope. They now run
  on `Dispatchers.Default`. Palette extraction was already off Main and remains so.

`HomeScrollTest` covers gradient continuity across lazy hero disposal, twenty
pixel-state updates without foreground recomposition/re-measurement, stable
physical-pixel image sizing/cache precision, and horizontal row-position restoration
when vertical scrolling disposes and recreates the row. Native Compose screenshot
checks also render the production Home. These are behavior/rendering checks, not
measured phone frame rates. Hardware frame-time improvement remains unverified;
software-only emulation without KVM cannot establish a realistic phone frame budget.

## Provider-aware Smart Downloads

Automatic suggestions now resolve the requested movie or episode through the
provider's supported OTT search routes before queueing. TMDB popularity or the
`isOriginal` flag alone does not establish provider availability. At most six
ranked candidates are checked per pass, unavailable results are cached for fifteen
minutes, and rate limits stop the pass. The exact first episode of an unknown
series is verified rather than assumed available. Next Episode keeps the watched
copy when its replacement is unavailable. An explicit Play tap cancels the
background suggestion pass, and an account change prevents a late enqueue.

Fresh automatic downloads reuse their preflight stream cache. Interrupted transfers
request fresh links. Cookie-free CDN rejection refreshes the links while preserving
the valid provider handshake; genuine provider-cookie rejection renews that session.
Provider search details stay in diagnostics. Unavailable titles show:

- “This title cannot be downloaded. Try again later.”
- “This title cannot be played. Try again later.”

Actionable storage, interrupted-transfer and playback cooldown messages remain.

## Separate audio offline

The selected variant's declared audio group is retained in a local HLS bundle.
Video and default audio resources use independent synced, checksum-verified
checkpoints. Local playlists preserve initialization segments, fragment timing and
discontinuities. Every resource completes before the local master is published;
no expiring URLs or cookies enter the completed playlists. Stored download sizes
include both tracks. Cancel/delete removes the complete bundle.

Tests use locally generated H.264/fMP4 and AAC/ADTS fixtures. One audio segment
rejects the old authorization with 403; renewal completes without downloading
verified video or earlier audio again. The resulting bundle was probed and fully
decoded with FFmpeg using `file,crypto,data` protocols only, confirming both audio
and video without network access. This does not establish live-provider playback or
Android decoder behavior on every device. Encrypted and byte-range offline HLS
remain unsupported and fail without creating a falsely completed download.

Reproduce the local decoder check after `OfflineHlsTest`:

```sh
ffprobe -v error -protocol_whitelist file,crypto,data -show_entries stream=codec_type,codec_name -of json app/build/reports/offline-hls-playback/movie.m3u8
ffmpeg -hide_banner -v error -protocol_whitelist file,crypto,data -i app/build/reports/offline-hls-playback/movie.m3u8 -map 0:v:0 -map 0:a:0 -f null -
```

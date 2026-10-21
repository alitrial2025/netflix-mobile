# Automatic picture-in-picture

Release 1.31 enters the system PiP window when Home is pressed during full-screen playback on supported Android 8+ devices. Android 12+ uses gesture-aware automatic entry; Android 8–11 uses onUserLeaveHint. Android 7 and devices with PiP disabled retain normal playback behavior.

Only a resolved, permitted main video that is ready/buffering and playing enables automatic entry. Home previews and trailers cannot enable it. The same ExoPlayer and surface stay attached. Playback follows STARTED/STOPPED visibility rather than pausing at ON_PAUSE, since a PiP activity remains visible while paused. Full-screen controls and modal dialogs are hidden in PiP; the system Play/Pause action updates the existing player state once. Episode countdowns continue without drawing full-screen cards in the small window. Closing PiP pauses, saves progress and closes playback.

The activity owns the non-exported action receiver and tears it down on destruction. No new app button or casting integration is included. Cast remains deferred at the owner's request.

Validation: Robolectric API 28/34 covers inactive/paused/playing entry, gesture auto-entry, action delivery, dismiss versus background, and clearing eligibility. The full Android CI also runs unit tests, lint, release shrinking and original-key release signing. Physical PiP animation/device policy behavior still needs a phone check; no device is attached in the cloud workspace.

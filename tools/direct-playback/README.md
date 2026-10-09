# Direct playback check

This checker validates an explicitly supplied, owned or approved HLS URL without
Net52 discovery or fallback. It fetches the issued master, one video sample, and
the declared default audio sample when present. URL signatures remain unchanged.
It uses at most eight requests, a 20-second budget, 256 KiB per manifest, and
64 KiB per media sample. Other origins must be approved explicitly.

It stops on 401, 403, 429, rate-limit waiting manifests, malformed media, or an
unapproved redirect/segment origin. It does not generate tokens, inspect storage
buckets, discover origins, replay provider sessions, or solve Cloudflare challenges.
Encrypted playlists require validation in an authorized player; this tool does
not fetch keys or licenses. Container samples do not prove successful decoding,
title/episode identity, or playback for the full duration.

Run local fixture checks with Node 22+:

```sh
node --test tools/direct-playback/verify-issued-stream.test.mjs
```

To test an approved live URL, set `DIRECT_PLAYBACK_URL` privately in the
environment, then run:

```sh
node tools/direct-playback/verify-issued-stream.mjs \
  --allow-origin https://approved-segment-host.example \
  --report /tmp/direct-playback-report.json
```

Do not place signed URLs in source control or shell command arguments. Reports
contain hostnames, query names, statuses, and sample sizes, excluding URL paths,
query values, response bodies, and raw errors. No credentials are sent.

The current mobile pipeline still needs Net52 to resolve title/episode identities
into an issued playback master (`PublicPlaybackResolver.resolveOne`). Its final
result intentionally keeps the master so Media3 retains adaptive video and
alternate audio. Replacing that master with one video variant can lose audio
and quality switching. Removing Net52 completely requires an approved direct
master URL source and a catalog mapping for each title/episode, including expiry,
required request context, captions, download permissions, and DRM where applicable.

No live CDN/storage access has been tested for this change. The ownership/API
question must be resolved before live integration.

Validation on 2026-10-08: all 25 Node tests passed across this checker and the
existing CDN/public-catalog suites (10 new direct-stream tests, 15 existing tests).
All network behavior in these tests uses mocks or local fixture servers. The
issued-master fixture verifies video and default audio with zero provider calls;
access denials and rate limits stop immediately. `git diff --check` also passed.
The mobile app's production resolver has not been changed by this investigation.

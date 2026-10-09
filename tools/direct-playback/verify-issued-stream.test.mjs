import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyIssuedStream } from './verify-issued-stream.mjs';

const ts = () => { const bytes = Buffer.alloc(188 * 4); for (let i = 0; i < bytes.length; i += 188) bytes[i] = 0x47; return bytes; };
function fixture(routes) {
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push({ url, options });
    assert.equal(options.redirect, 'manual');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.headers.Cookie, undefined);
    assert.equal(options.headers.Authorization, undefined);
    const route = routes.get(url);
    if (!route) throw new Error(`unexpected private URL: ${url}`);
    return new Response(route.body, { status: route.status ?? 200, headers: route.headers });
  };
  return { fetchImpl, calls };
}

test('issued CDN master validates video and default audio samples with zero provider requests or signature changes', async () => {
  const master = 'https://cdn.example/private/SECRET/master.m3u8?sig=MASTER_SECRET%2Fpart';
  const video = 'https://cdn.example/video.m3u8?sig=VIDEO_SECRET';
  const audio = 'https://cdn.example/audio.m3u8?sig=AUDIO_SECRET';
  const f = fixture(new Map([
    [master, { body: '#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="sound",DEFAULT=NO,URI="/wrong.m3u8"\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="sound",DEFAULT=YES,URI="/audio.m3u8?sig=AUDIO_SECRET"\n#EXT-X-STREAM-INF:BANDWIDTH=100,AUDIO="sound"\n/video.m3u8?sig=VIDEO_SECRET' }],
    [video, { body: '#EXTM3U\n#EXTINF:6,\n/video.ts?sig=SEGMENT_SECRET' }],
    [audio, { body: '#EXTM3U\n#EXTINF:6,\n/audio.aac' }],
    ['https://cdn.example/video.ts?sig=SEGMENT_SECRET', { body: ts(), status: 206 }],
    ['https://cdn.example/audio.aac', { body: Buffer.from([0xff, 0xf1, 0x50, 0x80]), status: 206 }]
  ]));
  const result = await verifyIssuedStream(master, f);
  assert.equal(result.success, true);
  assert.deepEqual(result.samples.map(sample => sample.kind), ['mpeg_ts', 'aac']);
  assert.deepEqual(f.calls.map(call => call.url), [master, video, 'https://cdn.example/video.ts?sig=SEGMENT_SECRET', audio, 'https://cdn.example/audio.aac']);
  assert.ok(f.calls.every(call => new URL(call.url).hostname === 'cdn.example'));
  assert.equal(f.calls[2].options.headers.Range, 'bytes=0-65535');
  assert.equal(JSON.stringify(result).includes('SECRET'), false);
});

for (const status of [401, 403, 429]) test(`HTTP ${status} stops immediately without provider fallback or retry`, async () => {
  const url = 'https://cdn.example/master.m3u8';
  const f = fixture(new Map([[url, { status, body: 'Rejected', headers: { 'Retry-After': '120' } }]]));
  const result = await verifyIssuedStream(url, f);
  assert.equal(result.success, false);
  assert.equal(result.error, status === 429 ? 'rate_limited' : 'authorization_required');
  if (status === 429) assert.equal(result.retryAfterMs, 120_000);
  assert.equal(f.calls.length, 1);
});

test('cross-origin redirects and segment hosts require explicit approval', async () => {
  const url = 'https://cdn.example/master.m3u8';
  const redirected = fixture(new Map([[url, { status: 302, headers: { Location: 'https://storage.example/video.m3u8' } }]]));
  assert.equal((await verifyIssuedStream(url, redirected)).error, 'origin_not_approved');
  assert.equal(redirected.calls.length, 1);
  const f = fixture(new Map([
    [url, { body: '#EXTM3U\n#EXTINF:6,\nhttps://storage.example/video.ts' }],
    ['https://storage.example/video.ts', { body: ts() }]
  ]));
  assert.equal((await verifyIssuedStream(url, f)).error, 'origin_not_approved');
  const allowed = await verifyIssuedStream(url, { ...f, allowedOrigins: ['https://storage.example'] });
  assert.equal(allowed.success, true);
});

test('successful HTTP status is insufficient when manifest or segment contains HTML', async () => {
  const url = 'https://cdn.example/master.m3u8';
  const html = fixture(new Map([[url, { body: '<html>login</html>' }]]));
  assert.equal((await verifyIssuedStream(url, html)).error, 'invalid_hls');
  const segment = fixture(new Map([
    [url, { body: '#EXTM3U\n#EXTINF:6,\n/video.ts' }],
    ['https://cdn.example/video.ts', { body: '<html>login</html>' }]
  ]));
  assert.equal((await verifyIssuedStream(url, segment)).error, 'invalid_media_sample');
});

test('rate-limit waiting manifests stop without requesting the waiting video', async () => {
  const url = 'https://cdn.example/master.m3u8';
  const f = fixture(new Map([[url, { body: '#EXTM3U\n#EXTINF:6,\n/files/220884/video.ts' }]]));
  assert.equal((await verifyIssuedStream(url, f)).error, 'rate_limited');
  assert.equal(f.calls.length, 1);
});

test('encrypted playlists report a player requirement without fetching keys or segments', async () => {
  const url = 'https://cdn.example/master.m3u8';
  const f = fixture(new Map([[url, { body: '#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="/private-key"\n#EXTINF:6,\n/video.ts' }]]));
  assert.equal((await verifyIssuedStream(url, f)).error, 'encrypted_media_requires_player');
  assert.equal(f.calls.length, 1);
});

test('redirect loops obey the shared eight-request budget', async () => {
  const url = 'https://cdn.example/master.m3u8';
  const f = fixture(new Map([[url, { status: 302, headers: { Location: url } }]]));
  assert.equal((await verifyIssuedStream(url, f)).error, 'request_budget_exceeded');
  assert.equal(f.calls.length, 8);
});

test('manifest size and media sampling are bounded even when a server ignores Range', async () => {
  const url = 'https://cdn.example/master.m3u8';
  const huge = fixture(new Map([[url, { body: '#EXTM3U\n' + 'x'.repeat(262_145) }]]));
  assert.equal((await verifyIssuedStream(url, huge)).error, 'manifest_too_large');
  const f = fixture(new Map([
    [url, { body: '#EXTM3U\n#EXTINF:6,\n/video.ts' }],
    ['https://cdn.example/video.ts', { body: Buffer.concat(Array.from({ length: 200 }, ts)) }]
  ]));
  const result = await verifyIssuedStream(url, f);
  assert.equal(result.success, true);
  assert.equal(result.samples[0].bytes, 65_536);
});

#!/usr/bin/env node
// Verify an explicitly supplied, authorized HLS URL. No discovery, signing, or provider fallback.
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

class CheckError extends Error {
  constructor(code, retryAfterMs) { super(code); this.code = code; this.retryAfterMs = retryAfterMs; }
}
const fail = code => { throw new CheckError(code); };
const attributes = line => Object.fromEntries([...line.matchAll(/([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))/g)]
  .map(([, key, quoted, plain]) => [key, quoted ?? plain]));

function sampleKind(bytes) {
  for (let offset = 0; offset < Math.min(bytes.length, 188); offset++) {
    if (offset + 376 < bytes.length && [offset, offset + 188, offset + 376].every(i => bytes[i] === 0x47)) return 'mpeg_ts';
  }
  if (bytes.length >= 12 && ['ftyp', 'styp', 'moof', 'sidx'].includes(bytes.toString('ascii', 4, 8))) return 'iso_bmff';
  if (bytes.length >= 3 && (bytes.toString('ascii', 0, 3) === 'ID3' || bytes[0] === 0xff && (bytes[1] & 0xf0) === 0xf0)) return 'aac';
  return 'unrecognized';
}

function retryDelay(header) {
  if (!header) return undefined;
  if (/^\d+$/.test(header.trim())) return Math.min(Number(header) * 1000, 86_400_000);
  const date = Date.parse(header);
  return Number.isFinite(date) ? Math.min(Math.max(date - Date.now(), 0), 86_400_000) : undefined;
}

/** Only the supplied origin and explicitly approved origins can receive requests. */
export async function verifyIssuedStream(source, { allowedOrigins = [], fetchImpl = globalThis.fetch, timeoutMs = 20_000 } = {}) {
  const events = [];
  const samples = [];
  const deadline = Date.now() + timeoutMs;
  const approved = new Set();
  let requestCount = 0;
  const checkedUrl = (raw, parent) => {
    let url; try { url = new URL(raw, parent); } catch { fail('invalid_url'); }
    if (url.protocol !== 'https:' || url.username || url.password) fail('https_url_required');
    if (!approved.has(url.origin)) fail('origin_not_approved');
    return url;
  };

  async function readBounded(response, binary) {
    const limit = binary ? 65_536 : 262_144;
    const chunks = [];
    let size = 0;
    const reader = response.body?.getReader();
    if (!reader) fail('empty_response');
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const remaining = limit - size;
        if (value.length > remaining && !binary) fail('manifest_too_large');
        chunks.push(Buffer.from(value.subarray(0, remaining)));
        size += Math.min(value.length, remaining);
        if (size >= limit) break;
      }
    } finally { await reader.cancel().catch(() => {}); }
    return Buffer.concat(chunks);
  }

  async function request(raw, stage, binary = false) {
    let url = checkedUrl(raw);
    // Redirects share the same bounded request budget and origin checks.
    while (true) {
      if (++requestCount > 8) fail('request_budget_exceeded');
      const remaining = deadline - Date.now();
      if (remaining <= 0) fail('time_budget_exceeded');
      const event = { stage, host: url.host, queryKeys: [...new Set(url.searchParams.keys())] };
      events.push(event);
      const response = await fetchImpl(url.href, {
        headers: binary ? { Accept: '*/*', Range: 'bytes=0-65535' } : { Accept: 'application/vnd.apple.mpegurl, application/x-mpegURL' },
        redirect: 'manual', credentials: 'omit', signal: AbortSignal.timeout(Math.min(5000, remaining))
      });
      event.status = response.status;
      if ([301, 302, 303, 307, 308].includes(response.status)) {
        const location = response.headers.get('location');
        await response.body?.cancel();
        if (!location) fail('invalid_redirect');
        url = checkedUrl(location, url);
        continue;
      }
      if (!response.ok) {
        await response.body?.cancel();
        if (response.status === 429) throw new CheckError('rate_limited', retryDelay(response.headers.get('retry-after')));
        if ([401, 403].includes(response.status)) fail('authorization_required');
        fail('http_error');
      }
      const bytes = await readBounded(response, binary);
      event.sampledBytes = bytes.length;
      if (binary) return { bytes, url };
      const body = bytes.toString('utf8');
      if (/\/files\/220884(?:[/?\s]|$)|too many requests|\brate[_ -]?limit(?:ed)?\b/i.test(body)) fail('rate_limited');
      if (!body.trimStart().startsWith('#EXTM3U')) fail('invalid_hls');
      return { body, url };
    }
  }

  async function mediaPlaylist(raw, stage, depth = 0) {
    if (depth >= 4) fail('manifest_depth_exceeded');
    const result = await request(raw, stage);
    const lines = result.body.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    if (lines.some(line => line.startsWith('#EXT-X-KEY:') && attributes(line).METHOD !== 'NONE')) fail('encrypted_media_requires_player');
    const variant = lines.findIndex(line => line.startsWith('#EXT-X-STREAM-INF:'));
    if (variant >= 0) {
      const child = lines[variant + 1];
      if (!child || child.startsWith('#')) fail('missing_variant');
      return await mediaPlaylist(checkedUrl(child, result.url).href, stage, depth + 1);
    }
    const segment = lines.find(line => !line.startsWith('#'));
    if (!segment || !lines.some(line => line.startsWith('#EXTINF:'))) fail('missing_segment');
    return checkedUrl(segment, result.url).href;
  }

  async function sample(raw, stage) {
    const { bytes } = await request(raw, stage, true);
    const kind = sampleKind(bytes);
    if (kind === 'unrecognized') fail('invalid_media_sample');
    samples.push({ stage, kind, bytes: bytes.length });
  }

  try {
    const initial = new URL(source);
    approved.add(initial.origin);
    for (const raw of allowedOrigins) {
      const origin = new URL(raw);
      if (origin.protocol !== 'https:' || origin.username || origin.password || origin.pathname !== '/' || origin.search || origin.hash) fail('invalid_allowed_origin');
      approved.add(origin.origin);
    }
    const master = await request(checkedUrl(source).href, 'master');
    const lines = master.body.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    const variant = lines.findIndex(line => line.startsWith('#EXT-X-STREAM-INF:'));
    if (variant >= 0) {
      const video = lines[variant + 1];
      if (!video || video.startsWith('#')) fail('missing_variant');
      const group = attributes(lines[variant]).AUDIO;
      const audios = lines.filter(line => line.startsWith('#EXT-X-MEDIA:')).map(attributes)
        .filter(row => row.TYPE === 'AUDIO' && row['GROUP-ID'] === group && row.URI);
      const audio = audios.find(row => row.DEFAULT === 'YES') ?? audios[0];
      await sample(await mediaPlaylist(checkedUrl(video, master.url).href, 'video_manifest'), 'video_sample');
      if (audio) await sample(await mediaPlaylist(checkedUrl(audio.URI, master.url).href, 'audio_manifest'), 'audio_sample');
    } else {
      if (lines.some(line => line.startsWith('#EXT-X-KEY:') && attributes(line).METHOD !== 'NONE')) fail('encrypted_media_requires_player');
      const segment = lines.find(line => !line.startsWith('#'));
      if (!segment || !lines.some(line => line.startsWith('#EXTINF:'))) fail('missing_segment');
      await sample(checkedUrl(segment, master.url).href, 'video_sample');
    }
    return { success: true, scope: 'HTTP/HLS/container samples; does not verify player decoding or title identity', events, samples };
  } catch (error) {
    // Never expose exception messages containing signed URLs, response bodies, or credentials.
    return { success: false, error: error instanceof CheckError ? error.code : 'transport_error',
      ...(error.retryAfterMs !== undefined ? { retryAfterMs: error.retryAfterMs } : {}), events, samples };
  }
}

async function main(args = process.argv.slice(2)) {
  if (args.includes('--help')) {
    console.log('DIRECT_PLAYBACK_URL=https://approved.example/issued.m3u8 node tools/direct-playback/verify-issued-stream.mjs [--allow-origin https://approved-segment-host.example] [--report report.json]\nUse only an owned or approved playback URL. No provider discovery, retries, credential generation, or URL rewriting.');
    return;
  }
  const allowedOrigins = [];
  let reportPath;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--allow-origin' && args[i + 1]) allowedOrigins.push(args[++i]);
    else if (args[i] === '--report' && args[i + 1]) reportPath = args[++i];
    else throw new Error('invalid_arguments');
  }
  if (!process.env.DIRECT_PLAYBACK_URL) throw new Error('direct_playback_url_required');
  const result = await verifyIssuedStream(process.env.DIRECT_PLAYBACK_URL, { allowedOrigins });
  const report = JSON.stringify(result, null, 2) + '\n';
  if (reportPath) await writeFile(reportPath, report, { mode: 0o600 });
  process.stdout.write(report);
  if (!result.success) process.exitCode = 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { console.error('Check arguments and DIRECT_PLAYBACK_URL.'); process.exitCode = 1; });
}

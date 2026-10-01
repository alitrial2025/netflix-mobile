# Offline HLS test media

Generated locally with FFmpeg from `testsrc2=size=160x90:rate=12` and a 440 Hz
`sine=sample_rate=48000`, duration 3 seconds. No provider media or credentials.
Video is H.264 in fragmented MP4 with a separate initialization segment; audio
is AAC/ADTS. These fixtures exercise independent audio and video checkpoints,
token renewal, local manifest rewriting and offline A/V decoding.

Generation:

```sh
ffmpeg -f lavfi -i testsrc2=size=160x90:rate=12 -t 3 -an -c:v libx264 -preset ultrafast -g 12 -pix_fmt yuv420p -f hls -hls_time 1 -hls_playlist_type vod -hls_segment_type fmp4 -hls_fmp4_init_filename init.mp4 -hls_segment_filename video/%d.m4s video/index.m3u8
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=48000 -t 3 -vn -c:a aac -b:a 48k -f segment -segment_time 1 -segment_format adts audio/%d.aac
```

# Bundled sound provenance

The five probe tones are original mathematical synthesis in `Tones` in `apps/android/app/src/main/java/com/echorelle/android/Timeline.kt`; no third-party recordings, samples, or sound fonts are used. They are finite 48,000 Hz mono float PCM buffers generated offline on first access. Attack and tail envelopes are part of the counted duration. No source clips are downloaded. No open-source license has yet been selected for this repository.

| Sound | Frames | Actual decoded duration |
| --- | ---: | ---: |
| Soft Ding | 20,160 | 0.420 s |
| Warm Bell | 31,200 | 0.650 s |
| Bright Glass Ping | 18,240 | 0.380 s |
| Gentle String Pluck | 23,040 | 0.480 s |
| Loud Alarm Buzz | 34,560 | 0.720 s |

The alarm uses additional odd harmonics and a stronger sustained envelope; it remains within normal media volume and source sample bounds. Final listening/loudness review on the phone is pending. Buffer duration eligibility uses the integer frame count; labels never govern validation. The whole palette is shorter than one second, including its finite decay. These tones are suitable for the feasibility probe but require listening review before release. Exact-loop tests use a separate synthetic loop-safe sequence; decaying bells are not assumed to have a perceptually smooth seam.

## Full-app reuse and import fixtures — 0.2.0

The original five tones are reused unchanged and their earlier clean-phone listening result remains evidence. There is no new subjective loudness review in this session. The full app shows names, descriptions and exact counted durations, offers explicit one-shot and three-repeat seam previews, and never autoplays selection. The optional 5ms edge fade changes amplitude only, preserving sample count.

`apps/android/app/src/androidTest/assets/gapless.mp3` and `gapless.m4a` are original test-only sine fixtures, not product sounds or third-party recordings. Generated locally with FFmpeg from a 440Hz sine, 48kHz mono, 1.2 seconds (57,600 content frames), and 20ms attack/tail fades:

```text
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=48000:duration=1.2 -af afade=t=in:d=0.02,afade=t=out:st=1.18:d=0.02 -c:a libmp3lame -q:a 4 gapless.mp3
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=48000:duration=1.2 -af afade=t=in:d=0.02,afade=t=out:st=1.18:d=0.02 -c:a aac -b:a 96k gapless.m4a
```

The WAV resampling fixture is generated in the Android test itself, including its intentional silent edges. The MP3/AAC tests assert exact retained frames after delay/padding handling on both Samsung Android 13 and emulator Android 16. Tests do not establish perceptual seam quality for arbitrary imports, nor does FFmpeg's presence select a project license.

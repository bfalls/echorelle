# Android background feasibility

## Sequential origami lifecycle check — 0.3.0

The new single-sheet visual passed screen-off work-counter checks and returned directly to the current observed fold after 500 simulated missed intervals, without rebuilding/replaying the sequence. Immutable models are cached; per-card material textures are independent. Final unit/build/lint checks pass (29 JVM tests; zero lint errors/15 warnings). Native geometry/render/lifecycle checks and actual limitations are recorded in `sequential-origami.md` and the latest build handoff. No new audio, physical-phone, energy/Doze, full 1,265-second playback or hours-long battery measurement was performed. The 0.2.9 silent-stream wake-lock limitation still applies.


## Screen-off CPU observations — 0.2.9, 2026-10-25

Latest user decision: retain precise intervals and measure/reduce power; do not add special alarm permission prompts. The proposed wake–play–sleep approach is not implemented. The service still streams sample-counted PCM, including silence, while holding a partial wake lock. This is not a CPU countdown timer, but it still keeps the application processor active. Entirely silent mixer blocks now advance exact voice cursors in one block and fill zero PCM, avoiding per-sample voice mixing. Sound/boundary/fade blocks retain the existing sample path. This optimization does not permit deep sleep during playing gaps or establish near-zero power.

Two short debug-app observations on the same API 36.1 emulator used a 1,265-second, zero-gain job after its first sound window. Screen was non-interactive for both 20.001-second measurement windows; then the job was paused and measured for another 20.001 seconds. Process CPU includes the service, diagnostics and test instrumentation. These were fixed-order single samples with differing warm-up/JIT history, not a controlled physical energy experiment.

| App | Silent-playing process CPU | Paused process CPU | Consumed frames while playing / paused |
| --- | ---: | ---: | ---: |
| 0.2.8 baseline | 1,305 ms | 52 ms | 965,056 / 0 |
| 0.2.9 silent-block optimization | 1,138 ms | 50 ms | 965,056 / 0 |

CPU time was lower in the second sample; no battery percentage, watt-hours, phone lifetime, deep-Doze reliability, external audible onset or statistically established reduction is inferred. Native audio output and the wake lock remain the major unresolved energy work. Local self-reports: ignored `device-results/power-review/before.json`, `after.json`. The new `SilentGapPowerTest` restores only its own fixture and refuses to disturb existing active playback. Its two 20-second windows run only when explicitly selected, not as a new multi-hour gate.

Visual work is separately gated by activity resume, device interactive state and the list viewport. The emulator lifecycle test counted zero cube draws/preparations while screen-off and prepared only the current interval after 500 simulated missed cycles. A worst-length 24-hour route built in 225.38 ms off the UI thread; 100 checkpoint seeks had p95 0.306 ms/max 0.506 ms, with no more than 127 turns applied. Cached recovery does not replay missed intervals; a new uncached route still has a preparation cost. These are emulator timing observations, not real-phone responsiveness or battery results.

Android system-timer limits, checked 2026-10-25: [inexact alarms](https://developer.android.com/develop/background-work/services/alarms) may be delayed substantially, including up to an hour on recent Android absent further battery restrictions; exact wake alarms generally require alarm access, and idle wakeups have rate limits. [`Handler` delays](https://developer.android.com/reference/android/os/Handler) are delayed by deep sleep. These are not interchangeable with the current precise short-interval timeline. No alarm API or permission was added. If a different scheduling mode is considered later, define its accuracy and background contract first rather than silently degrading interval timing.


## Visual-only validation — 0.2.6, 2026-10-25

Cube/visual refinements passed 23 JVM tests, four final emulator render tests (68.890s), debug assembly and lint (zero errors/15 warnings). Rendering samples include 5/60/1500/7200-second timelines, real-time 12-second excerpts of a 1,500-second route and the final solved-cube display; these are phase-driven captures, not full-duration playback or energy measurements. Earlier in this session 14 combined service/UI/render/migration tests passed before the final cube refinements (111.054s). No new physical-phone or extended audio/energy reliability evidence. App updates preserved current saved jobs and visual selection. See `review.md` for the final APK/hash and precise validation scope.


## Current development decision — 2026-10-25

The user explicitly authorized building the full Android app now and deferring extended soak testing to the real app. The positive short physical-device checks below are sufficient to continue implementation. Do not repeat the probe-first/eight-hour prerequisite, ask the user to monitor another overnight test, or treat the inconclusive attempt as a platform failure. The historical protocol and gate language below document the original approach; this decision supersedes their use as development blockers. Extended reliability, energy cost, externally measured audible timing, and final supported limits remain unverified. Preserve diagnostics in the actual app for later self-reporting validation. The background-playback product requirement and per-commit approval rule remain unchanged.

Status on 2026-10-24: **INITIAL PHYSICAL CHECKS PASSED, INCLUDING FIVE-MINUTE SILENT GAPS; extended feasibility pending.** Foreground sound, short locked-screen/other-app playback, Match mode, four-source mixing, and a reported unplugged locked-phone long-gap run passed user review. Do not advertise an extended reliability envelope or close the planned multi-hour gate from these results. No Android phone was listed by ADB during initial inspection; replacing the USB cable subsequently enabled an authorized physical connection. Emulator success cannot close this gate.

## Environment observed

- Windows workspace; Android Studio installed with bundled OpenJDK 21.0.10.
- Android platforms 35 and 36; build tools 35.0.0, 36.0.0, 36.1.0.
- Emulator profiles: Medium_Phone_API_36.1 and Pixel_2; availability is not a test result.
- Gradle 9.4.1, AGP 9.2.1; compile/target SDK 36, minimum SDK 26 for this prototype. Minimum API is a code choice, not a validated device matrix.
- GitHub CLI authenticates as bfalls outside the sandbox. Public bfalls/echorelle was created empty, with no README/license initialization or commits.
- Initial ADB check outside the sandbox: device list empty. Phone model/OS, output route, battery settings, and hardware support remain unknown.
- Subsequent USB connection after replacing the cable: authorized Samsung SM-G781U1, Android 13 / API 33. Device serial is intentionally omitted from tracked notes. The user reports that all sounds are clean on the phone; emulator distortion is set aside for now. Battery policy and the physical-device background reliability envelope remain unverified.

## Prototype and rationale

A visible, user-started `mediaPlayback` foreground service owns a platform MediaSession and a single 48 kHz float-mono AudioTrack. A Kotlin mixer queues 480-frame blocks with nonblocking writes and bounded backpressure waits, allowing Stop to exit even if the sink stalls. The period is an integer frame count. Its initial frame contains sound; the remainder of each period contains the natural silence specified by the user. The service does not use alarms, UI timers, completion callbacks to schedule repetition, or a separate silent keepalive track. Exact mode wraps directly from the last decoded/synthesized sample to the first. An arbitrary waveform may still have an audible discontinuity at that join.

This feasibility stage uses native platform MediaSession rather than adding a Media3 Player adapter before the mixer has been tested. Reassess Media3 integration after hardware results; this is not a decision to omit final media lifecycle integration. Oboe/C++ is deferred unless measurements justify it.

The wake lock is held only while actively rendering this timeline. It is released while paused and at stream teardown. It may materially affect energy use during long silent gaps; measure this before claiming a supported envelope. A foreground service and sample-correct offline output do not establish audible timing or survival under Doze.

Audio focus uses GAIN with media/music attributes; coexistence with another music player is not promised. Any loss, including duck requests, pauses; this conservative probe requires explicit resume. Headphone disconnection also pauses. Pause retains queued PCM and clip phase; Stop destroys the session and resets phase. These are whole-probe controls; independent job controls remain unimplemented. Pause retains AudioTrack memory and the paused media session, which is not yet the final resource-release contract.

## Evidence and limits

| Check | Result |
| --- | --- |
| Immediate first sample, start-to-start silence | Automated offline test implemented; see build evidence below |
| Exact-loop content across 1,000 joins | Automated offline test implemented |
| Chunk independence, concurrent independent periods | Automated offline tests implemented |
| Duration boundary: exact equality permitted, one frame too long rejected | Automated offline test implemented |
| Five complete synthesized sounds below one second, no clipped source samples | Automated offline test implemented; listening review pending |
| Real-phone locked screen / another app | Short five-second-period check passed user observation; extended runs pending |
| 1s, 5s, 30s, 5min, 30min periods | Five-second short check and five-minute gaps over a 25-minute run passed; remaining matrix and long soaks pending |
| Exact loop on output capture | Short Match-mode listening passed; external join capture NOT RUN |
| Four concurrent sources | Short Samsung mixer/listening check passed; extended cap/lifecycle profiling pending |
| Doze, low battery, calls, Bluetooth, route changes | NOT RUN |
| Eight-hour soak and energy comparison | Overnight attempt inconclusive: latest retained session paused after ~85s; no eight-hour evidence or energy baseline |

Probe input guardrails are 1 second to 24 hours for manual periods; Match derives sub-second periods from the buffer. These are temporary resource/input choices, not measured product limits. Four-source mode uses periods p, p+⅓s, p+⅔s, p+1s (exact mode uses equal periods) and starts them together solely for measurement. The final product starts each job independently when tapped.

CSV files in private app storage are named `probe-<wall-timestamp>.csv`. Rows contain elapsed realtime nanoseconds, rendered frame count, unsigned playback head, underrun count, and per-voice scheduled onset counts. Wall time only names the file; it does not schedule playback. These are **render/queue diagnostics, not audible-onset measurements**. A separate diagnostics executor samples approximately every 100ms for the visible countdown and writes CSV approximately once per second; columns are not an atomic snapshot. CSV I/O is kept off the audio thread. Compare with uninstrumented output before performance conclusions. AudioTrack playback head can wrap during very long runs; analyze deltas modulo 2^32. Timestamp-to-speaker latency and clock drift must be measured externally.

## Probe usability correction after first user test

The user reported that changing sounds did not work and playback appeared to go silent. Inspection found that the previous screen let the form change during playback but Start returned immediately whenever a stream already existed. Consequently, the old sound and cadence continued without acknowledging the edited form. Captured emulator diagnostics showed one run continuing at its original 10-second interval through 17 scheduled onsets with zero sampled underruns; this rules out a stopped render timeline for that run, but does not prove audible output reached the user's speakers. The original status was above a long scrolling list and could disappear while the user reached the controls.

The correction pins state, active sound, countdown, and controls in a footer; disables editing/Start during an existing test; explains that Stop is required before a new sound/timing; and adds explicit one-shot previews independent of interval validity. Each preview drains only its finite source buffer and then releases the session. It stops on leaving the activity. Stream resources now belong to one run, and stale completion/focus callbacks cannot stop another run. The initial notification permission prompt completes before the first user-requested playback starts.

Single-source output previously reserved 12dB of headroom for four sources even when only one existed. The mixer now divides by the actual, fixed source count for that run and retains a hard output ceiling. This improves single-tone audibility without bypassing media volume or clipping. Final loudness/listening review remains pending.

UI regression coverage previews all five sounds with timing blank, selects three different sounds at one-second intervals, verifies continued output across later onsets, and stops/restarts between selections. The service smoke test also checks Pause/Resume. Older Espresso failed before entering the UI test due to a removed reflective InputManager call; the test dependencies were updated following the [official AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).

Regression results on 2026-10-24: all six JVM tests and both Android 16 emulator tests passed. UI actions selected Warm Bell, Bright Glass Ping, and Gentle String Pluck in turn; all three runs reached four scheduled onsets and playback-head positions beyond 144,000 frames at a 48,000-frame period. All CSV samples from those runs reported zero underruns. Each of the five explicit previews completed and returned to stopped state. The fixed footer was asserted visible during the running tests. Lint passed with zero errors and seven version/style warnings. This verifies controls, state, and output consumption; externally captured audibility and physical-phone feasibility are still pending. Updated debug build: 0.1.1-probe.

## Physical-device protocol

1. Connect the phone, enable USB debugging, accept its authorization prompt, and record model, Android build, output route, media volume, battery state/settings, and power connection. Preserve default battery policy for baseline tests.
2. Build/install the debug APK. Keep an external recorder or loopback measurement rig with a known sample clock. Start audible tests deliberately at a comfortable media volume. Selecting a sound never autoplays.
3. Test each synthetic period (1s, 5s, 30s, 5min, 30min) locked and with another app foreground. Record actual onset times, start latency, missing/duplicate onsets, cadence error, underruns, service state, and elapsed duration. Measure at least eight hours for the supported matrix, including the longest silent gap. Target within 50ms on the built-in speaker; do not infer audible timing from enqueue counters.
4. Exact mode: first use a known loop-safe fixture, capture output, compare at least 1,000 joins for dropped/inserted frames and engine gaps. The generated bell sounds are not guaranteed loop-safe musical material. Distinguish source clicks from buffer starvation. Repeat with the planned imported-audio decoder once implemented.
5. Test four independent periods and record CPU, peak amplitude, timing error, underruns, and battery. No supported concurrency cap until these measurements pass.
6. Pause mid-sound and mid-silence, resume, stop/start, navigate away/back, and change wall-clock time. Verify preserved phase, silence while paused, new cadence after Stop, and no restart from UI reattachment.
7. Check incoming calls, another media app taking focus, headphone removal, Bluetooth connect/disconnect, low-power mode, natural idle/Doze, and OS task/process termination. Force-stop and reboot must not restart playback. A forced-Doze diagnostic is useful but does not replace natural idle.
8. Compare disconnected-from-power idle, ordinary continuous audio, and intermittent playback over equal durations. Record battery percentage/energy, CPU/wake time, and thermal behavior; choose an energy budget from the results.
9. Capture CSV with `adb exec-out run-as com.echorelle.android cat files/<file>.csv` and platform `dumpsys` state into ignored `device-results/`. Keep device identifiers and private recordings out of Git.

When available, collect `adb shell dumpsys activity services com.echorelle.android`, `adb shell dumpsys media_session`, `adb shell dumpsys power`, and `adb logcat -d -s EchorelleProbe` alongside external recording. USB power changes idle behavior; use a disconnected soak after setup and collect afterward.

If intermittent playback fails, first identify whether the failure is focus policy, process/service termination, scheduling underruns, or energy cost. Present the actual device/build, test duration, onset evidence, and failure mechanism. Alternatives to evaluate explicitly are a narrower measured device/interval envelope, notification/alarm-based cues with different cadence and audio limitations, or foreground-only operation. None is an authorized silent substitution for the stated requirement. UI expansion waits for the findings and any necessary product decision.

## Official references refreshed 2026-10-24

- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types): mediaPlayback declaration/permission and user-visible playback lifecycle.
- [Media3 background playback](https://developer.android.com/media/media3/session/background-playback): service/session ownership; paused/stopped/failed players can leave foreground state after ten minutes. Natural silent intervals must not be represented as user pause.
- [AudioTrack API](https://developer.android.com/reference/android/media/AudioTrack): streaming PCM, blocking writes, playback head, and underrun diagnostics.
- [Audio focus](https://developer.android.com/media/optimize/audio-focus): foreground/top-app requirements for modern targets and focus-loss behavior.

## Build and emulator evidence

On 2026-10-24, `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest` succeeded using the installed Studio JDK and SDK. All six JVM tests passed (no skips/failures). The Android 16 / API 36.1 headless Medium Phone emulator passed its one service lifecycle instrumentation test. Its diagnostic playback head advanced, stayed at 212,160 frames across successive paused samples, and advanced after resume. All sampled underrun counters were zero in this roughly nine-second stream. Emulator audio output was disabled, so this is exclusively a runtime/control smoke test, not a listening test or audible timing measurement.

The final build reruns unit tests, lint, and APK assembly after moving diagnostics to fixed-delay scheduling and specifying older-Android backup exclusion. Remaining lint notices concern newer available SDK/tool/dependency versions; the probe deliberately uses the installed SDK and pinned dependencies. CI configuration is prepared but has not run on GitHub because there are no approved commits. The empty remote has no default-branch commit. The temporary headless emulator was shut down after its test.

The complete decoded/synthesized default buffer is 20,160 frames (0.420 seconds); all five generated buffers are below one second. Six offline tests establish numerical rendering properties only. No device-based interval envelope, concurrency cap, battery budget, or background reliability claim is established.

The physical Android phone is now connected and authorized, and foreground sound quality has passed user listening review. Next, run the background protocol above before expanding into saved jobs, imports, and the six visual styles. This is a pending measurement gate, not evidence that the requirement is infeasible.

## First physical-phone listening result

On 2026-10-24 the user reported that all sounds sounded fine on the Samsung phone and authorized continuing past the emulator-quality investigation. This is user-observed foreground listening evidence, not an externally recorded spectral or timing measurement. Phone diagnostics from that session include separate Bright Glass Ping, Gentle String Pluck, and Loud Alarm Buzz runs at 144,000-frame (three-second) periods, with advancing playback heads, repeated scheduled onsets, and zero sampled underruns. The playback service was stopped when inspected afterward. No locked-screen or other-app condition was established for those runs.

At the follow-up inspection the phone was USB-powered, battery 32%, reported temperature 27.7°C. This is a setup snapshot, not an energy-use measurement. Next is a short user-observed locked-screen/other-app test, followed by the longer interval, interruption, exact-loop, concurrency, and unplugged soak protocol. Clean foreground sound alone does not close the background feasibility gate.

## Short physical background check

The user was asked to run Soft Ding at a five-second interval, lock the phone for two minutes, use another app for one minute, then check Pause, Resume, and Stop. The user reported that it seemed to work. This is a reported check, not independently verified screen-state or audible-onset capture.

The latest phone CSV contains 412 diagnostic samples spanning 414.055 seconds. Its final playback head is 19,545,600 frames (407.2 seconds of output) and final scheduled onset count is 82. All sampled underrun counters are zero. Seven paused rows share the same playback head of 19,023,360 frames, followed by advancing output after resume. No playback service remained at inspection. Raw CSV is retained only under ignored `device-results/`; no device serial is included in tracked evidence.

These observations support short-session background continuity and pause/resume on this Samsung, under USB power. They do not establish externally measured audible cadence, natural Doze behavior, exact-loop seams, four-source reliability, battery cost, or eight-hour survival. Those gates remain pending. Next is a short Match-mode listening check, then the remaining matrix and unplugged soak protocol.

## Short physical Match-mode check

The user reported that the requested Soft Ding Match-mode listening check worked well, including the requested locked-screen portion. The phone log identifies a one-source run with a 20,160-frame period (0.420 seconds). At inspection its CSV had 42 samples spanning 41.289 seconds, a final playback head of 1,982,400 frames (41.3 seconds), 99 scheduled onsets, and zero sampled underruns. No paused samples were present. The raw snapshot is retained only in ignored `device-results/samsung-match-smoke.csv`.

This supports clean short-session repeated playback by user listening and agrees with the frame-timeline diagnostics. It is not an external capture proving zero inserted/dropped frames or a loop-safe sustained-waveform seam comparison. The finite decaying ding is not a substitute for the planned loop-safe fixture. Concurrent-source, longer silent-gap, energy, and unplugged soak tests remain pending. Next is the four-source mixer check using the existing probe control; this does not yet validate independent saved-job lifecycle controls.

## Short physical four-source check

The user reported that the four-source test continued clearly, with an irregular-sounding pattern. The phone log confirms Soft Ding with four sources and a base period of 144,000 frames (three seconds); the actual test used three seconds rather than the suggested five. The probe's periods were 144,000, 160,000, 176,000, and 192,000 frames (3s, 3⅓s, 3⅔s, 4s). Their drifting onsets explain the irregular pattern without any randomized scheduling.

The captured CSV contains 124 samples spanning 123.890 seconds. Final playback head: 5,947,200 frames (123.9 seconds); scheduled onset counts per source: 42, 38, 34, and 31. All sampled underrun counts are zero and no paused rows are present. The service was stopped at inspection. Raw diagnostics remain in ignored `device-results/samsung-four-source-smoke.csv`.

This passes a short four-source mixing/listening smoke check on this Samsung. It does not establish independent saved-job controls, sustained concurrency under power management, external onset accuracy, or battery cost. Four remains a provisional product cap pending extended tests. The remaining background gate prioritizes long natural silent gaps with the phone locked and disconnected from USB power, followed by the eight-hour matrix and interruption/energy checks.

## Physical five-minute-gap check

The user was asked to run a single Soft Ding with Match and four-source mode off, a 300-second manual interval, and the phone unplugged and locked for 20 minutes. The user reported success and reconnected the phone for collection. The log confirms a 14,400,000-frame period (300 seconds), Soft Ding, and one source. Screen-lock and disconnected-power conditions are user-reported; the CSV does not independently record them or establish actual Doze entry.

The captured CSV has 1,501 samples spanning 1,512.170 seconds (25 minutes 12 seconds), a final playback head of 72,585,600 frames (1,512.2 seconds), and six scheduled onsets. All sampled underrun counters are zero and there are no paused samples. These counts agree with an immediate cue and subsequent cues at 5, 10, 15, 20, and 25 minutes. The service was stopped when inspected. Raw CSV remains only in ignored `device-results/samsung-long-gap.csv`.

This supports survival across repeated five-minute natural silent gaps in the reported unplugged, locked-phone conditions on this Samsung. It does not establish the proposed 50ms audible-onset target, battery budget, 30-minute-gap envelope, eight-hour reliability, or call/focus/routing behavior. The next extended check is a 1,800-second (30-minute) period over at least eight hours, unplugged and locked, starting from an adequately charged battery and retaining before/after battery observations.

## Overnight collection on 2026-10-25 — inconclusive

The user reconnected the phone expecting the soak to be complete. No playback service was running. The latest retained CSV contains 145 samples spanning 144.933 seconds, only 4,080,000 consumed frames (85.0 seconds of output), one scheduled onset, and 60 paused samples at the end. All sampled underrun counters are zero. No eight-hour CSV was present; the largest retained run was the previously collected 25-minute five-minute-gap test. The latest run's period cannot be verified from its single onset because the old CSV format did not persist start configuration.

The old diagnostic format records `paused=true` without its cause. Relevant overnight focus/start logs had rotated away, and Android's queried app exit history supplied no exit record. We cannot determine whether the short run ended after user Pause, audio focus loss, headphone disconnection, explicit Stop, or OS termination following a pause. Phone uptime at collection was about 5.35 days, providing no evidence of an overnight reboot. No observed evidence proves unsupported background playback, and this attempt must not be counted as a pass or an engine/platform failure.

The phone was at 5% battery and USB-powered when collected. The user did not know the starting battery percentage or whether cues sounded overnight, and said monitoring a test this long was impractical. Without a start baseline, run duration, and idle/continuous-audio comparison, the battery drop cannot be attributed to Echorelle. Do not request another blind, listening-based overnight repeat. First validate persistent diagnostics with a short controlled session; any later long soak must be self-reporting.

Probe 0.1.2 adds a per-run `events-<id>.jsonl` alongside the same-id CSV. It records requested configuration, application version, exact period and source periods, initial battery/power/screen state, output initialization, focus callbacks, pause reason/source, resume, Stop, worker completion, normal service destruction, and task removal. Environment sampling every ten seconds records changes in battery percentage, charger connection, interactive/keyguard state, idle mode, power-saving state, and media volume. CSV continues to report frame progress and underruns off the audio thread. Neither journal nor CSV proves physical audible output. Abrupt process death may still produce no final event; the last checkpoint plus Android exit evidence must be used rather than inventing a cause.

This is diagnostic instrumentation, not a change to interruption or restart policy. The probe still pauses conservatively on focus loss and headphone removal and requires explicit Resume; it never restarts after process death. The full product UI remains behind the feasibility gate. Raw collected CSVs remain only in ignored `device-results/`.

Validation for 0.1.2: debug assembly, six JVM tests, and both Android 16 emulator tests passed. Android lint passed with zero errors and seven existing version/style warnings. The updated service smoke test checks the persistent journal's sound/period and battery-baseline fields, explicit pause source, Resume, Stop, and worker completion. The same test was then run directly on the USB-powered Samsung (Android 13), passing in 6.774 seconds while preserving prior app data. This verifies the recorder and a short controlled lifecycle, not background soak duration. The main APK was updated in place; no Git commits were made.

## Full app implementation — 2026-10-25

The uncommitted **0.2.0** build now implements saved jobs, independent controls, Delete/Undo, Stop All, imported audio validation/private copies, exact Match and eligibility, per-job gain, a provisional four-active-job cap, five explicit built-in previews and seam previews, optional duration-preserving edge fades, and all six global visuals. The prior probe evidence above remains historical evidence for the earlier engine, not a full-app long-duration pass.

Actual checks in this build session:

- Eleven JVM tests passed: retained six numerical/probe tests plus five real-job timing/eligibility/pause/stop/seam tests. Both old and new exact-loop paths preserve 1,000 joins in offline PCM assertions.
- Five Android emulator tests passed on Android 16/API 36.1: decoded WAV/stereo resampling and offline copy/failure cleanup; MP3/AAC delay/padding; independent pause/resume/stop; four-active cap/duplicate-Start/focus interruption; UI save/preview/Delete/Undo/all-six-visual selection and persistence across activity recreation.
- The four service/import tests passed directly on the Samsung SM-G781U1 / Android 13 (6.086 seconds for the expanded suite). Existing app data and historical probe logs were preserved. These are controlled runtime/decoder assertions, not a new subjective sound-quality review or externally recorded output measurement.
- The four-test suite before adding the cap/focus case also passed on the emulator at **200% font scale with animator duration scale zero**. The UI test selects every visual and scrolls to job controls; its captured large-text Home layout was inspected. Settings were restored afterward.
- Phone UI instrumentation did not find an accessible Compose hierarchy on the phone; screen/lock state was not independently captured. Do not report a phone UI pass; emulator UI coverage passed. No long user monitoring was requested.
- Debug APK assembly and lint passed. Lint currently reports zero errors and 14 warnings (inlined gapless keys on older APIs, pinned newer-version suggestions, a conservative wake-lock analyzer notice and a preferences style suggestion). CI now includes an API 33 emulator job, but has not executed remotely because there is still no approved commit.

The compressed fixture tests caught and fixed two concrete platform differences: Android 16 M4A extraction supplied 1,024 delay samples but omitted 768 final padding samples; the Samsung extractor also omitted the delay. Reading the audio track's integer container duration and edit start makes both generated 1.2-second MP3 and AAC/M4A fixtures retain exactly **57,600 frames at 48,000 Hz** on both devices. A 441-frame, 44.1kHz stereo WAV fixture becomes exactly 480 mono output frames, retaining its intentional silent boundaries. This covers these fixtures rather than every encoder/container combination.

The actual app retains bounded `app-events-<id>.jsonl` diagnostics in debug builds. Start records include exact clip/period frames, source identity, gain and initial environment. Checkpoints include cumulative render/consumption/underrun observations, per-job phase/mode and interruption reason. Focus/noisy/user commands are recorded off the render thread. Normal service teardown appends a final environment/frame summary; abrupt death can still omit it. `tools/Collect-AndroidDiagnostics.ps1` collects the journals and writes automatic per-session summaries under ignored `device-results/` without uploading or asking someone to listen for hours. It deliberately does not infer an unknown end cause or certify audible output. Checkpoint growth is capped; a record ending at that cap is not proof of a later failure.

Still unverified: multi-hour real-app survival, natural Doze, 30-minute or 24-hour silent-gap reliability, unplugged comparative energy/CPU/thermal cost, external audible onset/seam accuracy, physical calls/Bluetooth/headphone transitions, human TalkBack review, API 26–32 devices, and the wider codec/format matrix. Controlled audio-focus interruption is now covered; it is not a substitute for a real call/route test. Resource/input bounds and four active jobs remain provisional implementation guardrails. The existing positive listening/background checks stand, and the inconclusive overnight attempt remains inconclusive. Further extended tests belong to this real app and do not gate its implementation.

Final artifact follow-up: the final APK passed all 11 JVM tests, lint/debug assembly, and all 5 emulator tests (14.882 seconds). The later phone repeat disconnected during execution after successful installation; count that repeat as incomplete, preserving the preceding 4-test Samsung pass rather than inventing a new one. The final APK is installed, but phone-side collection/test-package cleanup could not finish once ADB disconnected. The diagnostic collection script was exercised successfully on the emulator: 11 journals and automatic summaries were collected locally; the final short preview session reported 3.146 recorded seconds, a normal service end, and a 100%-to-100% USB-powered battery snapshot. This is a collector smoke check, not an energy result or soak pass.

## Emulator-only completion follow-up — 2026-10-25

The user no longer has the Samsung and authorized finishing available testing with the emulator. No further phone access or prolonged listening is required. The debug application's production code/APK is unchanged from the preceding build; this follow-up expands automated tests and their reproducible harness.

Actual Android 16/API 36.1 emulator results:

- Three added reliability tests passed together in 68.299s: AtomicFile backup restoration/corrupt-document recovery and orphan cleanup; 25 MiB input rejection plus cancellation leaving no metadata/temporary assets; and an unaccelerated 65-second four-job screen-off/background run.
- The background job periods were 1s, 5s, 20s and 0.420s Match. Test gains were zero, so this checks runtime/output consumption rather than audibility. All four stayed Playing and each advanced more than 60 seconds. The 20-second job reached at least four immediate/start-to-start onsets. Six persisted checkpoints included `interactive=false`; the last checkpoint reported 2,926,720 consumed frames and zero sampled cumulative underruns. The journal spanned 65.664s through Stop All. Instrumentation shutdown ended that process before a normal service-end record, so its summary correctly reports `cleanServiceEnd=false`; do not infer an app failure or an unknown end cause from that test-runner ending.
- Eight service/UI tests passed together in 22.695s at 200% font scale, dark theme and animator scale zero. Coverage includes independent controls, cap/focus, exact WAV/MP3/AAC counts, system MediaSession Pause/Resume/Stop, Match-off minimum explanation, imported-duration revalidation with disabled choices still visible, preview cancellation on editor exit, live activity recreation without resetting frames, all six visual choices, persisted selection and Delete/Undo. Dark large-text Home capture inspected. Emulator display/theme/animation settings restored afterward.
- An initial media-session test failure was a harness issue: globally adopted shell permission identity overlapped asynchronous app audio-focus requests. Privileges are now scoped only to controller discovery; the app requests focus under its real identity. Direct shell injection of Android's protected headphone-noisy broadcast was denied by the platform, so no headphone-route pass is claimed.
- Two separate true process-recovery scenarios passed: app force-stop and actual emulator reboot, each using a seed instrumentation process and a separate verification process. Saved definitions and global visual persisted, reopening did not start audio, and the original library was restored. `tools/Test-AndroidProcessRecovery.ps1` reproduces both without clearing app data and refuses physical devices. Its two phase-only tests deliberately skip in the ordinary suite; run the script to exercise those process boundaries.
- Current coverage is 11 passing emulator tests across the reliability and service/UI runs, plus both process-recovery scenarios. Eleven JVM tests, lint and APK assembly remain successful. Lint remains zero errors/14 warnings. No Echorelle playback service remained after the final recovery check; post-reboot system/Play-services wake locks are unrelated to the app.

The unchanged 0.2.0 APK SHA-256 is `38E699A7ECA49770A459FF1AE040728E3467F3ABC44F593252CD95116F62043D`. Journals, summaries and screenshots are local under ignored `device-results/emulator-follow-up/`; the collector successfully processed the retained 20 app journals. No commit or push was made.

Remaining hardware-only/release validation is unchanged: extended natural Doze and multi-hour survival, actual unplugged energy/thermal cost, external speaker onset/seam capture, physical calls/headphones/Bluetooth, wider device/API/codec coverage, and human TalkBack interaction. Short emulator screen-off success is not a physical-device energy or reliability certification. The earlier positive Samsung checks and inconclusive overnight evidence remain recorded as they were.

## User correction: interval progress and product copy — 0.2.1

All six visuals now represent beginning-to-end completion of the chosen interval. Completed structure changes across the whole duration; duration-adaptive working detail remains visible during long intervals. Five seconds uses one pass; one minute uses eight, twenty minutes uses 150, and two hours uses 900 work passes, with each pass at most eight seconds. Canvas interpolation observes timestamped audio snapshots and redraws at display refresh rate, with prediction capped at 150ms. Audio scheduling/mixing commands remain unchanged. Paused/interrupted drawings freeze; stopped shapes reset. Reduced motion uses static progress poses; sub-second loops retain a calm continuous texture rather than rapid repeated fills.

Actual validation on the updated APK: 15 JVM tests pass, including frame interpolation, exact start/end/long-duration precision, onset wrap, stale-observation limits and pause behavior. Nine emulator service/UI/render tests passed together in 46.607 seconds, plus the atomic recovery test passed separately. The render test captured all six visuals at 0/25/50/75/99% for 5s, 1m, 20m and 2h durations (120 drawing captures), and verified motion between observations and frozen paused images for each style. These are representative phase/render tests, not two hours of playback. Loom beginning/middle/end and other style captures were inspected, along with Home after removing the status clutter. Existing service controls, focus/cap, imports, visual selection preserving audio phase and UI reattachment still pass. Debug assembly/lint pass with zero errors and the existing 14 warnings.

The provisional/testing footer, routine restart-status text, and normal-screen frame/cache/engine commentary were removed. Original imported metadata is also sanitized for display. Actual errors and useful instructions remain. This product-copy rule is now explicit in AGENTS.md; test/measurement evidence stays in documentation.

Current APK is **0.2.1 / code 5**, SHA-256 `BFB573B052D6824F1ADDD0F7BC7F799004E2F77736C7F21C0A53355B8BAFC5F4`, installed on the emulator. No physical-phone recheck was possible or claimed. Existing reliability/energy/route/TalkBack limitations remain unchanged; all changes remain uncommitted.

## Textured Paper Bird build — 0.2.2

Only visual rendering and its under-five-second cutoff changed; audio scheduling is unchanged. Final emulator validation passed 11 rendering/service/UI tests in 61.757 seconds, with 15 JVM tests, debug assembly and lint (zero errors/15 warnings). Native-renderer studies sampled a full five-second construction, accelerated two-hour construction, and 12-second real-time excerpts at 25/50/90% of a two-hour interval. They are rendering samples, not a two-hour playback/energy run. No new physical-device, natural Doze, call/route, audibility or extended reliability claim is made. See `review.md` for exact coverage, artwork limitations and the emulator test-data loss incident. APK SHA-256: `58E70CA3C0B696B452EBD222AB90A2CFADEC7A5AE2AB95D59189635D35465BFA`. Changes remain uncommitted.

## Flat-paper opening — 0.2.3

A visual-only correction makes Paper Bird begin as a flat sheet. Three emulator rendering tests, debug build and lint passed; no audio-owner changes or new reliability/energy observations. Exact evidence and APK checksum are recorded in `review.md`. App data was backed up and installation/testing used the direct, data-preserving workflow.

## Authentic long origami and timing correction — 0.2.4

The saved 1,500-second job was correctly configured; the initial visual gather had a three-second cap and misleadingly exposed an invented complex bird. Long Origami now uses a real Miura-ori crease pattern with its endpoint tied to the entire observed interval. No audio scheduling changes. Seventeen JVM and twelve combined emulator tests passed, including a live three-second observation of a 1,500-second fixture still around 0.2% completion. After the final crease-stroke correction, three rendering tests and build/unit/lint passed again (zero errors/15 warnings). The unchanged-library hash check passed. These are geometry/phase checks and a short live fixture, not 25 minutes of audio or energy testing. Longer reliability and physical-device limitations remain as previously documented. See `review.md` and `origami-references.md` for exact evidence and remaining animation limits.

## 0.3.1 origami reveal verification

Four emulator visual tests passed in 60.194s for early completion/rotation, phase interpolation/pause, under-five static artwork and screen-off suppression/recovery. The native renderer verified completed geometry throughout the reveal and exact final-pose return for all three projects. Audio scheduling and wake-lock behavior did not change; this is not an energy, physical-phone or extended reliability measurement. See docs/review.md for APK and validation details.

## 0.3.2 stained-glass visual verification

Four final emulator tests passed in 82.364s, including screen-off/viewport no-work checks and recovery to current observed phase after 500 simulated missed intervals. No visual replay occurs. The 24-hour stress artwork prepared in 883.0328ms and first reconstructed 90% in 71.5231ms (34,558 steps). These emulator measurements are not physical energy/battery evidence; audio streaming and partial-wake-lock limitations are unchanged. Material/flight/haze/artwork are entirely UI-side. Final APK/hash and actual validation are in docs/review.md.

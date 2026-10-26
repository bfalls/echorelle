# Echorelle

Interval sounds & seamless loops. Kotlin/Jetpack Compose Android app, offline, with no accounts or backend.

Version **0.3.2** includes saved independent jobs, local audio imports, Match looping, explicit previews, per-job volume, and three global visuals: Stained glass, Cube, and Origami. Stained glass adds original classic-style Magnolia, Iris garden and River of light artwork, piece arrivals about every 2.5 seconds, faint atmospheric haze and a final light reveal. See [stained glass](docs/stained-glass.md). Origami now folds a single uncut sheet one operation at a time: a 9-operation dart, 14-operation samurai helmet, or original 189-operation radial fan, selected by duration. Origami finishes five seconds early (half the interval below ten seconds), rotates the finished object, and returns to its resting view. Miura is removed; Hydrangea remains unimplemented. See [sequential origami](docs/sequential-origami.md). Cube uses legal 3×3/4×4/5×5 layer turns, solves a few seconds early, then shows its solved faces and returns to the starting view. Each interval gets a fresh reversible scramble, with one turn per second while solving. Visual work stops while hidden or screen-off and seeks the current pose on return. Intervals under five seconds show static artwork. All changes remain uncommitted. Long-duration reliability, natural Doze, audible seam/onset capture, and energy cost remain unverified. See [cube behavior](docs/cube-animation.md), [origami sources](docs/origami-references.md), [validation](docs/background-feasibility.md) and the [review handoff](docs/review.md).

## Build and install

Open `apps/android` in Android Studio. Use its JDK 21, SDK 36, and the existing Gradle 9.4.1 / AGP 9.2.1 wrapper. Java source target is 17; minimum Android API is 26. Only API 33 on the Samsung and API 36.1 on the emulator have been exercised locally.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
cd apps/android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`. Select the intended authorized device from `adb devices -l`, then install in place (preserves data):

```powershell
adb -s YOUR_DEVICE install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_DEVICE shell am start -n com.echorelle.android/.MainActivity
```

Add a job, enter an interval in seconds or enable **Match**, choose a sound and volume, and **Save job**. Saving is silent. **Start** plays immediately; each interval is start-to-start. Pause freezes that job's clip/cycle; Resume continues it. Stop resets it. Stop before editing. Delete offers brief Undo, restoring stopped. Stop All releases playback resources. Four active jobs, including paused/interrupted jobs, is a provisional cap.

The five built-in sounds show descriptions and actual durations. Selection is silent; **Preview** plays once, **Seam preview** repeats three times, and leaving the editor stops previews. Imported audio is copied privately and validated before use. Manual timing supports 1 second–24 hours; Match allows shorter exact loops. Invalid/too-long choices remain visible with reasons. Resource guardrails: 25 MiB compressed input, 60 decoded seconds per import, 128 MiB audio library, 100 saved jobs and 100 import entries. Remove unused imports from the editor to reclaim space. These limits are implementation bounds, not a measured reliability envelope.

Home's **Visual** button opens two silent preview tiles. Tap one to apply globally and persist immediately. Origami is the default; removed legacy visual preferences safely fall back to it. Visuals observe audio phase and cannot restart it. System theme, animation scale/reduced motion, readable state labels, wrapping controls and scrollable large-text layouts are supported.

Media notification/session controls pause/resume/stop the collection. Focus loss (including duck requests) and headphone removal interrupt currently playing jobs, requiring explicit Resume. Already manually paused jobs stay paused. The app never automatically starts saved jobs after process termination, force-stop or reboot. Normal media volume applies.

See [audio behavior and architecture](docs/audio-behavior.md), [sound provenance](docs/audio-assets.md), and [privacy](docs/privacy.md). [Android CI](.github/workflows/android.yml) builds, runs unit/lint checks, uploads the APK and runs an API 33 emulator suite. Remote CI has not run because no commit has been approved.

## Later self-reporting measurements

Debug builds retain bounded local journals with exact start configuration, commands/interruption reasons, frame consumption, underruns, battery/power/screen/idle observations, and a normal service-end summary. Collect them without listening for hours:

```powershell
.\tools\Collect-AndroidDiagnostics.ps1 -Serial YOUR_DEVICE
```

Raw logs and automatic summaries go to ignored `device-results/`. The script reports observations without claiming audible output, inferring an abrupt termination cause, or attributing battery drain without comparison runs. No logs are uploaded. The earlier inconclusive overnight attempt remains inconclusive; it does not block this implementation.

## Repository policy

Every Git commit needs explicit approval for its exact reviewed changes/message, including the initial commit. Read [AGENTS.md](AGENTS.md). No license has been selected. No iOS/Mac, accounts, backend or executable jobs are included.

The Gradle wrapper retains upstream Apache 2.0 headers; [Gradle license](docs/third-party/gradle-LICENSE.txt) and [notice](docs/third-party/gradle-NOTICE.txt) do not select a license for Echorelle's original code or tones.

For emulator process recovery (requires the matching debug/instrumentation APKs installed), run one scenario at a time:

```powershell
.\tools\Test-AndroidProcessRecovery.ps1 -Serial emulator-5554 -Mode force-stop
.\tools\Test-AndroidProcessRecovery.ps1 -Serial emulator-5554 -Mode reboot
```

The harness refuses physical devices and restores saved definitions after each scenario. The normal emulator suite includes a 65-second real-time screen-off test; two process-phase methods intentionally skip there because a genuine process boundary must be driven externally. See the latest validation in `docs/background-feasibility.md`.

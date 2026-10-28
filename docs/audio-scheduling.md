# Audio scheduling and screen-off work — 0.4.0

The 2026-10-27 design replaces continuous silent PCM for long gaps. Audio still owns every deadline; UI and visuals only observe it. Saved job schema and import/frame-count validation are unchanged.

## Continuous and sleeping paths

Match loops and all-mix gaps shorter than five seconds retain one sample-continuous AudioTrack. This avoids repeatedly opening the device for very short intervals. Other jobs still mix through the single owner. A long-interval job cannot suspend output while another job is actually playing, continuously looping, or about to play within that short-gap threshold.

When every playing voice is in a silent portion and the earliest next sound is at least five seconds away, finish consuming the real queued samples and release AudioTrack. Calculate the next sound from a monotonic frame clock anchored to the running session. Do not send PCM to represent the long gap. Each voice's paused/running state and independent local phase persist. An immutable gap observation captures each voice's starting frame count so publication cannot count a gap twice during a concurrent transition.

The worker blocks on a semaphore until the preparation deadline or an immediate command. One second before the next sound it creates the audio output, then blocks again until the actual deadline. Device preparation time is deducted from the remaining wait. Sound windows are then mixed/submitted, with at most one 5ms block of alignment padding at a non-aligned clip boundary; no PCM is submitted throughout the long gap. Subsequent deadlines use the original monotonic schedule, not the completion time of the previous sound. Partial writes, overlapping jobs and exact Match frame joins retain the native mixing path.

If the preparation deadline is at least five seconds away and exact alarms are available, schedule one AlarmManager alarm-clock event for the earliest timer, then release the app's partial wake lock. Its non-exported receiver reacquires the lock before handing control back to the worker. Stale delivery generations are ignored. Start/Pause/Resume/Stop/Stop All, focus interruption and wall-clock changes wake the semaphore immediately and cancel/recompute the alarm. There is no per-job polling loop.

Alarms are converted from monotonic remaining duration to RTC only at the platform boundary. Wall-clock changes trigger recalculation; they do not change interval phase. Under an exceptional wake delay exceeding one second, rebase the global schedule once after playing the due sound, rather than playing missed intervals in a burst. Normal small wake jitter is absorbed by the next absolute deadline. Hardware startup/output latency still limits onset precision across separately opened tracks; the short/Match path retains continuous frame timing.

## Platform permissions and behavior

On Android 13+, declare the automatically granted `USE_EXACT_ALARM` permission because precise interval timers are the core user-facing function. No special app-access screen or alarm permission prompt is launched. For Android 12/12L, the legacy `SCHEDULE_EXACT_ALARM` declaration is bounded with maxSdkVersion 32 and access is checked; it is never requested interactively. Earlier Android versions do not require this special exact-alarm access. If access is unavailable, keep the wake lock and block the thread; AudioTrack still closes for a long gap, but the same deep-suspend power saving is not claimed. Deep-Doze behavior of that older-platform fallback remains unverified.

Use `setAlarmClock` for the explicitly started interval timer. It is a user-visible timer wake event, not a hidden maintenance job, and avoids the idle-frequency throttling of `setExactAndAllowWhileIdle` for frequent repeats. Android may display its normal upcoming-alarm indicator; tapping it opens Echorelle. Android can deliberately keep full Doze away from an imminent alarm, so releasing the app wake lock does not imply that the entire device will enter its deepest idle mode. A foreground playback service still supplies ongoing controls during a running timer. Killing the process or rebooting never automatically executes saved jobs; a receiver without a live owner does nothing. Destroying the service cancels its alarm and releases resources.

Modern timer-permission eligibility is based on the app's actual core function; Play distribution still requires the applicable declaration/review. No full-screen intent, battery-exemption request, account/backend, or extra permission dialog was added.

## Hidden UI and resources

MainActivity reports visibility, and screen on/off broadcasts gate publication. The service blocks its update coroutine on a conflated channel while hidden rather than waking ten times per second. Actual audio transitions/commands can publish state as events; continuous short/Match rendering supplies at most one background observation per second. Long sleeping gaps have no periodic publications or journal timer. Returning to the app requests a current audio-owner snapshot and the normal visible 100ms observations resume.

All three visuals already gate frame refresh, drawing and cancellable preparation by resumed/interactive state and viewport. Those gates remain active. Renderer/model caches retain bounded memory without running workers. On return, visuals seek the current observed phase instead of replaying missed animation. An undisturbed hidden gap has no asset/audio decoding or periodic file I/O; event-triggered diagnostics remain possible. The debug-only accepted-output test capture uses preallocated storage and is inactive in ordinary runs.

## Validation boundaries

See the latest review/build handoff for actual checks and measurements. Emulator CPU counters, no-PCM/no-wake-lock assertions and normal speaker-loopback measurements can validate behavior; they cannot establish battery percentage per hour or physical phone suspend reliability. An idle-request test records whether Android actually enters forced Doze instead of assuming it did. Near-term alarm-clock events may legitimately prevent that transition. No new phone soak is required as a build gate.

References: [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [AlarmManager alarm-clock events](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)), [exact timer permission policy](https://support.google.com/googleplay/android-developer/answer/16558241).

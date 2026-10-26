# Cube animation

## Current behavior — 0.2.9

The cube is a local mathematical NxN model: 3×3 below 20 minutes, 4×4 from 20 minutes, 5×5 from one hour. Each playback start receives a random visual seed on the command path. Observed consumed-frame count selects the current interval; mixing that interval index into the seed gives a fresh deterministic scramble each time. Pause, scrolling, activity recreation and reopening in the same playback session recover the same interval's route. There is no visual state in saved job definitions and no visual calculation in the audio render path.

A solved cube is scrambled with legal random quarter-turns, including inner slices on larger cubes, then solved by reversing and inverting those turns. Consecutive scramble turns use different axes to avoid immediate cancellation. There is no general arbitrary-input solver and no optimality claim. This supersedes the old staged layer-algorithm route. Random reversal is not guaranteed to make solved sticker count rise monotonically or provide a calibrated visual percentage.

## Cadence and final display

The solving portion uses one quarter-turn slot per second; a fractional last second receives a shortened final slot. Each turn moves during the middle 80% of its slot, leaving a brief settled beat either side. Thus 1,265 seconds has 1,260 solving slots plus the five-second final display. This replaces the four-seconds-per-turn target, 2,048-turn cap and uneven stage timing.

Solving finishes five seconds before completion, or halfway through intervals shorter than ten seconds. During the final display the solved cube turns through a full orbit, pitch and restrained roll, revealing its other faces and returning to its original presentation. Under five seconds stays static. Reduced motion uses static progress poses and a stationary solved cube. Pause freezes both solving and display. The chooser's one-minute demo is compressed into its silent preview cycle and does not change playback.

Every sticker retains its original color and follows its cubie's integer coordinates and outward normal. Individual dark cubelet bodies rotate with their layer; there is no stationary black shell. The original shaded/rounded material treatment and soft shadow are retained.

## Bounded preparation and instant seeking

A route has at most 86,400 turns within the 24-hour input bound. Exact poses are checkpointed every 128 turns, not stored at every second. The largest checkpoint table is 2,430,000 bytes. Turns and boundaries add bounded memory; an activity ViewModel retains at most six cube renderers, surviving recreation. Ordinary frames reuse the working pose; a jump restores one checkpoint and applies at most 127 turns, independent of elapsed background time. Skipped intervals are never constructed or replayed.

Preparation runs on a cancellable Default dispatcher only while the card is in the list viewport, the activity is resumed and the device interactive. Lifecycle events and screen-on/off broadcasts gate rendering and preparation; no refresh transition exists while inactive. Cached models consume memory but do no work themselves. A newly needed route after missed intervals is prepared asynchronously; its initial preparation is not promised instantaneous. In the emulator, a worst-length cold route took 225.38 ms, while 100 sampled seeks had p95 0.306 ms and max 0.506 ms. These are local observations, not a device-independent latency guarantee.

No new wake locks, alarms, services or permissions belong to visuals. The audio service still has a different background power cost; see `background-feasibility.md`.

## Sources and validation

[Rubik's official solve guide](https://rubiks.com/solve-guide) and [WCA Article 12 notation](https://www.worldcubeassociation.org/regulations/#article-12-notation) describe face turns/notation. The mathematical engine, generated routes and renderer are original implementation; no photos, logos or external solver dependency are bundled. Android lifecycle/interactive-state references: [activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle), [PowerManager](https://developer.android.com/reference/android/os/PowerManager).

Behavior tests cover legal slice identities and inverses, exact reverse playback, fresh/reproducible seeds, one-second cadence through 24 hours, bounded checkpoint storage/seek/cancellation, solved-before-display timing and exact reveal return. Emulator tests confirm no hidden cube work, deterministic current-interval recovery after 500 skipped cycles, actual-card phase/pause interpolation and static under-five behavior. Rendering samples are not an hours-long playback or energy run.

Only Origami (`BIRD`) and Cube (`CUBE`) remain selectable. Removed visual preferences migrate to Origami without losing jobs/imports. The six future concepts in `animation-ideas.md` remain deferred.
# Sequential single-sheet origami — 0.3.1

The user's latest rule is strict: one regular sheet, no cuts/slits/tears, no detached decorations, no added sheets, and no morphing from a sheet into an unrelated mesh. A single folding operation runs at a time. This replaces the old stylized bird; its textured material treatment is retained, not its independent triangle rig. Miura stays removed and Hydrangea remains unimplemented.

## Projects and timing

| Selected interval | Project | Encoded operations |
| --- | --- | ---: |
| Under 30 seconds | Square-sheet dart | 9 |
| 30 seconds to under five minutes | Traditional samurai helmet | 14 |
| Five minutes and longer | Original radial fan | 189 |

Under five seconds shows the completed artwork statically. These are automatic artwork thresholds, not interval defaults or timing presets. Silent chooser previews currently demonstrate the short dart.

The count is individual physical operations, not a publisher's numbered diagram count. Left and right folds are separate; creasing and reopening count separately. The dart prepares its centre, folds each nose corner and narrower edge, closes its body, then forms each wing separately. The helmet prepares the centre, folds its triangle/corners/flaps/horns individually, rolls the brim, and creases/opens/folds its back flap behind the model. The fan prepares 63 actual radial creases with 126 crease/open operations, then forms those 63 ribs separately. It is an original geometric corner-fan project, not a traditional seashell, Hydrangea or reproduction of another designer's model.

Observed audio phase reserves the final five seconds for a completed-object rotation; intervals below ten seconds reserve half their duration instead. Folding progress is the observed fraction divided by that folding window, clamped at one. It selects `floor(foldingProgress × operationCount)` and the current operation's local angle. Once folding completes, the entire rigid object turns through a full revolution with a gentle tilt, returning exactly to its finished resting view at interval end. Reduced motion shows that resting view throughout the reveal. Quintic easing moves only that hinge's selected material. There is no opening measured in fixed elapsed seconds, early saturation, UI audio timer or simultaneous global collapse. At 1,265 seconds the 189-operation fan folds over 1,260 seconds, averaging about 6.67 seconds per operation; at two hours it folds over 7,195 seconds, about 38.07 seconds per operation. Large preparation folds move broad portions of the sheet, and completed creases remain in the paper texture. Counts and perceptual percentage readability are different: especially during preparation, crease work rather than finished silhouette communicates progress. User art acceptance and hour-scale legibility remain subject to review.

## Geometry and material

`PaperVertex` permanently records original material coordinates alongside current 3D coordinates. `SheetFolds.Builder` subdivides convex panels for meshing where a crease passes; it does not remove any material or create a cut. Selected polygons rotate rigidly around a straight hinge, with interpolation of material coordinates at new mesh vertices. Fixed polygons remain fixed. Shared boundaries stay attached throughout intermediate angles. Closed-fold stage snapshots provide direct O(1) operation selection and bounded vertex transforms on seek; missed steps are not replayed.

Layer marks select the actual left/right/front/back flaps, rather than rotating coincident but unrelated layers. Layer order is preserved at the start of an operation and reversed as a flap turns over. The fan's creases radiate from a boundary corner: its unformed remainder is one rigid flap, so each successive material crease has an exact straight 3D hinge. Its original 64-panel design uses alternating 61.2-degree turns in the forming pass. It has no closed vertex loop that would require simultaneous collapse.

An operation sequence owns immutable polygon/triangle snapshots. At most three project geometries are cached in the activity ViewModel. Each card owns mutable projection, texture and paint state. Grain/fibers are procedural and attached to material coordinates; crease texture is rebuilt from the completed crease list when seeking, not by replaying past animation. Only actual panel boundaries are edged; render fan triangulation diagonals are not drawn as folds. The flat starting sheet is rendered as one uncreased surface.

This is an ideal zero-thickness sheet model. Geometry checks cover conserved material, rigid distances, connected boundaries and adjacent-step continuity. The radial fan forming pass also checks orientation/non-crossing of its projected sectors at sampled intermediate angles. The system is not a general contact solver: finite paper thickness, realistic fold radii, hand accessibility, and collision-free pocket insertion are not claimed validated. In particular the helmet's tucked flap is represented as a behind-layer hinge fold, not a simulated flexible pocket manipulation. Do not call these tests proof of general physical origami simulation.

## Lifecycle and limits

Preparation runs off the UI thread with cancellation checks, gated by viewport, resumed activity and interactive screen state. Hidden cards do not draw or prepare. Returning in the same project seeks the current fold directly; reopening after a different duration/project may need asynchronous preparation. Pausing freezes the whole observed pose. Reduced motion quantizes static poses. No new audio scheduling, wake lock, alarm permission or background visual worker is added. Audio energy limitations remain documented separately.

Builder panel counts are capped at 12,000 per operation; tests assert fewer than 10,000 render triangles for these projects. Immutable project geometry is shared, while per-card mutable bitmaps are not. Source diagrams/photos are ignored research files and are not bundled into the APK.

## References and candidate review

- [Fold 'N Fly: Basic Dart](https://www.foldnfly.com/1.html): reference for the standard nose/body/wing construction. This implementation uses an original square-sheet coordinate encoding and an open-wing final pose, rather than copying diagram images or prose.
- [Origami.me: traditional samurai helmet](https://origami.me/samurai-helmet/): all eight diagram images visually reviewed; paired folds were separated into individual operations. No diagram assets are shipped.
- [Siggy: Pleated Leaf](https://freethoughtblogs.com/atrivialknot/2022/12/02/origami-pleated-leaf/): researched the grid/alternating pleat sequence and finished reference. The attempted rigid flat-fold rendition did not reproduce the relief satisfactorily; it was rejected during visual review and is absent from the app.
- [Origami Instructions: Easy Leaf](https://www.origami-instructions.com/easy-origami-leaf.html): reviewed its photos; the final shaped leaf requires flexible-sheet behavior beyond the present simple hinge implementation. Not shipped.

The radial fan is independent original construction, with explicit material-coordinate creases and verified connected rigid motions; no reference creator is implied for it. The app contains no downloaded paper photos, diagrams, third-party solver or new runtime dependency.

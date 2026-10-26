# Origami sources and implementation

Current implementation is documented in [sequential origami](sequential-origami.md): dart, helmet and original radial fan, all one sheet and one active folding operation. The earlier bird and Miura derivations below are historical. Neither the pleated-leaf candidate nor Hydrangea is shipped.


## Retired in 0.2.8

The user rejected Miura as boring, with no good stopping point, after reporting almost motionless paper on a 1,265-second job. Its geometry and dedicated tests are deleted from the app. Origami temporarily uses the approved stylized bird for all durations, driven by whole-interval audio phase. A richer authentic long-duration folding sequence and Hydrangea remain unimplemented. The material below is a historical derivation/research record, not the current implementation or permission to restore Miura.

## Correction, 2026-10-25

The prior long bird was an invented arrangement of hinged triangles, not a verified sheet-folding model. Its initial sheet gather was capped at three seconds, so the long job quickly acquired a bird-like silhouette and then changed too little perceptually. The saved emulator job is correctly 72,000,000 frames / 1,500 seconds. Audio and the main whole-interval observer were not found to shorten that period. Retire the invented complex bird, not just its name.

The short bird remains a stylized animation the user liked; it is not claimed to be a verified physical origami tutorial. Long intervals now use a traditional single-sheet Miura-ori corrugation. The chooser is called **Origami**, while persistence keeps the existing `BIRD` key. Under five seconds still shows static artwork.

## Primary references

- [Michał Kosmulski: Miura-ori instructions](https://origami.kosmulski.org/instructions/miura-ori). Identifies the traditional herringbone / zigzag corrugation and links step-by-step instructions and a video.
- [Eric Gjerde: Bauhaus Foundation Course booklet](https://www.origamitessellations.com/2018/01/bauhaus-foundation-course-instructional-booklet/). Includes the Basic Corrugation, Hypar and other folded paper exercises.
- [Schenk and Guest: Geometry of Miura-folded metamaterials](https://doi.org/10.1073/pnas.1217998110). Research reference for rigid Miura folding and its one-parameter kinematics.
- [Madonna Yoder: basic twists and tessellations, OrigamiUSA](https://origamiusa.org/thefold/article/basic-twists-origami-tessellations-and-new-video-series). Explains single-sheet tessellations, including patterns built one twist at a time. Relevant to future richer sequences rather than merely increasing decorative lines.
- [Fujimoto Hydrangea diagrams, John S. Smith, CFC Origami](https://cfcorigami.com/sites/default/files/diagrams/igaribi/hydrangea_john_smith.pdf). A candidate for an eventual genuine layered folding sequence; not implemented here.
- [Amanda Ghassaei's Origami Simulator](https://origamisimulator.org/). A validation/research tool; it explicitly simulates simultaneous crease folding, not a sequential tutorial. It is not bundled into the Android app.

These are references, not shipped image/video assets. The app uses original procedural paper material and an original analytic geometry implementation. No third-party diagrams, photographs, videos or simulator code were copied into the APK.

## Geometry used in the app

MiuraSheet uses parallelogram cells, with initial lengths `a`, `b` and sector angle `alpha = 60 degrees`. Its own folding parameter is `theta`, from zero to 52 degrees; this parameterization should not be confused with every paper's naming conventions.

```text
S = a cos(theta)             H = a sin(theta)
V = b cos(alpha)/cos(theta)  L = sqrt(b² - V²)

x(i,j) = i S + parity(j) V
y(i,j) = parity(i) H
z(i,j) = j L
```

The two cell edges have invariant lengths `sqrt(S²+H²)=a` and `sqrt(V²+L²)=b`. Their dot product is `±S V = ±ab cos(alpha)`, so panel shape and diagonals are preserved. Neighboring cells use the same crease vertices. No rubber-sheet interpolation or inserted bridging surfaces are used for the Miura model.

The raw lattice has staggered sides. Boundary cells are clipped in the original material plane to a 1.8 by 1.35 rectangle, then those exact material points follow their rigid panels. This represents a regular rectangular sheet with partial edge panels; it does not require cuts inside the paper. Texture coordinates remain attached to material points.

Longer durations use 6x4, 12x8 or 18x12 grids. The folded endpoint is an architectural paper relief, not a bird. The native renderer shares the existing grain, shading and soft ground-shadow treatment.

## Timing and limits

For long jobs, `theta = 52 degrees * observed whole-interval fraction`. There is no elapsed-seconds opening cap, preview clock, early saturation or decorative fold loop. At 3 / 1,500 seconds the parameter is 0.2%; at 375 / 1,500 it is 25%; the target is reached only at completion. Pause freezes the observed phase and changing styles does not restart audio.

Miura-ori is a simultaneous one-parameter collapse of a real crease pattern. It is **not** a long sequence of distinct hand-folding instructions. More grid cells increase geometric detail but do not magically create more independent folding steps. Movement can be slow at hour-scale durations; this implementation does not establish that its perceptual movement meets every long-duration art requirement. Hydrangea or twist tessellations would require their own genuine, validated crease sequence, not an invented bird with more fins.

Tests check rectangle coverage, panel distances, shared-vertex continuity and unsaturated progress through 1,500 seconds. These are geometric checks, not proof of finite paper-thickness collision clearance, physical hand-foldability at arbitrary grid density, or a measured 25-minute audio/energy run. No such claims are made.

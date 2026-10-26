Latest scope: only Origami and Cube; the five other styles mentioned below are removed. Cube now uses real 3×3/4×4/5×5 geometry and reserves its final five seconds (half for short intervals) for a solved-face reveal and return. Read `cube-animation.md` and the latest handoff before using older artwork descriptions.

# Animation art and motion contract

Latest correction: the user rejected the invented complex bird and its three-second visual gather on a 1,500-second job. Long objects must come from a real regular sheet and finish over the whole interval. In 0.2.4 the long model is a traditional Miura-ori paper relief; the approved short bird remains stylized. See [origami references and geometric limits](origami-references.md). Earlier approvals below do not approve the complex bird's physical validity or timing.

Latest user feedback, 2026-10-25: the textured bird animation looks really good; start it with the paper flat. Paper Bird must begin as one flat, uncreased square and gather into the existing fold sequence. Preserve the approved material and finished shapes. This replaces its earlier disarranged opening; the other styles' beginning-to-end contract remains in effect.

User direction, 2026-10-25. This supersedes the visual-quality and duration-adaptation claims in the 0.2.1 implementation notes. That build establishes observation/rendering mechanics; its elementary artwork does not meet the requested standard. Render tests are not evidence of artistic quality.

## Readable completion

Each interval starts unresolved or disarranged and ends in a clearly finished composition. With countdown and percentage hidden, early, quarter-complete, half-complete and nearly finished states must be distinguishable. At 90%, the major form should read as almost complete, with visibly unfinished work remaining. Avoid withholding the recognizable form until the final instant.

Progress is the amount of meaningful construction completed, not a wandering marker or a decorative loop. Fine motion contributes to that construction. An endlessly repeating shuttle, crease shimmer, wobble or particle orbit cannot substitute for progress.

## Duration changes the construction

Latest user clarification: intervals **under five seconds have no animation**. Show static artwork with the ordinary playback controls/state. Five seconds is the inclusive threshold. This replaces the earlier continuous-texture treatment for sub-second loops, across all six styles. Silent chooser demonstrations remain independent of a running job's interval.

Choose model complexity and choreography automatically from the job duration. Do not simply stretch a five-second animation over two hours. These are illustrative durations, not interval presets or fixed implementation thresholds:

| Duration | Paper Bird construction |
| --- | --- |
| Five seconds | An elegant simple bird, resolved through a few broad, continuous folds. |
| Around a minute | A more developed origami bird with distinct body, neck, wings and tail; additional folds build those features. |
| Twenty minutes to two hours | An intricate bird with layered wings, detailed tail and many smaller crease, tuck and alignment operations. Small operations visibly advance while the larger silhouette gradually resolves. |

The finished bird itself may differ between duration ranges. All variants must have comparable visual quality. A short animation is simpler in construction, not cheaper in appearance. Longer constructions need enough distinct work to sustain visible change on a seconds scale; repeating a small set of motions for hours does not qualify. Do not add decorative feather lines to a flat silhouette and call that a complex fold sequence.

Retain a coherent completion measure across broad folds and fine operations. More substeps alone do not make 25% or 90% perceptually legible: distribute the construction so the visible unresolved portion reflects remaining time. Evaluate intermediate states, not only the attractive final frame.

Apply this principle to all six styles: progressively resolve strands into The Loom's woven form, arrange Magnetic Garden's elements into its flower, gather Ink Creature into its resolved form, assemble Constellation's geometry, and construct Paper Landscape's folded terrain. Longer versions need richer actual construction, not extra idle decoration. Exact choreography remains design work.

## Art direction

The chosen direction is atmospheric light and fluid motion. Paper Bird should have convincing folded surfaces, thickness cues, depth, occlusion, restrained translucency where appropriate, soft cast shadows and carefully composed lighting. Folds must read as surfaces moving in space rather than stick-art outlines or a fan of lines over a flat polygon. Use fluid, deliberate movement without sacrificing the recognizable folded-paper material.

Compose each scene for the actual small job card, light and dark themes, and several simultaneous jobs. Intricacy must remain legible at that size. More particles or more strokes are not a substitute for composition, material and motion quality.

## Runtime constraints

Whole construction and local motion derive from observed audio phase. No animation may schedule, delay or restart audio. Pause freezes construction; reattachment and global visual changes reconstruct the correct current state. Use deterministic construction from phase so returning to a card does not replay completed folds. Respect reduced motion with readable static progress states and accessible text. Avoid flashing or abrupt high-frequency resets for short loops.

## Review before calling it finished

Review a representative polished motion study at five seconds and a richer long-duration construction, including real-speed seconds-scale excerpts early, midway and late in the long interval. Accelerated playback alone cannot demonstrate suitable long-duration motion. Compare unlabeled 0%, 25%, 50%, 90% and complete frames at actual card size. Check that near-completion is obvious and that the short version looks equally considered.

Automated phase, pause and audio-independence tests remain necessary mechanical checks. They cannot establish sophistication, believable folds or perceived completion. The current APK has not passed this art review; no replacement artwork is claimed by this document.

## Implementation evidence — 0.2.2

The Paper Bird renderer now provides textured, shaded articulated surfaces with 1/5/12/24 wing pleats chosen from duration, and additional tail layers for longer birds. Material is original procedural grain/fibers, anchored by per-face texture coordinates. It is a stylized articulated sculpture; no physically verified single-sheet fold pattern is claimed. The other five styles still await artwork revision to this standard. Phase storyboards and real-time/accelerated motion studies exist locally under ignored `device-results/`; mechanics pass tests, while the user's assessment of the visual result is still pending. All styles are static below five seconds.

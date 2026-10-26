# Future animation ideas

Update — 0.3.2: Stained Glass has moved from this idea list into the app. See [actual scope](stained-glass.md); it has three original classic-style artworks and atmospheric haze. Future stained-glass variants include Gothic rose windows, dragonflies/peacocks, koi/water lilies and soft colored caustics. These additions and the other five candidates remain ideas. The original brainstorming record below is historical.

Saved from the user's accepted brainstorming on 2026-10-25. These are ideas to revisit, not approved implementation work or shipped visuals. Current selectable visuals remain Origami and Cube; Miura and the five retired styles stay removed. Hydrangea remains a separate unfinished candidate.

Every candidate must start visibly unfinished and resolve into an unmistakably complete object. Support seconds through hours by adding meaningful operations, not stretching one movement. Use convincing materials, atmospheric light and fluid, restrained motion. The unfinished portion should help distinguish roughly 25% from 90% completion at actual card size. Under-five-second intervals remain static, with accessible reduced-motion alternatives.

| Idea | Construction and satisfying endpoint | Duration adaptation |
| --- | --- | --- |
| Stained Glass | Colored pieces settle into a framed design; connecting seams close, then soft light illuminates the finished window. | A handful of large pieces for short intervals; detailed botanical sections for long ones. |
| Kintsugi Bowl | Ceramic fragments fit together, gold seams join them, and the complete bowl turns gently for inspection. | Few broad fragments versus many sections and intricate seam tracing. |
| Miniature Observatory | Stonework, stairs, brass instruments and a domed roof assemble into a small observatory; the dome finishes the object. | A simple pavilion versus detailed architecture, with completed floors and missing sections showing progress. |
| Wooden Puzzle | Grained wooden pieces rotate and slide into an interlocking sculpture; the final piece sits flush. | More intricate puzzles and assembly steps for longer intervals, with gentle inspection turns. |
| Sand Garden | A rake transforms rough sand into deliberate patterns around smooth stones; the last stroke completes the composition. | Broad curves versus distinct regions and finer patterns; unfinished sand remains visibly rough. |
| Marquetry | Walnut, maple and other veneers fit into a heron, botanical illustration or landscape. | A few broad shapes versus hundreds of inlays, with completed image area showing progress. |

Stained Glass and Kintsugi were the strongest suggested starting points, not a user-selected priority. Reference directions: [ustwo's Monument Valley art direction](https://ustwo.com/blog/monument-valley-out-now/) for architecture and [Nervous System's generative puzzles](https://n-e-r-v-o-u-s.com/projects/) for sophisticated piece shapes. References are inspiration, not permission to copy their artwork.

Runtime requirement: observe audio position and interval identity; stop visual construction/drawing and preparation workers when hidden or screen-off. Reconstruct the current pose directly on return rather than replaying missed steps. Keep all visual work outside the audio render path.

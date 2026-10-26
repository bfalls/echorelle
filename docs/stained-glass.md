# Stained glass — 0.3.2

The user selected stained glass with classic imagery, realistic materials, one arriving piece approximately every two or three seconds, duration-adaptive artwork and a faint atmospheric fog. This supersedes the earlier deferral of this specific idea. Other saved candidates remain deferred. Origami's user-reported flatness/disappearing folds are not considered resolved by its previous geometry tests; this change does not claim renewed origami art acceptance.

## Artwork and motion

Three original bitmap artworks use classic botanical/landscape motifs, not reproductions attributed to Tiffany or another historical maker:

| Interval | Artwork | Extracted source regions |
| --- | --- | ---: |
| Below three minutes | Magnolia: ivory blossom, sage leaves, smoky teal glass | 79 |
| Three to under fifteen minutes | Iris garden: violet/ivory flowers and turquoise water | 230 |
| Fifteen minutes and longer | River of light: magnolias, irises, river, mountains and sunrise | 264 |

These are the initial three works, one per duration tier, not a completed multi-work catalog for every tier. The source images contain rippled/mottled glass, opalescent color, dark leading and transmitted light. Enclosed regions are extracted offline from leading, grown through dark glass, and stored as paths. Path simplification and dark-glass ambiguity mean these are approximate image-derived boundaries, not a fabrication pattern or physical glass model.

The construction window ends five seconds early (halfway for five-to-ten-second intervals). Target arrivals are `round(constructionSeconds / 2.5)`, with at least one step and at most 34,560. Very short intervals group existing regions into larger sections, balanced by area. Longer intervals bisect the largest regions by actual polygon area to provide finer construction steps. Thus not every animation step corresponds to one original pane in the source image: short flights can contain several panes, and long flights can be subdivisions. At 60 seconds there are 22 flights; 600 seconds gives 238; 1,265 seconds gives 504; two hours gives 2,878. These average about 2.5 seconds per flight.

Exactly one current piece/section follows a gently curved trajectory from outside the canvas, banking slightly and settling into its matching position. Previously seated glass remains fixed. Source texture travels with the piece. Completed imagery and missing dark regions communicate construction progress. At completion, warm transmitted light increases gently; a flat window does not perform a fake whole-object inspection turn. A soft colored floor glow, restrained beams and four extremely faint haze layers supply atmosphere. Haze is clipped away from the actual artwork and moves only as a function of observed playback position. It is not a particle simulation or background effect worker.

Below five seconds the completed window is static. Reduced motion suppresses flights and haze drift, showing settled construction poses and a finished window during the reveal. Pause freezes glass and atmosphere. Selection remains in Home's global Visual chooser and uses the existing immediately persisted preference; it does not change audio phase. Existing Origami/Cube choices and saved preferences are preserved.

## Runtime and resources

No audio, permissions, alarms or wake-lock changes. All assets, segmentation and preparation are local/offline. Source decoding and cancellable topology/path preparation run on Dispatchers.Default only for visible, resumed, screen-on cards. Each activity caches at most the three immutable sources; each card owns its mutable bitmap and paths. Source images are 1024×683 pixels (about 2.67 MiB decoded apiece); each renderer adds one image-sized assembly bitmap. The six bundled WebP/JSON files total about 1.49 MiB. No downloaded museum photos or new dependencies are bundled.

Subdivision uses area bisection, including for concave outlines with zero-area bridges. Regression tests use all bundled contours through the maximum supported duration. Seeking chooses the present construction index directly, without replaying elapsed time or missed intervals. Fixed 256-piece bitmap batches plus a retained partial path keep antialiasing deterministic across different seek histories; the partial path contains at most 255 steps. Bitmap reconstruction still has a bounded cost on a long forward/backward seek, so measure and report it rather than claiming instant recovery on all hardware. At maximum duration individual pieces are very small at card size; perceptual legibility and art acceptance remain user-review questions.

## Source research and asset provenance

Museum references informed materials/leading, not shipped images: [The Met's Tiffany overview](https://www.metmuseum.org/essays/louis-comfort-tiffany-1848-1933), [Magnolias and Irises](https://www.metmuseum.org/art/collection/search/9819), [William Lightfoot Price's window](https://www.metmuseum.org/art/collection/search/14805). The Met API currently marks object 9819 as non-public-domain with no primary image, so it was not reused despite broader search-result claims. Three other verified public-domain museum photos were downloaded for research/review only, under ignored device-results; none is in the APK.

Final artwork was generated with the built-in image generation tool. See [asset files, prompts and processing](stained-glass-assets.md). Generated original PNGs are retained locally under ignored device-results/glass-research; optimized WebP and extracted outlines live in the app's assets. No artwork attribution to a historical designer is implied.

## Possible later additions

Not implemented: jewel-toned Gothic rose windows; Art Nouveau dragonflies or peacocks; koi and water lilies; subtle colored caustics cast onto nearby stone; variants of morning/late-afternoon transmitted light. Preserve the calm pace, visible unfinished areas and screen-off work suppression rather than adding perpetual independent movement.

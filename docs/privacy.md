# Privacy and local storage

Echorelle works offline. The manifest requests no network permission. There are no accounts, servers, analytics, ads, uploads or cloud sync.

Audio chosen with the system picker is copied into private app storage. Playback uses the private decoded copy, so removing/moving the original does not affect it. Imported names, exact frame counts, saved jobs, volume and global visual choice stay on the device. Android backup/device-transfer extraction is disabled. Uninstalling the app removes its private library; no export/backup product flow is provided yet.

Debug builds record local diagnostic journals containing sound names and job IDs, timing, playback commands, interruption reasons and sampled battery/power/screen/idle state. They contain no PCM or imported file content. Up to 20 app journals are retained; regular checkpoint growth is capped at 4 MiB per journal. Control events/final summaries can still be written after that checkpoint cap. Diagnostic paths and imported names may be private; do not commit or publish collected files. The collection script writes only to ignored `device-results/` by default and never uploads. Old probe evidence is preserved separately.

Use Remove import for an unused sound; Delete removes a job with a brief Undo period. Referenced imports remain until no saved/Undo-pending job uses them and you remove the sound. Corrupt library recovery documents are kept locally instead of silently discarding definitions or audio. App-private Android storage is relied upon; there is no separate application-level encryption or secure deletion guarantee.

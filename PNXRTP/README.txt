PNXRTP 1.3.0 - PowerNukkitX API 3.0.6

Target platform
- PowerNukkitX 3.0.6 API (server-local dependency and Java compiler settings remain unchanged).
- Build with the existing Maven project and local PowerNukkitX jar at ${user.home}/storage/downloads/powernukkitx.jar.

Language output
- Russian is the default for player and command responses.
- Append ENG to a command to receive English output, suitable for consoles that do not display Cyrillic correctly.
- Examples: /rtp status ENG, /rtpgen status ENG, /rtpgen diagnostics ENG, /rtpgen region square ENG.
- Without ENG, command responses remain Russian.

RTP
- Builds a per-world index from LevelDB VERSION records asynchronously.
- Selects only indexed saved chunks and calls loadChunk(x,z,false); it does not intentionally generate chunks for teleportation.
- Checks solid ground, two air blocks, and common hazardous block identifiers.
- Supports per-world enable flags, cooldowns, and player-avoidance distance through config.properties.
- Maintains a short per-player/per-world destination history with synchronized access.

Chunk generator
- Gradual spiral scan around the configured center; one generation request is submitted per eligible generator cycle.
- Supports circle and square regions. Circle is the default; square fills the configured radius-aligned square.
- Region center defaults to the selected world's spawn. A custom block-coordinate center can be configured with /rtpgen region <circle|square> <X> <Z>.
- /rtpgen preview displays the active region shape, radius, and center.
- /rtpgen status reports counters, current session completion rate, memory use, and queue observations.
- Uses PNX isChunkGenerating observations to track in-flight requests. Leaving the generation queue does NOT prove that LevelDB has flushed a chunk to disk.
- Memory policy defaults: slow down at 65%, auto-pause at 75%, auto-resume at 60%, stop at 85%. Thresholds are configurable in config.properties.
- Progress, configuration, and RTP history are written through temporary files and replace operations.
- Progress checkpoints are throttled by generator ticks and request count.
- Scheduler/runtime failures are journaled and stop generation safely. Individual chunk-request exceptions restore the spiral cursor for retry.
- Generation does not automatically start after a server restart. Run /rtpgen start to continue from the last saved cursor.

Administration and diagnostics
- /rtpgen diagnostics shows memory, task flag, pending count, index state, and data-file availability.
- /rtpgen history displays the last ten event-log entries.
- /rtpgen backup creates a timestamped backup of config, progress, RTP history, and events.log under the plugin data folder.
- /rtpgen map exports the saved-chunk index and currently pending chunk observations to a CSV file in the plugin data folder. This is a data export, not an in-game map renderer.
- events.log records plugin lifecycle and generator events.
- Memory reads cgroup v2/v1 when available; otherwise it falls back to JVM heap usage. The fallback is not total host/container memory.
- The index is a startup snapshot. Queue completion is not proof of durable disk save; pending entries are not serialized.

Install and validation
- Replace only the PNXRTP plugin jar after building and testing it.
- Do not replace the PowerNukkitX server jar.
- Keep a backup of the existing plugin jar and config before testing. Validate on a copy/staging server before production use.

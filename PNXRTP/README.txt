PNXRTP 1.2.0 - PowerNukkitX API 3.0.6

Target platform
- PowerNukkitX 3.0.6 API (the server-local dependency and Java compiler settings are unchanged).
- Build with the existing Maven project and local PowerNukkitX jar at ${user.home}/storage/downloads/powernukkitx.jar.

RTP
- Builds a per-world index from LevelDB VERSION records asynchronously.
- Selects only indexed saved chunks and calls loadChunk(x,z,false); it does not intentionally generate chunks for teleportation.
- Checks solid ground, two air blocks, and common hazardous block identifiers.
- Supports per-world enable flags, cooldowns, and player-avoidance distance through config.properties.
- Maintains a short per-player/per-world destination history with synchronized access.

Chunk generator
- Gradual spiral scan around the selected world's spawn; one generation request is submitted per eligible generator cycle.
- Uses PNX isChunkGenerating observations to track in-flight requests. This is not a durable completion callback: leaving the generation queue does NOT prove that LevelDB has flushed the chunk to disk.
- Memory policy defaults: slow down at 65%, auto-pause at 75%, auto-resume at 60%, stop at 85%. Pause, resume, and stop thresholds are configurable in config.properties using generator.memory.pause, generator.memory.resume, and generator.memory.stop.
- Progress, configuration, and RTP history are written through a temporary file and replace operation to reduce the chance of partially written state.
- The selected generator world, radius, and speed are persisted with the progress cursor.

Diagnostics and safety notes
- /rtp status shows the saved-chunk index state and memory reading.
- /rtpgen status shows generator state, counters, in-flight observations, and memory thresholds.
- events.log records plugin lifecycle and generator start/pause/resume/stop events.
- Memory reads cgroup v2/v1 when available; otherwise it falls back to JVM heap usage. The fallback is not total host/container memory.
- The index is a startup snapshot. Chunks generated after indexing are added to the in-memory index only when the plugin observes them leave the generation queue; queue completion is not proof of durable disk save.
- Progress does not serialize pending queue entries. After a server restart, the cursor resumes from the last saved point; a chunk that was in flight during shutdown may be encountered again and safely skipped if already indexed.

Install
- Replace only the PNXRTP plugin jar after building and testing it.
- Do not replace the PowerNukkitX server jar.
- Keep a backup of the existing plugin jar and config before testing. Validate on a copy/staging server before production use.

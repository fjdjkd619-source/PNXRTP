PNXRTP 1.4.0 - PowerNukkitX API 3.0.6

Target platform
- PowerNukkitX 3.0.6 API (server-local dependency and Java compiler settings remain unchanged).
- Build with the existing Maven project and local PowerNukkitX jar at ${user.home}/storage/downloads/powernukkitx.jar.

Language output
- Russian is the default for player and command responses.
- Append ENG to a command to receive English output, suitable for consoles that do not display Cyrillic correctly.
- Examples: /rtp status ENG, /rtp back ENG, /rtpgen status ENG, /rtpgen diagnostics ENG, /rtpgen dashboard ENG, /rtpgen region square ENG.
- Without ENG, command responses remain Russian.

RTP
- Builds a per-world index from LevelDB VERSION records asynchronously.
- Selects only indexed saved chunks and calls loadChunk(x,z,false); it does not intentionally generate chunks for teleportation.
- Checks solid ground, two air blocks, and common hazardous block identifiers.
- Supports per-world enable flags, cooldowns, player-avoidance distance, and minimum/maximum RTP distance through config.properties.
- Default RTP distance from the player's current position is 512-10000 blocks; configure rtp.min-distance and rtp.max-distance globally or world.<folder>.min-distance / world.<folder>.max-distance per world.
- /rtp back returns to the previous RTP destination in the current world history. It loads chunks with create=false and repeats safety checks before teleporting.
- Maintains a short per-player/per-world destination history with synchronized access.

Chunk generator
- Gradual spiral scan around the configured center; one generation request is submitted per eligible generator cycle.
- Supports circle and square regions. Circle is the default; square fills the configured radius-aligned square.
- Region center defaults to the selected world's spawn. A custom block-coordinate center can be configured with /rtpgen region <circle|square> <X> <Z>.
- /rtpgen preview displays the active region shape, radius, and center.
- Named regions: /rtpgen region add <name> <circle|square> <centerX> <centerZ> <radius>, /rtpgen region list, /rtpgen region use <name>, /rtpgen region remove <name>. Once a named region is selected, the generator advances to the next saved region after completing the current region. Rotation order follows the saved profile list; after one complete pass it stops. Use /rtpgen reset to clear completed-region state and start the saved sequence again.
- /rtpgen exclude <rtp|generator> add <x1> <z1> <x2> <z2> adds a rectangular exclusion zone in block coordinates; use /rtpgen exclude <rtp|generator> clear to clear global exclusions.
- /rtpgen status reports counters, current session completion rate, memory use, and queue observations.
- Uses PNX isChunkGenerating observations to track in-flight requests separately for each world. Leaving the generation queue does NOT prove that LevelDB has flushed a chunk to disk.
- Memory policy defaults: slow down at 65%, auto-pause at 75%, auto-resume at 60%, stop at 85%. Thresholds are configurable in config.properties.
- TPS-aware control reads PowerNukkitX average TPS and tick usage. It throttles generation below generator.tps.throttle-below (default 18 TPS), pauses below generator.tps.pause-below (default 14 TPS), and resumes above generator.tps.resume-above (default 18 TPS). Hysteresis prevents rapid pause/resume oscillation.
- Daily schedule: /rtpgen schedule <HH:mm> <HH:mm> or /rtpgen schedule off. The schedule uses the server JVM's local timezone and supports windows crossing midnight.
- Optional hourly generation request cap: generator.max-requests-per-hour; 0 disables the cap. The counter resets hourly in memory and is not persisted across restarts.
- Progress, configuration, and RTP history are written through temporary files and replace operations.
- Progress checkpoints are throttled by generator ticks and request count.
- Scheduler/runtime failures are journaled and stop generation safely. Individual chunk-request exceptions restore the spiral cursor for retry.
- Automatic continuation after restart is optional: /rtpgen autostart on|off. It is disabled by default. When enabled, the plugin attempts to start the selected world's generator using the saved progress during plugin enable. Run /rtpgen start manually if the selected world is unavailable during startup.

Administration and diagnostics
- /rtpgen diagnostics shows memory, task flag, pending count, index state, and data-file availability.
- /rtpgen dashboard combines generator status, diagnostics, and performance metrics in one admin view.
- /rtpgen performance shows average TPS, tick usage, memory, pending queue size, and hourly quota.
- /rtpgen validate checks basic configuration ranges and schedule syntax.
- /rtpgen biome <ID|here|off> selects an exact numeric biome ID for RTP; here reads the biome at the admin's current position; -1/off allows any biome. Per-world override: world.<folder>.target-biome-id.
- /rtpgen autostart on|off controls optional generator continuation after a server restart; it is disabled by default.
- /rtpgen map preview renders a 21x21 text map around the active generation center (# saved, + pending, . not indexed). /rtpgen map still exports CSV.
- The generator now reduces its effective request rate when the observed in-flight chunk queue grows (12+ pending: cap speed at 2; 24+: cap at 1), in addition to the existing memory-based slowdown.
- /rtpgen history displays the last ten event-log entries.
- /rtpgen backup creates a timestamped backup of config, progress, RTP history, and events.log under the plugin data folder.
- /rtpgen map exports the saved-chunk index and currently pending chunk observations to a CSV file in the plugin data folder. This is a data export, not an in-game map renderer.
- events.log records plugin lifecycle and generator events.
- Memory reads cgroup v2/v1 when available; otherwise it falls back to JVM heap usage. The fallback is not total host/container memory.
- The index is a startup snapshot. Queue completion is not proof of durable disk save; pending entries are not serialized.
- Biome filtering uses the numeric biome ID returned by Level.getBiomeId(x,y,z); biome names are not resolved by this command.
- Exclusion zones are rectangular block-coordinate regions stored in config.properties. Global keys are rtp.exclusions and generator.exclusions; RTP supports world.<folder>.rtp-exclusions overrides.
- Named region profiles are stored in generator.regions. The active profile is stored in generator.region.active.

Install and validation
- Replace only the PNXRTP plugin jar after building and testing it.
- New config keys are created with defaults on first launch; existing config.properties files are retained and receive missing defaults.
- Do not replace the PowerNukkitX server jar.
- Keep a backup of the existing plugin jar and config before testing. Validate on a copy/staging server before production use.

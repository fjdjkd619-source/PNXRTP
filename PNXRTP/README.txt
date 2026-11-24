PNXRTP 1.1.0 - PowerNukkitX API 3.0.6

This source targets the PowerNukkitX 3.0.6 API (commit c574993).
Build it inside the existing Maven project using the same PowerNukkitX dependency/systemPath configuration that was used for PNXRTP 1.0.0.

Important implementation notes:
- RTP builds a per-world index from LevelDB VERSION records asynchronously.
- RTP only selects indexed chunks and calls loadChunk(x,z,false), so it does not intentionally create chunks.
- The index is a startup snapshot. Chunks generated after the scan are not automatically inserted into that snapshot in this version; restart or re-indexing is needed before RTP can select them.
- The generator sends one request at a conservative interval and checks isChunkGenerating before/after. PNX does not expose a per-request completion callback through the API used here, so counters are labeled as attempts/queue acceptance rather than confirmed completed chunks.
- Memory uses cgroup v2/v1 when visible, otherwise falls back to JVM heap. It slows at 65%, pauses at 75%, and stops at 85%.
- Generator world is currently the server default world. Per-world enable flags and RTP settings can be added in config.properties as world.<folder>.<setting>.

Do not replace the server jar. Only replace the plugin source/resource and rebuild PNXRTP.

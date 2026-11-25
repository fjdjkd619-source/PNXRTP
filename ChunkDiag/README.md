# ChunkDiag 1.0.0

Read-only diagnostic plugin for PowerNukkitX 3.0.6 / API 3.0.6.

## Commands
- `/chunkdiag here` — inspect the player's currently loaded chunk.
- `/chunkdiag area` — inspect already-loaded chunks in the protected 21×21 region (chunk X -12..8, Z -8..12).

The area scan uses `Level#getChunkIfLoaded` through reflection. It does not request chunk loading, alter chunk flags, write LevelDB records, or modify world data. Unloaded chunks are skipped.

The report is written to `plugins/ChunkDiag/chunkdiag-report.txt`.

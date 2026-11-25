# TorchClean 0.1.0

PowerNukkitX 3.0.6 plugin for a strictly bounded torch scan/removal in the configured 441-chunk area.

## Fixed bounds

- World: `world`
- Chunk X: `-12..8`
- Chunk Z: `-8..12`
- Exactly 441 chunk coordinates

## Target blocks

- `minecraft:torch` (standing and wall-mounted)
- `minecraft:soul_torch` (standing and wall-mounted)
- `minecraft:copper_torch` (standing and wall-mounted)

Redstone torches are intentionally excluded because they are functional circuit components. All other blocks are excluded.

## Commands

- `/torchclean scan` — read-only scan; no block changes.
- `/torchclean remove` — allowed only after a complete successful scan in the current plugin session; removes only target blocks.
- `/torchclean status`
- `/torchclean stop`

Removal is rate-limited to 12 blocks per server tick. Each modified chunk is explicitly saved. Only already-existing generated chunks are read; no chunk generation is requested. Chunks loaded by the plugin are safely unloaded when possible.

Report: `plugins/TorchClean/torchclean-report.txt`.

## Safety notes

- No automatic work on plugin enable.
- No edits outside the fixed chunk bounds.
- No block layer 1 edits.
- A backup of the world is still recommended before running `remove`.

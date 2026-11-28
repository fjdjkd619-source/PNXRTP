# PNX–Terra Fantasy Content Overlay (Stage 49)

This directory is an additive overlay for the pinned TerraOverworldConfig revision:

- Terra ConfigPack: `PolyhedralDev/TerraOverworldConfig@445848bc9f9f28fab1d760f01f9a552d93944681`
- Terra runtime: 6.6.1 (pinned commit in the R15 build workflow)
- PNX block registry: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`

## Included feature families

- Rare high-altitude floating rock islands.
- Underground amethyst/calcite crystal pockets.
- Sparse surface ruined stone watchtowers.

The structures use finite, statically analyzable TerraScript write envelopes. The Stage 48 bounds generator must certify each script before the corresponding feature can be enabled. Unverified roots are intentionally rejected by the runtime guard.

## Apply

Run `apply-fantasy-pack.sh --terra-config PATH` from this directory's parent candidate checkout. The script copies the feature and structure assets into the pinned ConfigPack and adds the feature IDs to the abstract base biome stages.

This is an initial content slice, not the complete Stage 49 scope. Castles, villages, roads, bridges, waterfalls, large cave complexes, ancient cities and sanctuary networks remain to be implemented and validated.

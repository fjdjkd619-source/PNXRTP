# PNX–Terra Fantasy Content Overlay (Stage 49)

Additive overlay for the pinned TerraOverworldConfig revision:

- ConfigPack: `PolyhedralDev/TerraOverworldConfig@445848bc9f9f28fab1d760f01f9a552d93944681`
- Terra runtime: 6.6.1
- PNX block registry: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`

## Included procedural features

- Rare high-altitude floating rock islands.
- Underground amethyst/calcite crystal pockets.
- Sparse ruined stone watchtowers.
- Rare castle keeps.
- Small timber homesteads.
- Underground crypt chambers.
- Ancient stone sanctuaries with pillars and central altars.
- Stone bridges spanning water channels.
- Larger underground halls and chamber complexes.

Every structure is a TerraScript with a finite coordinate envelope. The Stage 48 generator statically analyzes the script and admits only profiles that pass its finite bounds proof. Unverified structures remain denied by the adapter.

## Apply

Run `content-pack/apply-fantasy-pack.sh --terra-config PATH` from the candidate checkout. It copies the feature and structure assets into the pinned ConfigPack and registers the features in the abstract base biome stages. The operation is idempotent.

## Remaining Stage 49 content

This is a partial content slice, not a completed fantasy world pack. Still to implement and validate: full castles/fortress compounds, villages, road networks, bridges, waterfalls, large cave systems, underground rivers/lakes, ancient cities, temples, sanctuaries and rare landmark combinations. The pack must also be loaded in the pinned Terra runtime and checked across representative seeds before Stage 49 acceptance.

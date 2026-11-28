# Terra structure BlockEntity capture audit — 2026-11-28

Pinned Terra source: `PolyhedralDev/Terra@05ddc8b8a66e17412670647cce78ef837b091256`  
Pinned PNX source: `PowerNukkitX/PowerNukkitX@c57499365737654eca32df008751ef6bca5af142`

## TerraScript call paths confirmed

### `state`

`StateFunction` parses the supplied state string with `SerialState.parse`, calls `ServerWorld.getBlockEntity(x,y,z)`, invokes `BlockEntity.applyState(state)`, then calls `update(false)`.

The Bukkit implementation confirms two concrete state schemas:
- `Sign`: `text0`, `text1`, `text2`, `text3` set the four front-side lines.
- `MobSpawner`: `type`, `delay`, `min_delay`, `max_delay`, `spawn_count`, `spawn_range`, `max_nearby`, and `required_player_range` map to the Terra `MobSpawner` methods.

The earlier adapter's generic `BlockEntity` wrapper did not implement the Terra `Sign` or `MobSpawner` subinterfaces. Merely overriding the default `applyState` method would not have made those TerraScript functions work.

### `loot`

`LootFunction` checks whether `getBlockEntity` returns a Terra `Container`, obtains its `Inventory`, fires the loot-table event, fills that inventory, and calls `update(false)`. A read-only BlockEntity wrapper cannot support this path.

## r8 staged capture implementation

- `PNXServerWorld.getBlockEntity` now checks the active placement's staged block cache. It only creates mutable staged views for supported PNX `BlockEntityHolder` blocks inside an active placement transaction.
- `PNXStagedSignDelegate` implements Terra `Sign`, maps `text0..text3` to PNX front-side sign NBT, and records an immutable `BlockEntityIntent` on `update(false)`.
- `PNXStagedMobSpawnerDelegate` implements Terra `MobSpawner` and maps the exact Bukkit/Terra state property names into PNX spawner NBT. Entity identifiers must resolve in the pinned PNX entity registry.
- `PNXStagedContainerDelegate` implements Terra `Container`; `PNXStagedInventory` stages PNX Item values without registering a live inventory. `ItemHelper.write` serializes populated slots into the PNX `Items` list.
- `PNXServerWorld.PlacementTransaction` now holds BlockEntity intents alongside block writes and physics intents. Nested transaction commit merges these intents; abort discards them. The root commit writes them through `PnxPlacementCommitter` into the existing durable journal/projection path.
- The staged delegates are detached; no PNX BlockEntity is created during Terra population. Live creation remains in the finalizer after all intents pass preflight.

## PNX-specific durability hardening

- The pinned `BlockEntityMobSpawner` stores its delay in an in-memory field but did not read/write that value in NBT. `22-stage22-pnx-spawner-delay.patch` adds a short `Delay` field to `loadNBT` and `saveNBT`.
- `PnxBlockEntityNbtCodec` adds `PNXTerraIntentHash`, a SHA-256 marker over the normalized source payload. PNX BlockEntity constructors may add default fields, so byte-for-byte comparison of the post-construction NBT is not a valid replay test. The marker is preserved while PNX normalizes other fields.
- `PnxBlockEntityIntentApplier` validates marker, canonical registry ID, coordinates, target chunk, and world height before construction. A matching marker is replay-safe; a conflicting existing entity is rejected.
- PNX's factory may swallow a subclass-constructor exception after the base constructor has registered the object. The applier snapshots `chunk.getBlockEntities()` and closes every newly registered entity if creation fails.
- The coordinator applies BE/physics intents through the owning Level scheduler. If a batch contains BlockEntities, it durably saves the chunk in that same scheduler task before the next scheduler heartbeat can tick a newly created spawner. If that early save fails, newly created entities are closed before the task returns. The normal finalization save/acknowledgement still follows.

## Contract tests added

The Java 21 narrow contract harness now covers:
- NBT identity, coordinates, hash marker, source-tag immutability, hash/coordinate drift, and trailing bytes.
- Sign front text line mapping and `update(false)` journal-intent creation.
- Spawner entity registry mapping and all documented Terra spawner properties, including `Delay`.
- Container inventory slot staging and PNX `Items` list serialization.
- Same-marker replay idempotency, type collision refusal, and rollback after a PNX-like factory swallows a constructor exception after base registration.
- Level-scheduler dispatch and rollback when the early durable save fails.

These tests use narrow API stubs. They are not a full PNX/Terra dependency build or a Bedrock runtime test.

## Explicit limitations / remaining acceptance gates

- Only PNX `BlockEntityHolder` types with an implemented staged view are mutable: sign, mob spawner, and recognized `BlockEntityInventoryHolder` container classes. Unknown BlockEntity types fail closed.
- Container inventory capacities are mapped by known PNX class names (chest/barrel/shulker, dispenser/dropper/crafter, hopper, brewing stand, furnace/smoker). An unrecognized inventory-holder class fails closed rather than guessing a size.
- Item conversion currently requires Terra `ItemStack.getHandle()` to be a PNX `Item`; non-PNX/custom item handles fail closed. Verify metadata/enchantment fidelity in the full upstream build/runtime.
- A block entity must be in the active placement's staged block cache for the mutable staged wrapper path. Existing live BlockEntities outside a staged placement remain read-only.
- Full adapter compilation against the pinned upstream APIs, PNX core patch compilation, structure fixtures using TerraScript `state` and `loot`, Bedrock save/restart verification, and crash-window replay tests are still required.
- No production server or existing world has been modified.

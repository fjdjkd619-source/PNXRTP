#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/main/java/org/powernukkitx/entity"
cat > "$TMP/src/main/java/org/powernukkitx/entity/Entity.java" <<'JAVA'
public abstract class Entity extends Location implements Metadatable, EntityID {
    public static final Entity[] EMPTY_ARRAY = new Entity[0];
    protected final ActorDataMap actorDataMap = new ActorDataMap();
    public static AtomicLong entityCount = new AtomicLong(1);
    public final List<Entity> passengers = new ArrayList<>();
    protected final AxisAlignedBB offsetBoundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);

    protected String name;
    private volatile boolean initialized;
    protected volatile boolean saveWithChunk = true;

    @Nullable
    public static Entity createEntity(@NotNull String identifier, @NotNull IChunk chunk, @NotNull CompoundTag nbt, @Nullable Object... args) {
        Identifier.assertValid(identifier);
        return Registries.ENTITY.provideEntity(identifier, chunk, nbt, args);
    }

    protected final void init(IChunk chunk, CompoundTag nbt) {
        if ((chunk == null || chunk.getProvider() == null)) {
            throw new ChunkException("Invalid garbage Chunk given to Entity");
        }
        this.id = Entity.entityCount.getAndIncrement();
        this.isPlayer = this instanceof Player;
        this.temporalVector = new Vector3();
        this.justCreated = true;
        this.nbt = nbt;

        this.setMotion(this.temporalVector.setComponents(0, 0, 0));
        try {
            this.initEntity();
            if (this.initialized) {
                // We've already initialized this entity
                return;
            }
            this.initialized = true;

            this.chunk.addEntity(this);
            this.level.addEntity(this);

            EntitySpawnEvent event = new EntitySpawnEvent(this);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.close(false);
            } else {
                this.scheduleUpdate();
                this.lastUpdate = this.level.getTick();
            }
        } catch (Exception e) {
            this.close(false);
            throw e;
        }
    }

    public Set<String> typeFamily() {
        return Set.of();
    }

    public void close() {
        close(true);
    }

    private void close(boolean despawn) {
        if (!this.closed) {
            this.closed = true;
        }
    }
}
JAVA
cd "$TMP"
git init -q
git add .
git -c user.email=bot@example.invalid -c user.name=fixture commit -qm fixture
git apply --check "$ROOT/patches/35-pnx-entity-rollback-api.patch"
git apply "$ROOT/patches/35-pnx-entity-rollback-api.patch"
git apply --check "$ROOT/patches/36-pnx-deferred-entity-api.patch"
git apply "$ROOT/patches/36-pnx-deferred-entity-api.patch"
git diff --check
echo 'PNX rollback + deferred lifecycle API patch context: PASS'

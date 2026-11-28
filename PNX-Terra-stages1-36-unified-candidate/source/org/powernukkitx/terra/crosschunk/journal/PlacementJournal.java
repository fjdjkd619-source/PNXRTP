package org.powernukkitx.terra.crosschunk.journal;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32C;

import static java.nio.file.StandardOpenOption.*;

/**
 * Append-only, fsync-before-return WAL. Invalid/torn tail is truncated to the last verified frame.
 * Frame: magic, payload length, payload, CRC32C(payload). All values are explicit and versioned.
 */
public final class PlacementJournal implements AutoCloseable {
    private static final int MAGIC = 0x50584A31; // PXJ1
    private static final int VERSION = 4;
    private static final int HEADER_BYTES = 8;
    private static final int TRAILER_BYTES = 4;
    private static final int MAX_FRAME = 64 * 1024 * 1024;
    private static final byte PLACEMENT = 1, ANCHOR_COMPLETE = 2, ANCHOR_STARTED = 3, CHUNK_APPLIED = 4;

    private final Path path;
    private final FileChannel channel;
    private final FileLock processLock;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<JournalEvent> events = new ArrayList<>();
    private final Map<String, PlacementRecord> placements = new HashMap<>();
    private final Map<java.util.UUID, String> entitySpawnOwners = new HashMap<>();
    private final Map<ChunkPos, Set<String>> placementsByTarget = new HashMap<>();
    private final Set<AnchorId> completedAnchors = new HashSet<>();
    private final Set<AnchorId> startedAnchors = new HashSet<>();
    private final Map<ChunkId, String> appliedDigests = new HashMap<>();
    private final Map<String, Long> placementSequences = new HashMap<>();
    private final Map<AnchorId, Long> startedAnchorSequences = new HashMap<>();
    private final Map<AnchorId, Long> completedAnchorSequences = new HashMap<>();
    private final Map<ChunkId, Long> appliedSequences = new HashMap<>();
    private long nextSequence = 1;
    private long recoveryDurationNanos;
    private long recoveredTailBytes;
    private boolean closed;

    public PlacementJournal(Path path) throws IOException {
        this.path = Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        channel = FileChannel.open(path, CREATE, READ, WRITE);
        try {
            processLock = channel.tryLock();
        } catch (OverlappingFileLockException alreadyOpen) {
            channel.close();
            throw new IOException("Placement journal is already open in this process: " + path, alreadyOpen);
        }
        if (processLock == null) {
            channel.close();
            throw new IOException("Placement journal is locked by another process: " + path);
        }
        try {
            recover();
            channel.position(channel.size());
        } catch (IOException | RuntimeException failure) {
            try { processLock.release(); } finally { channel.close(); }
            throw failure;
        }
    }

    public long appendPlacement(PlacementRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        lock.lock();
        try {
            String id = record.key().stableId();
            PlacementRecord old = placements.get(id);
            if (old != null) {
                if (sameRecord(old, record)) return sequenceOfPlacement(id);
                throw new IOException("Placement key collision with different payload: " + id);
            }
            validateEntitySpawnOwnership(id, record);
            long sequence = nextSequence;
            byte[] payload = encodePlacement(sequence, record);
            appendFrame(payload); // durable before visible in memory / return
            events.add(new JournalEvent.PlacementCommitted(sequence, record));
            placements.put(id, record);
            placementSequences.put(id, sequence);
            record.entitySpawns().forEach(spawn -> entitySpawnOwners.put(spawn.stableUuid(), id));
            indexPlacement(record);
            nextSequence++;
            return sequence;
        } finally { lock.unlock(); }
    }

    public long appendAnchorStarted(String worldKey, ChunkPos anchor) throws IOException {
        Objects.requireNonNull(worldKey, "worldKey"); Objects.requireNonNull(anchor, "anchor");
        if (worldKey.isBlank()) throw new IllegalArgumentException("worldKey is blank");
        lock.lock();
        try {
            AnchorId id = new AnchorId(worldKey, anchor);
            if (startedAnchors.contains(id)) return sequenceOfAnchorStarted(id);
            long sequence = nextSequence;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION); out.writeByte(ANCHOR_STARTED); out.writeLong(sequence);
                writeString(out, worldKey); out.writeInt(anchor.x()); out.writeInt(anchor.z());
            }
            appendFrame(bytes.toByteArray());
            events.add(new JournalEvent.AnchorStarted(sequence, worldKey, anchor));
            startedAnchors.add(id); startedAnchorSequences.put(id, sequence); nextSequence++;
            return sequence;
        } finally { lock.unlock(); }
    }

    /** Append a durable target acknowledgement only if the projection is still current. */
    public long appendChunkApplied(String worldKey, ChunkPos target, String digest) throws IOException {
        Objects.requireNonNull(worldKey, "worldKey"); Objects.requireNonNull(target, "target");
        if (worldKey.isBlank() || digest == null || !digest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid applied-chunk identity/digest");
        lock.lock();
        try {
            String current = PlacementDigest.compute(placementsForUnsafe(target), target);
            if (!current.equals(digest)) throw new IOException("Projection changed before applied acknowledgement for " + target);
            ChunkId id = new ChunkId(worldKey, target);
            if (digest.equals(appliedDigests.get(id))) return sequenceOfApplied(id, digest);
            long sequence = nextSequence;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION); out.writeByte(CHUNK_APPLIED); out.writeLong(sequence);
                writeString(out, worldKey); out.writeInt(target.x()); out.writeInt(target.z()); writeString(out, digest);
            }
            appendFrame(bytes.toByteArray());
            events.add(new JournalEvent.ChunkApplied(sequence, worldKey, target, digest));
            appliedDigests.put(id, digest); appliedSequences.put(id, sequence); nextSequence++;
            return sequence;
        } finally { lock.unlock(); }
    }

    public boolean isAnchorStarted(String worldKey, ChunkPos anchor) {
        lock.lock(); try { return startedAnchors.contains(new AnchorId(worldKey, anchor)); }
        finally { lock.unlock(); }
    }

    public boolean isChunkApplied(String worldKey, ChunkPos target, String digest) {
        lock.lock(); try { return digest.equals(appliedDigests.get(new ChunkId(worldKey, target))); }
        finally { lock.unlock(); }
    }

    public Set<ChunkPos> incompleteAnchors(String worldKey) {
        lock.lock(); try {
            TreeSet<ChunkPos> result = new TreeSet<>();
            for (AnchorId id : startedAnchors) if (id.worldKey.equals(worldKey) && !completedAnchors.contains(id)) result.add(id.anchor);
            return Collections.unmodifiableSet(result);
        } finally { lock.unlock(); }
    }

    public long appendAnchorCompleted(String worldKey, ChunkPos anchor) throws IOException {
        Objects.requireNonNull(worldKey, "worldKey"); Objects.requireNonNull(anchor, "anchor");
        if (worldKey.isBlank()) throw new IllegalArgumentException("worldKey is blank");
        lock.lock();
        try {
            AnchorId id = new AnchorId(worldKey, anchor);
            if (completedAnchors.contains(id)) return sequenceOfAnchor(id);
            long sequence = nextSequence;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION); out.writeByte(ANCHOR_COMPLETE); out.writeLong(sequence);
                writeString(out, worldKey); out.writeInt(anchor.x()); out.writeInt(anchor.z());
            }
            appendFrame(bytes.toByteArray());
            events.add(new JournalEvent.AnchorCompleted(sequence, worldKey, anchor));
            completedAnchors.add(id); completedAnchorSequences.put(id, sequence); nextSequence++;
            return sequence;
        } finally { lock.unlock(); }
    }

    public List<JournalEvent> events() {
        lock.lock(); try { return List.copyOf(events); } finally { lock.unlock(); }
    }
    public List<PlacementRecord> placementsFor(ChunkPos target) {
        lock.lock(); try { return placementsForUnsafe(target); }
        finally { lock.unlock(); }
    }

    private List<PlacementRecord> placementsForUnsafe(ChunkPos target) {
        Set<String> ids = placementsByTarget.get(target);
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream().map(placements::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(p -> p.key().stableId())).toList();
    }

    private void validateEntitySpawnOwnership(String placementId, PlacementRecord record) throws IOException {
        for (EntitySpawnIntent spawn : record.entitySpawns()) {
            String owner = entitySpawnOwners.get(spawn.stableUuid());
            if (owner != null && !owner.equals(placementId))
                throw new IOException("Entity UUID is already owned by another placement: " + spawn.stableUuid());
        }
    }

    private void indexPlacement(PlacementRecord record) {
        String id = record.key().stableId();
        for (ChunkPos target : record.targetChunks()) {
            placementsByTarget.computeIfAbsent(target, ignored -> new HashSet<>()).add(id);
        }
    }
    public boolean isAnchorCompleted(String worldKey, ChunkPos anchor) {
        lock.lock(); try { return completedAnchors.contains(new AnchorId(worldKey, anchor)); }
        finally { lock.unlock(); }
    }
    public long lastSequence() { lock.lock(); try { return nextSequence - 1; } finally { lock.unlock(); } }
    public Path path() { return path; }

    private void appendFrame(byte[] payload) throws IOException {
        ensureOpen();
        if (payload.length > MAX_FRAME) throw new IOException("Journal frame exceeds maximum size");
        CRC32C crc = new CRC32C(); crc.update(payload, 0, payload.length);
        ByteBuffer frame = ByteBuffer.allocate(HEADER_BYTES + payload.length + TRAILER_BYTES);
        frame.putInt(MAGIC).putInt(payload.length).put(payload).putInt((int) crc.getValue()).flip();
        long start = channel.size();
        channel.position(start);
        try {
            while (frame.hasRemaining()) channel.write(frame);
            channel.force(true);
        } catch (IOException failure) {
            try {
                channel.truncate(start);
                channel.force(true);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    /** Only a provably incomplete final write may be truncated; complete corruption fails closed. */
    private void recover() throws IOException {
        lock.lock();
        long startedAt = System.nanoTime();
        try {
            long size = channel.size(), offset = 0, validEnd = 0;
            boolean incompleteTail = false;
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
            while (offset < size) {
                long remaining = size - offset;
                if (remaining < HEADER_BYTES) {
                    if (isPartialMagicPrefix(offset, (int) remaining)) {
                        incompleteTail = true;
                        break;
                    }
                    throw corruption(offset, "incomplete bytes do not match frame magic");
                }
                header.clear();
                if (!readFully(channel, header, offset)) throw corruption(offset, "incomplete frame header");
                header.flip();
                int magic = header.getInt(), length = header.getInt();
                if (magic != MAGIC) throw corruption(offset, "invalid frame magic");
                if (length < 10 || length > MAX_FRAME) throw corruption(offset, "invalid complete frame length " + length);
                long frameBytes = HEADER_BYTES + (long) length + TRAILER_BYTES;
                if (frameBytes > remaining) {
                    incompleteTail = true;
                    break;
                }
                byte[] payload = new byte[length];
                if (!readFully(channel, ByteBuffer.wrap(payload), offset + HEADER_BYTES))
                    throw corruption(offset, "could not read complete payload");
                ByteBuffer crcBuffer = ByteBuffer.allocate(TRAILER_BYTES);
                if (!readFully(channel, crcBuffer, offset + HEADER_BYTES + (long) length))
                    throw corruption(offset, "could not read complete checksum");
                crcBuffer.flip();
                CRC32C crc = new CRC32C();
                crc.update(payload, 0, payload.length);
                if ((int) crc.getValue() != crcBuffer.getInt())
                    throw corruption(offset, "CRC32C mismatch in complete frame");
                JournalEvent event;
                try { event = decode(payload); }
                catch (RuntimeException | IOException malformed) {
                    throw new IOException("Corrupt complete WAL event at offset " + offset, malformed);
                }
                if (eventSequence(event) != nextSequence)
                    throw corruption(offset, "unexpected sequence " + eventSequence(event) + ", expected " + nextSequence);
                acceptRecovered(event);
                nextSequence++;
                offset += frameBytes;
                validEnd = offset;
            }
            if (incompleteTail) {
                recoveredTailBytes = size - validEnd;
                preserveTornTail(validEnd, size);
                channel.truncate(validEnd);
                channel.force(true);
            } else if (validEnd != size) {
                throw corruption(validEnd, "unclassified trailing bytes");
            }
        } finally {
            recoveryDurationNanos = System.nanoTime() - startedAt;
            lock.unlock();
        }
    }

    private boolean isPartialMagicPrefix(long offset, int length) throws IOException {
        if (length < 1 || length >= Integer.BYTES) return false;
        ByteBuffer prefix = ByteBuffer.allocate(length);
        if (!readFully(channel, prefix, offset)) return false;
        prefix.flip();
        for (int i = 0; i < length; i++) {
            int expected = (MAGIC >>> (24 - i * 8)) & 0xff;
            if ((prefix.get() & 0xff) != expected) return false;
        }
        return true;
    }

    private IOException corruption(long offset, String reason) {
        return new IOException("Placement WAL corruption at offset " + offset + ": " + reason
                + "; original WAL left untouched: " + path);
    }

    private void preserveTornTail(long start, long end) throws IOException {
        String base = path.getFileName() + ".torn-" + start;
        IOException collision = null;
        for (int suffix = 0; suffix < 10_000; suffix++) {
            Path evidence = path.resolveSibling(suffix == 0 ? base : base + "-" + suffix);
            try (FileChannel output = FileChannel.open(evidence, CREATE_NEW, WRITE)) {
                long position = start;
                ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
                while (position < end) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), end - position));
                    int n = channel.read(buffer, position);
                    if (n < 0) throw new EOFException("WAL shortened while preserving tail");
                    if (n == 0) { Thread.onSpinWait(); continue; }
                    buffer.flip();
                    while (buffer.hasRemaining()) output.write(buffer);
                    position += n;
                }
                output.force(true);
                return;
            } catch (FileAlreadyExistsException exists) {
                collision = new IOException("Evidence file already exists: " + evidence, exists);
            }
        }
        throw new IOException("Unable to create collision-safe torn-tail evidence", collision);
    }

    private void acceptRecovered(JournalEvent event) throws IOException {
        if (event instanceof JournalEvent.PlacementCommitted p) {
            String id = p.record().key().stableId();
            PlacementRecord previous = placements.putIfAbsent(id, p.record());
            if (previous != null && !sameRecord(previous, p.record()))
                throw new IOException("Duplicate placement key collision in WAL");
            if (previous == null) {
                validateEntitySpawnOwnership(id, p.record());
                p.record().entitySpawns().forEach(spawn -> entitySpawnOwners.put(spawn.stableUuid(), id));
                indexPlacement(p.record());
            }
            placementSequences.putIfAbsent(id, p.sequence());
        } else if (event instanceof JournalEvent.AnchorStarted a) {
            AnchorId id = new AnchorId(a.worldKey(), a.anchor());
            startedAnchors.add(id);
            startedAnchorSequences.putIfAbsent(id, a.sequence());
        } else if (event instanceof JournalEvent.AnchorCompleted a) {
            AnchorId id = new AnchorId(a.worldKey(), a.anchor());
            startedAnchors.add(id);
            completedAnchors.add(id);
            completedAnchorSequences.put(id, a.sequence());
        } else if (event instanceof JournalEvent.ChunkApplied a) {
            ChunkId id = new ChunkId(a.worldKey(), a.target());
            appliedDigests.put(id, a.digest());
            appliedSequences.put(id, a.sequence());
        }
        events.add(event);
    }

    private JournalEvent decode(byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readUnsignedByte(), type = in.readUnsignedByte(); long seq = in.readLong();
            if (version < 1 || version > VERSION) throw new IOException("Unsupported journal version " + version);
            JournalEvent result;
            if (type == PLACEMENT) {
                PlacementKey key = new PlacementKey(readString(in), in.readLong(),
                        new ChunkPos(in.readInt(), in.readInt()), readString(in), readString(in),
                        in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readLong());
                int count = in.readInt(); if (count < 0 || count > 2_000_000) throw new IOException("Invalid write count");
                List<BlockWrite> writes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int x=in.readInt(), y=in.readInt(), z=in.readInt(), layer=in.readUnsignedByte(), n=in.readInt();
                    if (n < 1 || n > 65_536) throw new IOException("Invalid state payload size");
                    byte[] state = in.readNBytes(n); if (state.length != n) throw new EOFException();
                    writes.add(new BlockWrite(x,y,z,layer,state));
                }
                List<BlockEntityIntent> blockEntities = new ArrayList<>();
                List<PhysicsIntent> physics = new ArrayList<>();
                if (version >= 3) {
                    int entityCount = in.readInt();
                    if (entityCount < 0 || entityCount > 65_536) throw new IOException("Invalid block entity intent count");
                    for (int i=0;i<entityCount;i++) {
                        int x=in.readInt(), y=in.readInt(), z=in.readInt(); String entityType=readString(in);
                        int n=in.readInt(); if(n<1||n>1_048_576) throw new IOException("Invalid block entity payload size");
                        byte[] entityPayload=in.readNBytes(n); if(entityPayload.length!=n) throw new EOFException();
                        blockEntities.add(new BlockEntityIntent(x,y,z,entityType,entityPayload));
                    }
                    int physicsCount=in.readInt();
                    if (physicsCount < 0 || physicsCount > 2_000_000) throw new IOException("Invalid physics intent count");
                    for (int i=0;i<physicsCount;i++) physics.add(new PhysicsIntent(in.readInt(),in.readInt(),in.readInt(),in.readBoolean()));
                }
                List<EntitySpawnIntent> entitySpawns = new ArrayList<>();
                if (version >= 4) {
                    int spawnCount = in.readInt();
                    if (spawnCount < 0 || spawnCount > 65_536) throw new IOException("Invalid entity spawn intent count");
                    for (int i=0;i<spawnCount;i++) {
                        java.util.UUID uuid = new java.util.UUID(in.readLong(), in.readLong());
                        String entityType = readString(in);
                        double x=in.readDouble(), y=in.readDouble(), z=in.readDouble();
                        float yaw=in.readFloat(), pitch=in.readFloat();
                        int n=in.readInt(); if(n<0||n>1_048_576) throw new IOException("Invalid entity spawn payload size");
                        byte[] entityPayload=in.readNBytes(n); if(entityPayload.length!=n) throw new EOFException();
                        entitySpawns.add(new EntitySpawnIntent(uuid, entityType, x,y,z,yaw,pitch,entityPayload));
                    }
                }
                if (writes.isEmpty() && blockEntities.isEmpty() && physics.isEmpty() && entitySpawns.isEmpty()) throw new IOException("Empty placement record");
                result = new JournalEvent.PlacementCommitted(seq, new PlacementRecord(key, writes, blockEntities, physics, entitySpawns));
            } else if (type == ANCHOR_COMPLETE) {
                result = new JournalEvent.AnchorCompleted(seq, readString(in), new ChunkPos(in.readInt(), in.readInt()));
            } else if (type == ANCHOR_STARTED) {
                if (version < 2) throw new IOException("Anchor-start event requires journal version 2");
                result = new JournalEvent.AnchorStarted(seq, readString(in), new ChunkPos(in.readInt(), in.readInt()));
            } else if (type == CHUNK_APPLIED) {
                if (version < 2) throw new IOException("Chunk-applied event requires journal version 2");
                result = new JournalEvent.ChunkApplied(seq, readString(in), new ChunkPos(in.readInt(), in.readInt()), readString(in));
            } else throw new IOException("Unknown journal event type " + type);
            if (in.available() != 0) throw new IOException("Trailing bytes in journal event");
            return result;
        }
    }

    private static byte[] encodePlacement(long seq, PlacementRecord record) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            PlacementKey k=record.key(); out.writeByte(VERSION); out.writeByte(PLACEMENT); out.writeLong(seq);
            writeString(out,k.worldKey()); out.writeLong(k.worldSeed()); out.writeInt(k.anchor().x()); out.writeInt(k.anchor().z());
            writeString(out,k.featureId()); writeString(out,k.structureId());
            out.writeInt(k.originX()); out.writeInt(k.originY()); out.writeInt(k.originZ());
            out.writeInt(k.rotationQuarterTurns()); out.writeLong(k.placementOrdinal());
            out.writeInt(record.writes().size());
            for (BlockWrite w : record.writes()) {
                out.writeInt(w.x()); out.writeInt(w.y()); out.writeInt(w.z()); out.writeByte(w.layer());
                byte[] state=w.canonicalState(); out.writeInt(state.length); out.write(state);
            }
            out.writeInt(record.blockEntities().size());
            for (BlockEntityIntent entity : record.blockEntities()) {
                out.writeInt(entity.x()); out.writeInt(entity.y()); out.writeInt(entity.z()); writeString(out, entity.type());
                byte[] payload=entity.payload(); out.writeInt(payload.length); out.write(payload);
            }
            out.writeInt(record.physics().size());
            for (PhysicsIntent intent : record.physics()) {
                out.writeInt(intent.x()); out.writeInt(intent.y()); out.writeInt(intent.z()); out.writeBoolean(intent.applyPhysics());
            }
            out.writeInt(record.entitySpawns().size());
            for (EntitySpawnIntent intent : record.entitySpawns()) {
                out.writeLong(intent.stableUuid().getMostSignificantBits()); out.writeLong(intent.stableUuid().getLeastSignificantBits());
                writeString(out, intent.type()); out.writeDouble(intent.x()); out.writeDouble(intent.y()); out.writeDouble(intent.z());
                out.writeFloat(intent.yaw()); out.writeFloat(intent.pitch()); byte[] payload=intent.payload();
                out.writeInt(payload.length); out.write(payload);
            }
        }
        return bytes.toByteArray();
    }

    private static boolean readFully(FileChannel c, ByteBuffer b, long position) throws IOException {
        while (b.hasRemaining()) { int n=c.read(b,position); if(n<0)return false; if(n==0)continue; position+=n; }
        return true;
    }
    private static void writeString(DataOutput out,String s)throws IOException{
        byte[] b=s.getBytes(StandardCharsets.UTF_8); if(b.length>1_048_576)throw new IOException("String too long"); out.writeInt(b.length);out.write(b);
    }
    private static String readString(DataInput in)throws IOException{
        int n=in.readInt();if(n<0||n>1_048_576)throw new IOException("Invalid string length");byte[] b=new byte[n];in.readFully(b);return new String(b,StandardCharsets.UTF_8);
    }
    private static long eventSequence(JournalEvent e){
        if(e instanceof JournalEvent.PlacementCommitted p)return p.sequence();
        if(e instanceof JournalEvent.AnchorStarted a)return a.sequence();
        if(e instanceof JournalEvent.AnchorCompleted a)return a.sequence();
        return ((JournalEvent.ChunkApplied)e).sequence();
    }
    private long sequenceOfPlacement(String id){return requiredSequence(placementSequences.get(id), "placement", id);}
    private long sequenceOfAnchor(AnchorId id){return requiredSequence(completedAnchorSequences.get(id), "completed anchor", id);}
    private long sequenceOfAnchorStarted(AnchorId id){return requiredSequence(startedAnchorSequences.get(id), "started anchor", id);}
    private long sequenceOfApplied(ChunkId id,String digest){
        if (!digest.equals(appliedDigests.get(id))) throw new IllegalStateException("Applied digest index mismatch: " + id);
        return requiredSequence(appliedSequences.get(id), "applied chunk", id);
    }
    private static long requiredSequence(Long sequence, String kind, Object id) {
        if (sequence == null) throw new IllegalStateException("Missing " + kind + " sequence index: " + id);
        return sequence;
    }
    private static boolean sameRecord(PlacementRecord a,PlacementRecord b){return a.key().equals(b.key())&&a.writes().equals(b.writes())&&a.blockEntities().equals(b.blockEntities())&&a.physics().equals(b.physics())&&a.entitySpawns().equals(b.entitySpawns());}
    private record AnchorId(String worldKey,ChunkPos anchor){}
    private record ChunkId(String worldKey,ChunkPos target){}
    @Override public void close() throws IOException {
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            IOException failure = null;
            try { channel.force(true); } catch (IOException e) { failure = e; }
            try { processLock.release(); } catch (IOException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
            try { channel.close(); } catch (IOException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
            if (failure != null) throw failure;
        } finally { lock.unlock(); }
    }

    private void ensureOpen() throws IOException {
        if (closed || !channel.isOpen()) throw new IOException("Placement journal is closed: " + path);
    }

    public JournalStats stats() throws IOException {
        lock.lock();
        try {
            return new JournalStats(channel.size(), events.size(), placements.size(), startedAnchors.size(),
                    completedAnchors.size(), appliedDigests.size(), nextSequence - 1,
                    recoveryDurationNanos, recoveredTailBytes);
        } finally { lock.unlock(); }
    }

    public record JournalStats(long walBytes, long eventCount, long placementCount,
                               long startedAnchorCount, long completedAnchorCount,
                               long appliedChunkCount, long lastSequence,
                               long recoveryDurationNanos, long recoveredTailBytes) { }
}

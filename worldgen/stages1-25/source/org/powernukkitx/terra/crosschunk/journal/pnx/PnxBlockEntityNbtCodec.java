package org.powernukkitx.terra.crosschunk.journal.pnx;

import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Canonical little-endian, uncompressed PNX CompoundTag encoding for durable BlockEntity intents.
 * The format matches the encoding used by PNX's LevelDB chunk serializer.
 */
public final class PnxBlockEntityNbtCodec {
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private PnxBlockEntityNbtCodec() { }

    public static byte[] encode(CompoundTag tag) throws IOException {
        Objects.requireNonNull(tag, "tag");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (NBTOutputStream nbt = NbtUtils.createWriterLE(output)) {
            nbt.writeTag(tag.toNetwork());
        }
        byte[] payload = output.toByteArray();
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("BlockEntity NBT payload length is out of range: " + payload.length);
        }
        return payload;
    }

    public static CompoundTag decode(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("BlockEntity NBT payload length is out of range: " + payload.length);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(payload);
             NBTInputStream nbt = NbtUtils.createReaderLE(input)) {
            Object root = nbt.readTag();
            if (!(root instanceof NbtMap map)) {
                throw new IOException("BlockEntity payload root is not an NBT compound");
            }
            if (input.available() != 0) {
                throw new IOException("BlockEntity payload contains trailing NBT data");
            }
            return CompoundTag.fromNetwork(map);
        }
    }

    /** Create an immutable journal intent with identity/position fields fixed by the caller. */
    public static BlockEntityIntent toIntent(int x, int y, int z, String type, CompoundTag source) throws IOException {
        if (type == null || type.isBlank()) throw new IOException("BlockEntity type is blank");
        CompoundTag canonical = Objects.requireNonNull(source, "source").copy();
        canonical.putString("id", type).putInt("x", x).putInt("y", y).putInt("z", z);
        return new BlockEntityIntent(x, y, z, type, encode(canonical));
    }

    /** Decode an intent and reject identity/coordinate drift before PNX object construction. */
    public static CompoundTag fromIntent(BlockEntityIntent intent) throws IOException {
        Objects.requireNonNull(intent, "intent");
        CompoundTag tag = decode(intent.payload());
        if (!intent.type().equals(tag.getString("id"))) {
            throw new IOException("BlockEntity NBT id does not match journal intent type");
        }
        if (tag.getInt("x") != intent.x() || tag.getInt("y") != intent.y() || tag.getInt("z") != intent.z()) {
            throw new IOException("BlockEntity NBT coordinates do not match journal intent position");
        }
        return tag;
    }
}

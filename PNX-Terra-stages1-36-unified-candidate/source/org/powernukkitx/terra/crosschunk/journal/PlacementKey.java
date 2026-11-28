package org.powernukkitx.terra.crosschunk.journal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Stable identity of one root feature placement; deliberately independent of runtime hashes. */
public record PlacementKey(String worldKey, long worldSeed, ChunkPos anchor,
                           String featureId, String structureId,
                           int originX, int originY, int originZ,
                           int rotationQuarterTurns, long placementOrdinal) {
    public PlacementKey {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(structureId, "structureId");
        if (worldKey.isBlank() || featureId.isBlank() || structureId.isBlank())
            throw new IllegalArgumentException("Placement identity fields must be non-empty");
        if (rotationQuarterTurns < 0 || rotationQuarterTurns > 3)
            throw new IllegalArgumentException("rotationQuarterTurns must be in [0,3]");
    }

    public String stableId() {
        String canonical = String.join("\u001f", worldKey, Long.toString(worldSeed),
                Integer.toString(anchor.x()), Integer.toString(anchor.z()), featureId, structureId,
                Integer.toString(originX), Integer.toString(originY), Integer.toString(originZ),
                Integer.toString(rotationQuarterTurns), Long.toString(placementOrdinal));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }
}

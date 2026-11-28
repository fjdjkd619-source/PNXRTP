package org.powernukkitx.terra.crosschunk.journal;

public sealed interface JournalEvent permits JournalEvent.PlacementCommitted, JournalEvent.AnchorStarted,
        JournalEvent.AnchorCompleted, JournalEvent.ChunkApplied {
    record PlacementCommitted(long sequence, PlacementRecord record) implements JournalEvent {}
    record AnchorStarted(long sequence, String worldKey, ChunkPos anchor) implements JournalEvent {}
    record AnchorCompleted(long sequence, String worldKey, ChunkPos anchor) implements JournalEvent {}
    record ChunkApplied(long sequence, String worldKey, ChunkPos target, String digest) implements JournalEvent {}
}

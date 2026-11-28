package org.powernukkitx.terra.crosschunk.journal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Regression test for rejecting oversized WAL payloads before delegate growth. */
public final class PlacementJournalEncodingLimitTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        PlacementJournal.BoundedOutputStream bounded =
                new PlacementJournal.BoundedOutputStream(target, 5);

        bounded.write(new byte[]{1, 2, 3, 4, 5});
        check(target.size() == 5, "payload exactly at the limit is accepted");

        boolean rejected = false;
        try {
            bounded.write(6);
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "single-byte overflow is rejected");
        check(target.size() == 5, "overflow does not reach the backing buffer");

        rejected = false;
        try {
            bounded.write(new byte[]{7, 8}, 0, 2);
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "bulk-write overflow is rejected");
        check(target.size() == 5, "bulk overflow does not grow the backing buffer");

        ByteArrayOutputStream empty = new ByteArrayOutputStream();
        PlacementJournal.BoundedOutputStream zero =
                new PlacementJournal.BoundedOutputStream(empty, 0);
        rejected = false;
        try {
            zero.write(1);
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected && empty.size() == 0, "zero-byte limit rejects all payload bytes");

        System.out.println("PlacementJournalEncodingLimitTest: PASS (" + assertions + " assertions)");
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}

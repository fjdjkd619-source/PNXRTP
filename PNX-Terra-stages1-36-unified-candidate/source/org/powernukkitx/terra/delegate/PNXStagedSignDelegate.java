package org.powernukkitx.terra.delegate;

import com.dfsek.terra.api.block.entity.SerialState;
import com.dfsek.terra.api.block.entity.Sign;
import com.dfsek.terra.api.block.state.BlockState;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.terra.crosschunk.journal.BlockEntityIntent;
import org.powernukkitx.utils.DyeColor;

import java.util.Map;
import java.util.function.Consumer;

/** Detached front-side sign view matching TerraScript's text0..text3 state properties. */
public final class PNXStagedSignDelegate extends PNXStagedBlockEntityDelegate implements Sign {
    private static final String FRONT_TEXT = "FrontText";
    private static final String BACK_TEXT = "BackText";
    private static final String TEXT = "Text";
    private final String[] frontLines = new String[4];

    public PNXStagedSignDelegate(int x, int y, int z, String type, BlockState blockState,
                                 CompoundTag initialNbt, Consumer<BlockEntityIntent> intentSink) {
        super(x, y, z, type, blockState, initialNbt, intentSink);
        readFrontLines();
    }

    @Override
    public void setLine(int index, String line) throws IndexOutOfBoundsException {
        checkLine(index);
        frontLines[index] = line == null ? "" : line;
    }

    @Override public String[] getLines() { return frontLines.clone(); }

    @Override
    public String getLine(int index) throws IndexOutOfBoundsException {
        checkLine(index);
        return frontLines[index];
    }

    @Override
    public void applyState(String state) {
        if (state == null || state.isBlank()) return;
        for (Map.Entry<String, String> property : SerialState.parse(state).entrySet()) {
            String key = property.getKey();
            if (!key.startsWith("text") || key.length() == 4) {
                throw new IllegalArgumentException("Invalid sign property: " + key);
            }
            int line = Integer.parseInt(key.substring(4));
            setLine(line, property.getValue());
        }
    }

    @Override
    public boolean update(boolean applyPhysics) {
        writeSignText(FRONT_TEXT, frontLines);
        if (!nbt.containsCompound(BACK_TEXT)) {
            writeSignText(BACK_TEXT, new String[] {"", "", "", ""});
        }
        nbt.putBoolean("TextIgnoreLegacyBugResolved", true)
                .putLong("LockedForEditingBy", -1L)
                .putByte("IsWaxed", (byte) 0);
        return super.update(applyPhysics);
    }

    private void readFrontLines() {
        String text = nbt.containsCompound(FRONT_TEXT) ? nbt.getCompound(FRONT_TEXT).getString(TEXT) : "";
        String[] lines = text == null ? new String[0] : text.split("\\n", -1);
        for (int i = 0; i < frontLines.length; i++) frontLines[i] = i < lines.length ? lines[i] : "";
    }

    private void writeSignText(String side, String[] lines) {
        CompoundTag tag = nbt.containsCompound(side) ? nbt.getCompound(side) : new CompoundTag();
        tag.putString(TEXT, String.join("\n", lines))
                .putInt("SignTextColor", DyeColor.BLACK.getSignColor().getARGB())
                .putByte("IgnoreLighting", (byte) 0)
                .putByte("PersistFormatting", (byte) 1)
                .putBoolean("HideGlowOutline", false)
                .putString("TextOwner", "");
        nbt.putCompound(side, tag);
    }

    private static void checkLine(int index) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException("Sign line index must be 0..3: " + index);
    }
}

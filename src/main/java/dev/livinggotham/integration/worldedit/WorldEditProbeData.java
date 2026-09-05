package dev.livinggotham.integration.worldedit;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Coordinates and phase only; block persistence itself is independently read after reopen. */
final class WorldEditProbeData extends SavedData {
    static final String ID = "living_gotham_worldedit_probe";
    private BlockPos origin;
    private String dimension = "";
    private String stage = "none";

    static WorldEditProbeData load(CompoundTag tag) {
        WorldEditProbeData data = new WorldEditProbeData();
        if (tag.contains("X")) {
            data.origin = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
        data.dimension = tag.getString("Dimension");
        data.stage = tag.getString("Stage");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (origin != null) {
            tag.putInt("X", origin.getX());
            tag.putInt("Y", origin.getY());
            tag.putInt("Z", origin.getZ());
        }
        tag.putString("Dimension", dimension);
        tag.putString("Stage", stage);
        return tag;
    }

    void set(BlockPos origin, String dimension, String stage) {
        this.origin = origin.immutable();
        this.dimension = dimension;
        this.stage = stage;
        setDirty();
    }

    BlockPos origin() {
        return origin;
    }

    String dimension() {
        return dimension;
    }

    String stage() {
        return stage;
    }
}

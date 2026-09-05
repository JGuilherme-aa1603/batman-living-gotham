package dev.livinggotham.integration.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import dev.livinggotham.debug.DevWorldSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Public WorldEdit API boundary. Writes must only run in a disposable DEV world. */
public final class WorldEditIntegration {
    private WorldEditIntegration() {
    }

    public static String version() {
        return WorldEdit.getVersion();
    }

    public static int pasteTinyProbe(ServerLevel level, BlockPos destination) throws WorldEditException {
        if (!DevWorldSafety.isDisposableDevWorld(level)) {
            throw new IllegalStateException("refusing WorldEdit write probe outside a disposable DEV world");
        }
        BlockVector3 min = BlockVector3.ZERO;
        BlockVector3 max = BlockVector3.at(1, 1, 1);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(min, max));
        clipboard.setOrigin(min);

        clipboard.setBlock(BlockVector3.at(0, 0, 0), BlockTypes.GOLD_BLOCK.getDefaultState());
        clipboard.setBlock(BlockVector3.at(1, 0, 0), BlockTypes.BLACK_CONCRETE.getDefaultState());
        clipboard.setBlock(BlockVector3.at(0, 0, 1), BlockTypes.BLACK_CONCRETE.getDefaultState());
        clipboard.setBlock(BlockVector3.at(1, 0, 1), BlockTypes.GOLD_BLOCK.getDefaultState());
        clipboard.setBlock(BlockVector3.at(0, 1, 0), BlockTypes.GLASS.getDefaultState());
        clipboard.setBlock(BlockVector3.at(1, 1, 0), BlockTypes.GLASS.getDefaultState());
        clipboard.setBlock(BlockVector3.at(0, 1, 1), BlockTypes.GLASS.getDefaultState());
        clipboard.setBlock(BlockVector3.at(1, 1, 1), BlockTypes.GLASS.getDefaultState());

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(ForgeAdapter.adapt(level))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(destination.getX(), destination.getY(), destination.getZ()))
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            return editSession.getBlockChangeCount();
        }
    }

    public static PersistenceResult setupPersistenceProbe(ServerLevel level, BlockPos near) throws WorldEditException {
        requireDev(level);
        BlockPos origin = findAirCube(level, near);
        int changed = pasteTinyProbe(level, origin);
        Verification verification = verifyPattern(level, origin, false);
        data(level).set(origin, level.dimension().location().toString(), "pasted");
        return new PersistenceResult(origin, changed, verification, "pasted");
    }

    public static PersistenceResult verifyPersistenceProbe(ServerLevel level) {
        requireDev(level);
        WorldEditProbeData data = data(level);
        requireSameDimension(level, data);
        Verification verification = verifyPattern(level, requireOrigin(data), false);
        return new PersistenceResult(data.origin(), 0, verification, data.stage());
    }

    public static PersistenceResult cleanupPersistenceProbe(ServerLevel level) {
        requireDev(level);
        WorldEditProbeData data = data(level);
        requireSameDimension(level, data);
        BlockPos origin = requireOrigin(data);
        Verification before = verifyPattern(level, origin, false);
        int changed = 0;
        for (BlockPos pos : positions(origin)) {
            if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                changed++;
            }
        }
        Verification after = verifyPattern(level, origin, true);
        data.set(origin, level.dimension().location().toString(), "cleaned");
        return new PersistenceResult(origin, changed,
                new Verification(before.matches() && after.matches(), before.actual() + " -> cleanup=" + after.actual()),
                "cleaned");
    }

    public static PersistenceResult verifyCleanPersistenceProbe(ServerLevel level) {
        requireDev(level);
        WorldEditProbeData data = data(level);
        requireSameDimension(level, data);
        Verification verification = verifyPattern(level, requireOrigin(data), true);
        return new PersistenceResult(data.origin(), 0, verification, data.stage());
    }

    private static Verification verifyPattern(ServerLevel level, BlockPos origin, boolean expectAir) {
        List<String> actual = new ArrayList<>();
        boolean matches = true;
        String[] expected = expectAir
                ? new String[]{"minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air",
                "minecraft:air", "minecraft:air", "minecraft:air", "minecraft:air"}
                : new String[]{"minecraft:gold_block", "minecraft:black_concrete",
                "minecraft:black_concrete", "minecraft:gold_block",
                "minecraft:glass", "minecraft:glass", "minecraft:glass", "minecraft:glass"};
        List<BlockPos> positions = positions(origin);
        for (int index = 0; index < positions.size(); index++) {
            String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getKey(level.getBlockState(positions.get(index)).getBlock()).toString();
            actual.add(positions.get(index).toShortString() + "=" + id);
            matches &= expected[index].equals(id);
        }
        return new Verification(matches, String.join(", ", actual));
    }

    private static List<BlockPos> positions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 1; z++) {
                for (int x = 0; x <= 1; x++) {
                    positions.add(origin.offset(x, y, z));
                }
            }
        }
        return positions;
    }

    private static BlockPos findAirCube(ServerLevel level, BlockPos near) {
        for (int dy = 2; dy <= 20; dy++) {
            BlockPos candidate = near.offset(4, dy, 4);
            if (positions(candidate).stream().allMatch(pos -> level.getBlockState(pos).isAir())) {
                return candidate;
            }
        }
        throw new IllegalStateException("no empty 2x2x2 probe volume found near player");
    }

    private static WorldEditProbeData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldEditProbeData::load, WorldEditProbeData::new,
                WorldEditProbeData.ID);
    }

    private static BlockPos requireOrigin(WorldEditProbeData data) {
        if (data.origin() == null) {
            throw new IllegalStateException("WorldEdit persistence probe has not been set up");
        }
        return data.origin();
    }

    private static void requireSameDimension(ServerLevel level, WorldEditProbeData data) {
        if (!data.dimension().equals(level.dimension().location().toString())) {
            throw new IllegalStateException("probe belongs to dimension " + data.dimension());
        }
    }

    private static void requireDev(ServerLevel level) {
        if (!DevWorldSafety.isDisposableDevWorld(level)) {
            throw new IllegalStateException("refusing WorldEdit persistence probe outside disposable DEV world");
        }
    }

    public record Verification(boolean matches, String actual) {
    }

    public record PersistenceResult(BlockPos origin, int changed, Verification verification, String stage) {
    }
}

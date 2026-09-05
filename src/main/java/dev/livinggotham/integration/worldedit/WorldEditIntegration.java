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
}

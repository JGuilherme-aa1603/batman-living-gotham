package dev.livinggotham.integration.create;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only probe of Create 6.0.8 public API; it never fabricates contraption NBT. */
public final class CreateIntegration {
    private CreateIntegration() {
    }

    public static Snapshot inspect(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return new Snapshot(
                CreateBuiltInRegistries.CONTRAPTION_TYPE.size(),
                BlockStressValues.getImpact(Blocks.COBBLESTONE),
                BlockMovementChecks.isMovementAllowed(state, level, pos)
        );
    }

    public record Snapshot(int contraptionTypes, double cobblestoneStressImpact, boolean blockMovementAllowed) {
    }
}

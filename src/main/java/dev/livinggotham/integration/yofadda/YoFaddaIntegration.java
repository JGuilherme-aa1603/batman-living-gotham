package dev.livinggotham.integration.yofadda;

import net.mcreator.batmanmod.forensic.Footprint;
import net.mcreator.batmanmod.forensic.ForensicTrailData;
import net.mcreator.batmanmod.network.BatmanModModVariables;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * Narrow compatibility boundary for Batman By Yo Fadda 1.0.9.
 *
 * <p>These are public classes and fields, but they are not a declared stable API.
 * Keep every direct symbol reference in this package and revalidate on upgrades.</p>
 */
public final class YoFaddaIntegration {
    public static final String MOD_ID = "batman_mod";

    private YoFaddaIntegration() {
    }

    public static Optional<ForensicState> forensicState(ServerPlayer player) {
        return player.getCapability(BatmanModModVariables.PLAYER_VARIABLES_CAPABILITY)
                .resolve()
                .map(variables -> new ForensicState(
                        variables.DetectiveMode,
                        variables.ForensicScanner,
                        variables.BloodInSight
                ));
    }

    public static int nearbyFootprintCount(ServerPlayer player) {
        long chunkKey = ChunkPos.asLong(player.chunkPosition().x, player.chunkPosition().z);
        return ForensicTrailData.get(player.serverLevel()).chunk(chunkKey).size();
    }

    public static List<Footprint> nearbyFootprints(ServerPlayer player) {
        long chunkKey = ChunkPos.asLong(player.chunkPosition().x, player.chunkPosition().z);
        return List.copyOf(ForensicTrailData.get(player.serverLevel()).chunk(chunkKey));
    }

    public static Footprint createDevFootprint(ServerPlayer player) {
        Footprint footprint = new Footprint(
                (float) player.getX(),
                (float) player.getY(),
                (float) player.getZ(),
                player.getYRot(),
                1.0F,
                Footprint.KIND_PLAYER,
                false,
                player.serverLevel().getGameTime()
        );
        ForensicTrailData.get(player.serverLevel()).add(footprint);
        return footprint;
    }

    public static boolean randomCrimeEnabled(ServerPlayer player) {
        return BatmanModModVariables.MapVariables.get(player.serverLevel()).RandomCrimeON;
    }

    public static RegistrySnapshot registrySnapshot() {
        return new RegistrySnapshot(
                isRegistered("sample_vial"),
                isRegistered("red_blood_sample"),
                isRegistered("green_blood_sample")
        );
    }

    private static boolean isRegistered(String path) {
        return ForgeRegistries.ITEMS.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
    }

    public record ForensicState(boolean detectiveMode, boolean scanner, boolean bloodInSight) {
    }

    public record RegistrySnapshot(boolean sampleVial, boolean redBloodSample, boolean greenBloodSample) {
    }
}

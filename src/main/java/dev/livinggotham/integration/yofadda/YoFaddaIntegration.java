package dev.livinggotham.integration.yofadda;

import net.mcreator.batmanmod.forensic.Footprint;
import net.mcreator.batmanmod.forensic.ForensicTrailData;
import net.mcreator.batmanmod.network.BatmanModModVariables;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    public static Optional<ForensicState> forensicState(Player player) {
        return player.getCapability(BatmanModModVariables.PLAYER_VARIABLES_CAPABILITY)
                .resolve()
                .map(variables -> new ForensicState(
                        variables.DetectiveMode,
                        variables.ForensicScanner,
                        variables.BloodInSight,
                        variables.DetectiveModeButtonPressed,
                        variables.ButtonPressVariable,
                        variables.vial_transfer.copy()
                ));
    }

    public static void equipClassicForensicKit(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, item("batsuit_helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, item("batsuit_chestplate"));
        player.setItemSlot(EquipmentSlot.LEGS, item("batsuit_leggings"));
        player.setItemSlot(EquipmentSlot.FEET, item("batsuit_boots"));
        give(player, item("sample_vial"));
    }

    /** Places only genuine Yo Fadda blood; collection still runs through SampleVialItem.useOn. */
    public static BloodSetup createBloodSetup(ServerPlayer player) {
        Block blood = ForgeRegistries.BLOCKS.getValue(id("red_blood_drop"));
        if (blood == null) {
            throw new IllegalStateException("missing batman_mod:red_blood_drop");
        }
        SupportPosition supportPosition = findSafeSupport(player);
        BlockPos support = supportPosition.position();
        if (supportPosition.created()) {
            player.serverLevel().setBlock(support, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos evidence = support.above();
        if (!player.serverLevel().setBlock(evidence, blood.defaultBlockState(), Block.UPDATE_ALL)) {
            throw new IllegalStateException("could not place red blood evidence at " + evidence);
        }
        var blockEntity = player.serverLevel().getBlockEntity(evidence);
        if (blockEntity != null) {
            var data = blockEntity.getPersistentData();
            data.putString("Name", "Living Gotham DEV Donor");
            data.putString("Health", "20");
            data.putString("Height", "1.80");
            data.putString("Age", "DEV");
            data.putString("Potions", "none");
            blockEntity.setChanged();
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item("sample_vial"));
        return new BloodSetup(support, evidence, supportPosition.created());
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
        return ForgeRegistries.ITEMS.containsKey(id(path));
    }

    private static ItemStack item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(id(path));
        if (item == null) {
            throw new IllegalStateException("missing Yo Fadda item batman_mod:" + path);
        }
        return new ItemStack(item);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static SupportPosition findSafeSupport(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (int radius = 2; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos support = new BlockPos(center.getX() + dx, center.getY() - 1, center.getZ() + dz);
                    if (!player.serverLevel().getBlockState(support).isAir()
                            && player.serverLevel().getBlockState(support.above()).isAir()
                            && player.serverLevel().getBlockState(support.above(2)).isAir()) {
                        return new SupportPosition(support, false);
                    }
                }
            }
        }
        for (int dy = 1; dy <= 12; dy++) {
            BlockPos support = center.offset(4, dy, 4);
            if (player.serverLevel().getBlockState(support).isAir()
                    && player.serverLevel().getBlockState(support.above()).isAir()
                    && player.serverLevel().getBlockState(support.above(2)).isAir()) {
                return new SupportPosition(support, true);
            }
        }
        throw new IllegalStateException("no safe three-block air column within DEV probe bounds");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public record ForensicState(boolean detectiveMode, boolean scanner, boolean bloodInSight,
                                boolean buttonPressed, double buttonTicks, ItemStack vialTransfer) {
    }

    public record RegistrySnapshot(boolean sampleVial, boolean redBloodSample, boolean greenBloodSample) {
    }

    public record BloodSetup(BlockPos support, BlockPos evidence, boolean temporarySupportCreated) {
    }

    private record SupportPosition(BlockPos position, boolean created) {
    }
}

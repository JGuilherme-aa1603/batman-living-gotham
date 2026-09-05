package dev.livinggotham.debug;

import dev.livinggotham.LivingGotham;
import dev.livinggotham.integration.yofadda.YoFaddaIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary observation hooks. They log state transitions; they do not implement gameplay. */
public final class ProbeEvents {
    private static final Map<UUID, Boolean> LAST_SCANNER_STATE = new HashMap<>();
    private static final Map<UUID, PendingSample> PENDING_SAMPLES = new HashMap<>();
    private static final Map<UUID, String> LAST_MENU = new HashMap<>();

    private ProbeEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ProbeCommands.register(event.getDispatcher());
        LivingGotham.LOGGER.info("[LG_PROBE] /lgprobe commands registered");
    }

    @SubscribeEvent
    public static void observeBloodCollection(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !yoFaddaLoaded()) {
            return;
        }
        ResourceLocation held = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (held != null && held.toString().equals("batman_mod:sample_vial")) {
            PENDING_SAMPLES.put(player.getUUID(), new PendingSample(player.serverLevel().getGameTime(), event.getPos()));
            LivingGotham.LOGGER.info("[LG_PROBE] sample_vial interaction player={} pos={}",
                    player.getGameProfile().getName(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void observePlayer(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player) || !yoFaddaLoaded()) {
            return;
        }

        YoFaddaIntegration.forensicState(player).ifPresent(state -> {
            Boolean previous = LAST_SCANNER_STATE.put(player.getUUID(), state.scanner());
            if (previous == null || previous != state.scanner()) {
                LivingGotham.LOGGER.info("[LG_PROBE] scanner_transition player={} scanner={} detective_mode={} blood_in_sight={}",
                        player.getGameProfile().getName(), state.scanner(), state.detectiveMode(), state.bloodInSight());
            }
        });

        // InventoryMenu intentionally cannot be reconstructed from MenuType and throws from
        // getType(). Only opened network menus are relevant to the DNA probe.
        if (player.containerMenu != player.inventoryMenu) {
            String currentMenu = String.valueOf(ForgeRegistries.MENU_TYPES.getKey(player.containerMenu.getType()));
            String previousMenu = LAST_MENU.put(player.getUUID(), currentMenu);
            if (!currentMenu.equals(previousMenu) && currentMenu.startsWith("batman_mod:dna_")) {
                LivingGotham.LOGGER.info("[LG_PROBE] dna_menu player={} menu={}",
                        player.getGameProfile().getName(), currentMenu);
            }
        } else {
            LAST_MENU.remove(player.getUUID());
        }

        PendingSample pending = PENDING_SAMPLES.get(player.getUUID());
        if (pending != null && player.serverLevel().getGameTime() > pending.startedAt()) {
            ItemStack mainHand = player.getMainHandItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(mainHand.getItem());
            if (itemId != null && itemId.getNamespace().equals(YoFaddaIntegration.MOD_ID)
                    && (itemId.getPath().equals("red_blood_sample") || itemId.getPath().equals("green_blood_sample"))) {
                LivingGotham.LOGGER.info("[LG_PROBE] blood_sample_collected player={} item={} source_pos={} tag={}",
                        player.getGameProfile().getName(), itemId, pending.source(), mainHand.getTag());
                PENDING_SAMPLES.remove(player.getUUID());
            } else if (player.serverLevel().getGameTime() - pending.startedAt() > 40) {
                LivingGotham.LOGGER.info("[LG_PROBE] sample_vial interaction expired without sample player={} source_pos={}",
                        player.getGameProfile().getName(), pending.source());
                PENDING_SAMPLES.remove(player.getUUID());
            }
        }
    }

    private static boolean yoFaddaLoaded() {
        return ModList.get().isLoaded(YoFaddaIntegration.MOD_ID);
    }

    private record PendingSample(long startedAt, net.minecraft.core.BlockPos source) {
    }
}

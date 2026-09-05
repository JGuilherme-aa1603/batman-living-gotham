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
    private static final Map<UUID, ForensicSnapshot> LAST_SERVER_FORENSICS = new HashMap<>();
    private static final Map<UUID, ForensicSnapshot> LAST_CLIENT_FORENSICS = new HashMap<>();
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
            PENDING_SAMPLES.put(player.getUUID(), new PendingSample(player.serverLevel().getGameTime(),
                    event.getPos(), held.toString(), String.valueOf(event.getItemStack().getTag())));
            LivingGotham.LOGGER.info("[LG_PROBE] sample_vial_interaction side=SERVER player={} tick={} clicked_pos={} before_item={} before_tag={}",
                    player.getGameProfile().getName(), player.serverLevel().getGameTime(), event.getPos(),
                    held, event.getItemStack().getTag());
        }
    }

    @SubscribeEvent
    public static void observePlayer(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !yoFaddaLoaded()) {
            return;
        }

        if (event.player.level().isClientSide) {
            observeForensics(event.player, "CLIENT", LAST_CLIENT_FORENSICS);
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        YoFaddaIntegration.forensicState(player).ifPresent(state -> {
            Boolean previous = LAST_SCANNER_STATE.put(player.getUUID(), state.scanner());
            if (previous == null || previous != state.scanner()) {
                LivingGotham.LOGGER.info("[LG_PROBE] scanner_transition side=SERVER player={} tick={} scanner={} detective_mode={} blood_in_sight={} button_pressed={} button_ticks={} head={} chest={} legs={} feet={}",
                        player.getGameProfile().getName(), player.serverLevel().getGameTime(), state.scanner(),
                        state.detectiveMode(), state.bloodInSight(), state.buttonPressed(), state.buttonTicks(),
                        itemId(player.getInventory().armor.get(3)), itemId(player.getInventory().armor.get(2)),
                        itemId(player.getInventory().armor.get(1)), itemId(player.getInventory().armor.get(0)));
            }
        });
        observeForensics(player, "SERVER", LAST_SERVER_FORENSICS);

        // InventoryMenu intentionally cannot be reconstructed from MenuType and throws from
        // getType(). Only opened network menus are relevant to the DNA probe.
        if (player.containerMenu != player.inventoryMenu) {
            String currentMenu = String.valueOf(ForgeRegistries.MENU_TYPES.getKey(player.containerMenu.getType()));
            String previousMenu = LAST_MENU.put(player.getUUID(), currentMenu);
            if (!currentMenu.equals(previousMenu) && currentMenu.startsWith("batman_mod:")) {
                LivingGotham.LOGGER.info("[LG_PROBE] yofadda_menu_open side=SERVER player={} tick={} menu={} slots={} mainhand={} vial_transfer={}",
                        player.getGameProfile().getName(), player.serverLevel().getGameTime(), currentMenu,
                        player.containerMenu.slots.size(), stack(player.getMainHandItem()),
                        YoFaddaIntegration.forensicState(player).map(state -> stack(state.vialTransfer())).orElse("capability-absent"));
            }
        } else {
            String closed = LAST_MENU.remove(player.getUUID());
            if (closed != null) {
                LivingGotham.LOGGER.info("[LG_PROBE] yofadda_menu_close side=SERVER player={} tick={} menu={} mainhand={}",
                        player.getGameProfile().getName(), player.serverLevel().getGameTime(), closed,
                        stack(player.getMainHandItem()));
            }
        }

        PendingSample pending = PENDING_SAMPLES.get(player.getUUID());
        if (pending != null && player.serverLevel().getGameTime() > pending.startedAt()) {
            ItemStack mainHand = player.getMainHandItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(mainHand.getItem());
            if (itemId != null && itemId.getNamespace().equals(YoFaddaIntegration.MOD_ID)
                    && (itemId.getPath().equals("red_blood_sample") || itemId.getPath().equals("green_blood_sample"))) {
                LivingGotham.LOGGER.info("[LG_PROBE] blood_sample_collected side=SERVER player={} started_tick={} observed_tick={} elapsed_ticks={} clicked_pos={} before_item={} before_tag={} after_item={} after_tag={} menu={}",
                        player.getGameProfile().getName(), pending.startedAt(), player.serverLevel().getGameTime(),
                        player.serverLevel().getGameTime() - pending.startedAt(), pending.source(), pending.beforeItem(),
                        pending.beforeTag(), itemId, mainHand.getTag(), currentMenu(player));
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

    private static void observeForensics(net.minecraft.world.entity.player.Player player, String side,
                                         Map<UUID, ForensicSnapshot> previousStates) {
        YoFaddaIntegration.forensicState(player).ifPresent(state -> {
            ForensicSnapshot current = new ForensicSnapshot(state.detectiveMode(), state.scanner(),
                    state.bloodInSight(), state.buttonPressed(), state.buttonTicks());
            ForensicSnapshot previous = previousStates.put(player.getUUID(), current);
            if (previous != null && (previous.detectiveMode() != current.detectiveMode()
                    || previous.scanner() != current.scanner()
                    || previous.bloodInSight() != current.bloodInSight()
                    || previous.buttonPressed() != current.buttonPressed())) {
                LivingGotham.LOGGER.info("[LG_PROBE] forensic_transition side={} player={} tick={} detective_mode={}->{} scanner={}->{} blood_in_sight={}->{} button_pressed={}->{} button_ticks={}->{} head={}",
                        side, player.getGameProfile().getName(), player.level().getGameTime(),
                        previous.detectiveMode(), current.detectiveMode(), previous.scanner(), current.scanner(),
                        previous.bloodInSight(), current.bloodInSight(), previous.buttonPressed(), current.buttonPressed(),
                        previous.buttonTicks(), current.buttonTicks(), itemId(player.getInventory().armor.get(3)));
            }
        });
    }

    private static String currentMenu(ServerPlayer player) {
        return player.containerMenu == player.inventoryMenu ? "minecraft:inventory"
                : String.valueOf(ForgeRegistries.MENU_TYPES.getKey(player.containerMenu.getType()));
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return stack.isEmpty() ? "empty" : String.valueOf(id);
    }

    private static String stack(ItemStack stack) {
        return itemId(stack) + " x" + stack.getCount() + " tag=" + stack.getTag();
    }

    private record PendingSample(long startedAt, net.minecraft.core.BlockPos source,
                                 String beforeItem, String beforeTag) {
    }

    private record ForensicSnapshot(boolean detectiveMode, boolean scanner, boolean bloodInSight,
                                    boolean buttonPressed, double buttonTicks) {
    }
}

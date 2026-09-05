package dev.livinggotham.debug;

import dev.livinggotham.LivingGotham;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Explicitly opt-in runtime harness used by {@code -PlivingGothamAutoProbe=true}.
 * It invokes the real Brigadier commands after a player joins and never runs in normal dev play.
 */
public final class AutoProbeEvents {
    private static final String MODE = System.getProperty("livinggotham.autoProbeMode", "false");
    private static final Map<UUID, Long> START_AT = new HashMap<>();
    private static final Set<UUID> EXECUTED = new HashSet<>();
    private static final Set<UUID> SAVED = new HashSet<>();

    private AutoProbeEvents() {
    }

    @SubscribeEvent
    public static void tickPlayer(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        long started = START_AT.computeIfAbsent(player.getUUID(), ignored -> now);
        if (!EXECUTED.contains(player.getUUID()) && now - started >= 60L) {
            if (!DevWorldSafety.isDisposableDevWorld(player.serverLevel())) {
                LivingGotham.LOGGER.error("[LG_PROBE] automatic probes refused: world is not a disposable DEV copy");
                EXECUTED.add(player.getUUID());
                SAVED.add(player.getUUID());
                return;
            }
            CommandSourceStack source = player.createCommandSourceStack().withPermission(4);
            execute(source, "lgprobe status");
            execute(source, "lgprobe yofadda");
            execute(source, "lgprobe forensics");
            execute(source, "lgprobe create");
            execute(source, "lgprobe worldedit");
            execute(source, "lgprobe tacz");
            if (MODE.equalsIgnoreCase("phase11")) {
                execute(source, "lgprobe yofadda forensic-kit");
                execute(source, "lgprobe yofadda blood-setup");
                execute(source, "lgprobe worldedit persistence setup");
                execute(source, "lgprobe tacz entity");
                execute(source, "lgprobe tacz accuracy");
                execute(source, "lgprobe tacz damage");
                execute(source, "lgprobe tacz suppressor");
                LivingGotham.LOGGER.info("[LG_PROBE] PHASE11_MANUAL_ACTIONS_READY hold_z_then_release_then_press_z; right_click_logged_support_with_sample_vial");
            } else if (MODE.equalsIgnoreCase("phase11-verify")) {
                execute(source, "lgprobe worldedit persistence verify");
                execute(source, "lgprobe tacz persistence");
                execute(source, "lgprobe worldedit persistence cleanup");
            } else if (MODE.equalsIgnoreCase("phase11-clean-verify")) {
                execute(source, "lgprobe worldedit persistence verify-clean");
                execute(source, "lgprobe tacz persistence");
            } else if (!MODE.equalsIgnoreCase("verify")) {
                execute(source, "lgprobe forensics create-footprint");
                execute(source, "lgprobe forensics");
                execute(source, "lgprobe worldedit paste");
                execute(source, "lgprobe tacz fire");
            } else {
                LivingGotham.LOGGER.info("[LG_PROBE] AUTOMATIC_DEV_VERIFY_COMPLETE writes=false");
                SAVED.add(player.getUUID());
            }
            EXECUTED.add(player.getUUID());
        }

        if (EXECUTED.contains(player.getUUID()) && !SAVED.contains(player.getUUID())
                && now - started >= (MODE.equalsIgnoreCase("phase11") ? 600L : 180L)) {
            boolean saved = player.getServer().saveEverything(false, true, true);
            SAVED.add(player.getUUID());
            LivingGotham.LOGGER.info("[LG_PROBE] AUTOMATIC_DEV_PROBES_COMPLETE world_saved={}", saved);
        }
    }

    private static void execute(CommandSourceStack source, String command) {
        LivingGotham.LOGGER.info("[LG_PROBE] auto_command /{}", command);
        source.getServer().getCommands().performPrefixedCommand(source, command);
    }
}

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
    private static final boolean VERIFY_ONLY = "verify".equalsIgnoreCase(
            System.getProperty("livinggotham.autoProbeMode", "false"));
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
            if (!VERIFY_ONLY) {
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
                && now - started >= 180L) {
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

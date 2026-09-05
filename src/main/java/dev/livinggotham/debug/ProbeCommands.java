package dev.livinggotham.debug;

import com.mojang.brigadier.CommandDispatcher;
import dev.livinggotham.LivingGotham;
import dev.livinggotham.integration.create.CreateIntegration;
import dev.livinggotham.integration.tacz.TaczIntegration;
import dev.livinggotham.integration.worldedit.WorldEditIntegration;
import dev.livinggotham.integration.yofadda.YoFaddaIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

public final class ProbeCommands {
    private ProbeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lgprobe")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("yofadda").executes(context -> yofadda(context.getSource())))
                .then(Commands.literal("forensics")
                        .executes(context -> forensics(context.getSource()))
                        .then(Commands.literal("create-footprint")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> createFootprint(context.getSource()))))
                .then(Commands.literal("worldedit")
                        .executes(context -> worldEditStatus(context.getSource()))
                        .then(Commands.literal("paste")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> worldEditPaste(context.getSource()))))
                .then(Commands.literal("create").executes(context -> create(context.getSource())))
                .then(Commands.literal("tacz")
                        .executes(context -> taczStatus(context.getSource()))
                        .then(Commands.literal("fire")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> taczFire(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        reply(source, "Living Gotham probe | Forge=" + version("forge")
                + " | YoFadda=" + version(YoFaddaIntegration.MOD_ID)
                + " | Create=" + version("create")
                + " | WorldEdit=" + version("worldedit")
                + " | TaCZ=" + version(TaczIntegration.MOD_ID));
        return 1;
    }

    private static int yofadda(CommandSourceStack source) {
        if (!loaded(YoFaddaIntegration.MOD_ID)) {
            return unavailable(source, "Batman By Yo Fadda");
        }
        YoFaddaIntegration.RegistrySnapshot items = YoFaddaIntegration.registrySnapshot();
        reply(source, "Yo Fadda " + version(YoFaddaIntegration.MOD_ID)
                + " | sample_vial=" + items.sampleVial()
                + " | red_sample=" + items.redBloodSample()
                + " | green_sample=" + items.greenBloodSample());
        return 1;
    }

    private static int forensics(CommandSourceStack source) {
        if (!loaded(YoFaddaIntegration.MOD_ID)) {
            return unavailable(source, "Batman By Yo Fadda");
        }
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            reply(source, "This probe requires a player.");
            return 0;
        }
        return YoFaddaIntegration.forensicState(player).map(state -> {
            reply(source, "Forensics | scanner=" + onOff(state.scanner())
                    + " | detective_mode=" + onOff(state.detectiveMode())
                    + " | blood_in_sight=" + onOff(state.bloodInSight())
                    + " | footprints_in_chunk=" + YoFaddaIntegration.nearbyFootprintCount(player)
                    + " | random_crime=" + onOff(YoFaddaIntegration.randomCrimeEnabled(player)));
            return 1;
        }).orElseGet(() -> {
            reply(source, "Yo Fadda player capability is absent.");
            return 0;
        });
    }

    private static int createFootprint(CommandSourceStack source) {
        if (!loaded(YoFaddaIntegration.MOD_ID)) {
            return unavailable(source, "Batman By Yo Fadda");
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            var footprint = YoFaddaIntegration.createDevFootprint(player);
            reply(source, "DEV footprint created at " + footprint.x + ", " + footprint.y + ", " + footprint.z
                    + "; current chunk count=" + YoFaddaIntegration.nearbyFootprintCount(player));
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("Yo Fadda footprint probe failed", exception);
            reply(source, "Footprint probe failed: " + exception.getClass().getSimpleName());
            return 0;
        }
    }

    private static int worldEditStatus(CommandSourceStack source) {
        if (!loaded("worldedit")) {
            return unavailable(source, "WorldEdit");
        }
        reply(source, "WorldEdit loaded | API version=" + WorldEditIntegration.version()
                + " | use /lgprobe worldedit paste only in a disposable DEV world");
        return 1;
    }

    private static int worldEditPaste(CommandSourceStack source) {
        if (!loaded("worldedit")) {
            return unavailable(source, "WorldEdit");
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            BlockPos destination = player.blockPosition().relative(player.getDirection(), 3);
            int changed = WorldEditIntegration.pasteTinyProbe(player.serverLevel(), destination);
            reply(source, "WorldEdit API pasted DEV clipboard at " + destination.toShortString()
                    + " | changed=" + changed);
            return changed > 0 ? 1 : 0;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("WorldEdit probe failed", exception);
            reply(source, "WorldEdit probe failed: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            return 0;
        }
    }

    private static int create(CommandSourceStack source) {
        if (!loaded("create")) {
            return unavailable(source, "Create");
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            var snapshot = CreateIntegration.inspect(player.serverLevel(), player.blockPosition().below());
            reply(source, "Create " + version("create")
                    + " | public contraption types=" + snapshot.contraptionTypes()
                    + " | cobblestone stress impact=" + snapshot.cobblestoneStressImpact()
                    + " | block below movement allowed=" + snapshot.blockMovementAllowed());
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("Create probe failed", exception);
            reply(source, "Create probe failed: " + exception.getClass().getSimpleName());
            return 0;
        }
    }

    private static int taczStatus(CommandSourceStack source) {
        if (!loaded(TaczIntegration.MOD_ID)) {
            return unavailable(source, "TaCZ");
        }
        var snapshot = TaczIntegration.inspect();
        reply(source, "TaCZ " + version(TaczIntegration.MOD_ID)
                + " | common guns=" + snapshot.guns()
                + " | ammo types=" + snapshot.ammoTypes()
                + " | attachments=" + snapshot.attachments()
                + " | tacz:glock_17=" + snapshot.probeGunPresent()
                + " | use /lgprobe tacz fire only in a disposable DEV world");
        return snapshot.probeGunPresent() ? 1 : 0;
    }

    private static int taczFire(CommandSourceStack source) {
        if (!loaded(TaczIntegration.MOD_ID)) {
            return unavailable(source, "TaCZ");
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            var probe = TaczIntegration.startDevShot(player);
            reply(source, "TaCZ DEV shot armed | shooter=" + probe.shooterId()
                    + " | target=" + probe.targetId()
                    + " | gun=" + probe.gunId()
                    + " | the server log will record shoot/fire/projectile/hit events");
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("TaCZ firing probe failed", exception);
            reply(source, "TaCZ firing probe failed: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            return 0;
        }
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo())
                .map(IModInfo::getVersion)
                .map(Object::toString)
                .orElse("not-loaded");
    }

    private static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static int unavailable(CommandSourceStack source, String name) {
        source.sendFailure(Component.literal(name + " is not loaded."));
        return 0;
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[LG Probe] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)), false);
        LivingGotham.LOGGER.info("[LG_PROBE] {}", message);
    }
}

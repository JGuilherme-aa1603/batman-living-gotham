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
                .then(Commands.literal("yofadda")
                        .executes(context -> yofadda(context.getSource()))
                        .then(Commands.literal("forensic-kit").requires(source -> source.hasPermission(2))
                                .executes(context -> forensicKit(context.getSource())))
                        .then(Commands.literal("blood-setup").requires(source -> source.hasPermission(2))
                                .executes(context -> bloodSetup(context.getSource()))))
                .then(Commands.literal("forensics")
                        .executes(context -> forensics(context.getSource()))
                        .then(Commands.literal("create-footprint")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> createFootprint(context.getSource()))))
                .then(Commands.literal("worldedit")
                        .executes(context -> worldEditStatus(context.getSource()))
                        .then(Commands.literal("paste")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> worldEditPaste(context.getSource())))
                        .then(Commands.literal("persistence")
                                .then(Commands.literal("setup").requires(source -> source.hasPermission(2))
                                        .executes(context -> worldEditPersistence(context.getSource(), "setup")))
                                .then(Commands.literal("verify").requires(source -> source.hasPermission(2))
                                        .executes(context -> worldEditPersistence(context.getSource(), "verify")))
                                .then(Commands.literal("cleanup").requires(source -> source.hasPermission(2))
                                        .executes(context -> worldEditPersistence(context.getSource(), "cleanup")))
                                .then(Commands.literal("verify-clean").requires(source -> source.hasPermission(2))
                                        .executes(context -> worldEditPersistence(context.getSource(), "verify-clean")))))
                .then(Commands.literal("create").executes(context -> create(context.getSource())))
                .then(Commands.literal("tacz")
                        .executes(context -> taczStatus(context.getSource()))
                        .then(Commands.literal("fire")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> taczEntity(context.getSource())))
                        .then(Commands.literal("entity").requires(source -> source.hasPermission(2))
                                .executes(context -> taczEntity(context.getSource())))
                        .then(Commands.literal("persistence").requires(source -> source.hasPermission(2))
                                .executes(context -> taczPersistence(context.getSource())))
                        .then(Commands.literal("accuracy").requires(source -> source.hasPermission(2))
                                .executes(context -> taczAccuracy(context.getSource())))
                        .then(Commands.literal("damage").requires(source -> source.hasPermission(2))
                                .executes(context -> taczDamage(context.getSource())))
                        .then(Commands.literal("suppressor").requires(source -> source.hasPermission(2))
                                .executes(context -> taczSuppressor(context.getSource())))));
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
                    + " | button_pressed=" + state.buttonPressed()
                    + " | button_ticks=" + state.buttonTicks()
                    + " | head=" + player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                    + " | footprints_in_chunk=" + YoFaddaIntegration.nearbyFootprintCount(player)
                    + " | random_crime=" + onOff(YoFaddaIntegration.randomCrimeEnabled(player)));
            return 1;
        }).orElseGet(() -> {
            reply(source, "Yo Fadda player capability is absent.");
            return 0;
        });
    }

    private static int forensicKit(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DevWorldSafety.requireDisposableDevWorld(player.serverLevel());
            YoFaddaIntegration.equipClassicForensicKit(player);
            reply(source, "equipped genuine classic Batsuit and supplied genuine Sample Vial; hold Z for >=10 ticks");
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("Yo Fadda forensic kit setup failed", exception);
            reply(source, "Forensic kit setup failed: " + exception.getMessage());
            return 0;
        }
    }

    private static int bloodSetup(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            DevWorldSafety.requireDisposableDevWorld(player.serverLevel());
            var setup = YoFaddaIntegration.createBloodSetup(player);
            reply(source, "genuine red blood placed at " + setup.evidence().toShortString()
                    + "; right-click support block " + setup.support().toShortString() + " with mainhand Sample Vial"
                    + " | temporary_support=" + setup.temporarySupportCreated());
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("Yo Fadda blood setup failed", exception);
            reply(source, "Blood setup failed: " + exception.getMessage());
            return 0;
        }
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

    private static int worldEditPersistence(CommandSourceStack source, String action) {
        if (!loaded("worldedit")) {
            return unavailable(source, "WorldEdit");
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            var result = switch (action) {
                case "setup" -> WorldEditIntegration.setupPersistenceProbe(player.serverLevel(), player.blockPosition());
                case "verify" -> WorldEditIntegration.verifyPersistenceProbe(player.serverLevel());
                case "cleanup" -> WorldEditIntegration.cleanupPersistenceProbe(player.serverLevel());
                case "verify-clean" -> WorldEditIntegration.verifyCleanPersistenceProbe(player.serverLevel());
                default -> throw new IllegalArgumentException("unknown action " + action);
            };
            reply(source, "WorldEdit persistence " + action + " | origin=" + result.origin().toShortString()
                    + " | stage=" + result.stage() + " | changed=" + result.changed()
                    + " | matches=" + result.verification().matches() + " | states=" + result.verification().actual());
            return result.verification().matches() ? 1 : 0;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("WorldEdit persistence probe failed", exception);
            reply(source, "WorldEdit persistence " + action + " failed: " + exception.getMessage());
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

    private static int taczEntity(CommandSourceStack source) {
        if (!loaded(TaczIntegration.MOD_ID)) {
            return unavailable(source, "TaCZ");
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            var probe = TaczIntegration.startDevEntityCycle(player);
            reply(source, "TaCZ Living Gotham DEV entity armed | shooter=" + probe.shooterId()
                    + " | target=" + probe.targetId()
                    + " | gun=" + probe.gunId() + " | sequence=3 shots -> reload -> shoot");
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("TaCZ firing probe failed", exception);
            reply(source, "TaCZ firing probe failed: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            return 0;
        }
    }

    private static int taczPersistence(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            var result = TaczIntegration.inspectPersisted(player);
            reply(source, "TaCZ persisted DEV entities=" + result.count() + " | " + result.detail());
            return result.count() > 0 ? 1 : 0;
        } catch (Exception exception) {
            reply(source, "TaCZ persistence inspection failed: " + exception.getMessage());
            return 0;
        }
    }

    private static int taczAccuracy(CommandSourceStack source) {
        return runTacz(source, "accuracy", TaczIntegration::startAccuracyProbe);
    }

    private static int taczDamage(CommandSourceStack source) {
        return runTacz(source, "damage", TaczIntegration::startDamageProbe);
    }

    private static int taczSuppressor(CommandSourceStack source) {
        return runTacz(source, "suppressor", TaczIntegration::startSuppressorProbe);
    }

    private static int runTacz(CommandSourceStack source, String name,
                               java.util.function.Consumer<ServerPlayer> probe) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            probe.accept(player);
            reply(source, "TaCZ " + name + " runtime probe started; inspect [LG_PROBE] server evidence");
            return 1;
        } catch (Exception exception) {
            LivingGotham.LOGGER.error("TaCZ " + name + " probe failed", exception);
            reply(source, "TaCZ " + name + " probe failed: " + exception.getMessage());
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

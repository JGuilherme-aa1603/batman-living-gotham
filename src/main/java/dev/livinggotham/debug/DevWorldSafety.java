package dev.livinggotham.debug;

import dev.livinggotham.LivingGotham;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Central fail-closed gate for probes that mutate a world. */
public final class DevWorldSafety {
    private DevWorldSafety() {
    }

    public static boolean isDisposableDevWorld(ServerLevel level) {
        return isDisposableDevWorld(level.getServer());
    }

    public static boolean isDisposableDevWorld(MinecraftServer server) {
        try {
            Path storagePath = server.getWorldPath(LevelResource.ROOT).toRealPath();
            Path fileName = storagePath.getFileName();
            boolean namedDev = fileName != null
                    && fileName.toString().toUpperCase(Locale.ROOT).contains("DEV");
            boolean underBaseline = false;
            for (Path component : storagePath) {
                if (component.toString().equals("LosPerrito2.0")) {
                    underBaseline = true;
                    break;
                }
            }
            LivingGotham.LOGGER.info("[LG_PROBE] world_safety path={} named_dev={} under_baseline={}",
                    storagePath, namedDev, underBaseline);
            return namedDev && !underBaseline;
        } catch (IOException exception) {
            LivingGotham.LOGGER.warn("[LG_PROBE] could not resolve world path for safety gate", exception);
            return false;
        }
    }
}

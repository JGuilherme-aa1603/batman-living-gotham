package dev.livinggotham;

import com.mojang.logging.LogUtils;
import dev.livinggotham.debug.AutoProbeEvents;
import dev.livinggotham.debug.ProbeEvents;
import dev.livinggotham.debug.TaczProbeEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LivingGotham.MOD_ID)
public final class LivingGotham {
    public static final String MOD_ID = "living_gotham";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingGotham() {
        MinecraftForge.EVENT_BUS.register(ProbeEvents.class);
        if (Boolean.getBoolean("livinggotham.autoProbe")) {
            MinecraftForge.EVENT_BUS.register(AutoProbeEvents.class);
            LOGGER.warn("[LG_PROBE] automatic DEV probe sequence ENABLED");
        }
        if (ModList.get().isLoaded("tacz")) {
            MinecraftForge.EVENT_BUS.register(TaczProbeEvents.class);
            LOGGER.info("[LG_PROBE] TaCZ observation hooks registered");
        }
        LOGGER.info("Living Gotham initialized");
    }
}

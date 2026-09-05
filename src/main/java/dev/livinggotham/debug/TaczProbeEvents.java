package dev.livinggotham.debug;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import dev.livinggotham.LivingGotham;
import dev.livinggotham.integration.tacz.TaczIntegration;
import dev.livinggotham.integration.tacz.TaczProbePolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/** Runtime evidence hooks for the candidate TaCZ integration. */
public final class TaczProbeEvents {
    private TaczProbeEvents() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            TaczIntegration.tick(level);
        }
    }

    @SubscribeEvent
    public static void observeShoot(GunShootEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && TaczIntegration.isProbeEntity(event.getShooter())) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_gun_shoot_event shooter={} gun={} canceled={}",
                    event.getShooter().getUUID(), TaczIntegration.gunId(event.getGunItemStack()), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void observeFire(GunFireEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && TaczIntegration.isProbeEntity(event.getShooter())) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_gun_fire_event shooter={} gun={} canceled={}",
                    event.getShooter().getUUID(), TaczIntegration.gunId(event.getGunItemStack()), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void observeReload(GunReloadEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && TaczIntegration.isProbeEntity(event.getEntity())) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_gun_reload_event shooter={} gun={} canceled={}",
                    event.getEntity().getUUID(), TaczIntegration.gunId(event.getGunItemStack()), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void controlProbeAccuracy(AttachmentPropertyEvent event) {
        TaczIntegration.applyControlledProperties(event);
    }

    @SubscribeEvent
    public static void observeProjectile(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof EntityKineticBullet bullet
                && TaczIntegration.isProbeEntity(bullet.getOwner())) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_projectile_spawned projectile={} shooter={} gun={} ammo={}",
                    bullet.getUUID(), bullet.getOwner().getUUID(), bullet.getGunId(), bullet.getAmmoId());
            TaczIntegration.observeProjectile(bullet);
        }
    }

    @SubscribeEvent
    public static void tuneDevDamage(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !TaczIntegration.isProbeEntity(event.getAttacker())) {
            return;
        }
        float original = event.getBaseAmount();
        float multiplier = TaczIntegration.damageMultiplier(event.getAttacker());
        float adjusted = TaczProbePolicy.adjustedDamage(original, multiplier);
        event.setBaseAmount(adjusted);
        LivingGotham.LOGGER.info("[LG_PROBE] tacz_damage_adjust profile={} shooter={} multiplier={} before={} after={}",
                TaczIntegration.profile(event.getAttacker()), event.getAttacker().getUUID(), multiplier,
                original, adjusted);
    }

    @SubscribeEvent
    public static void observeHit(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && TaczIntegration.isProbeEntity(event.getAttacker())) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_entity_hit shooter={} target={} gun={} base_damage={} headshot={}",
                    event.getAttacker().getUUID(),
                    event.getHurtEntity() == null ? "null" : event.getHurtEntity().getUUID(),
                    event.getGunId(), event.getBaseAmount(), event.isHeadShot());
        }
    }

    @SubscribeEvent
    public static void observeKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && TaczIntegration.isProbeEntity(event.getAttacker())) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_entity_kill shooter={} target={} gun={} base_damage={} headshot={}",
                    event.getAttacker().getUUID(),
                    event.getKilledEntity() == null ? "null" : event.getKilledEntity().getUUID(),
                    event.getGunId(), event.getBaseDamage(), event.isHeadShot());
        }
    }
}

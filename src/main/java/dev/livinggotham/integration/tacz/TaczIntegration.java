package dev.livinggotham.integration.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import dev.livinggotham.LivingGotham;
import dev.livinggotham.debug.DevWorldSafety;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Narrow TaCZ 1.1.8-hotfix public-API boundary.
 *
 * <p>The firing path deliberately uses a disposable vanilla entity. It proves that TaCZ's
 * LivingEntity mixin can back a future Living Gotham NPC without defining gameplay AI.</p>
 */
public final class TaczIntegration {
    public static final String MOD_ID = "tacz";
    public static final String PROBE_MARKER = "living_gotham:tacz_probe";
    private static final ResourceLocation PROBE_GUN_ID = ResourceLocation.fromNamespaceAndPath("tacz", "glock_17");
    private static final Map<UUID, PendingShot> PENDING_SHOTS = new HashMap<>();

    private TaczIntegration() {
    }

    public static Snapshot inspect() {
        return new Snapshot(
                TimelessAPI.getAllCommonGunIndex().size(),
                TimelessAPI.getAllCommonAmmoIndex().size(),
                TimelessAPI.getAllCommonAttachmentIndex().size(),
                TimelessAPI.getCommonGunIndex(PROBE_GUN_ID).isPresent()
        );
    }

    public static ArmedProbe startDevShot(ServerPlayer player) {
        if (!DevWorldSafety.isDisposableDevWorld(player.serverLevel())) {
            throw new IllegalStateException("refusing TaCZ write probe outside a world whose folder name contains DEV");
        }
        if (TimelessAPI.getCommonGunIndex(PROBE_GUN_ID).isEmpty()) {
            throw new IllegalStateException("TaCZ logical gun is unavailable: " + PROBE_GUN_ID);
        }

        Vec3 horizontal = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
        if (horizontal.lengthSqr() < 0.0001D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontal = horizontal.normalize();
        Vec3 shooterPosition = player.position().add(horizontal.scale(2.0D));
        Vec3 targetPosition = shooterPosition.add(horizontal.scale(8.0D));

        Zombie target = new Zombie(player.serverLevel());
        target.moveTo(targetPosition.x, targetPosition.y, targetPosition.z, 0.0F, 0.0F);
        target.setNoAi(true);
        target.setSilent(true);
        target.setCustomName(Component.literal("Living Gotham TaCZ Probe Target"));
        target.getPersistentData().putBoolean(PROBE_MARKER, true);

        ArmorStand shooter = new ArmorStand(player.serverLevel(), shooterPosition.x, shooterPosition.y, shooterPosition.z);
        shooter.setNoGravity(true);
        shooter.setShowArms(true);
        shooter.setCustomName(Component.literal("Living Gotham TaCZ Probe Shooter"));
        shooter.setCustomNameVisible(true);
        shooter.getPersistentData().putBoolean(PROBE_MARKER, true);

        aimAt(shooter, target.getEyePosition());
        ItemStack gunStack = GunItemBuilder.create()
                .setId(PROBE_GUN_ID)
                .setFireMode(FireMode.SEMI)
                .setAmmoCount(17)
                .setAmmoInBarrel(true)
                .build();
        if (gunStack.isEmpty() || IGun.getIGunOrNull(gunStack) == null) {
            throw new IllegalStateException("TaCZ failed to build " + PROBE_GUN_ID);
        }
        shooter.setItemSlot(EquipmentSlot.MAINHAND, gunStack);

        if (!player.serverLevel().addFreshEntity(target) || !player.serverLevel().addFreshEntity(shooter)) {
            target.discard();
            shooter.discard();
            throw new IllegalStateException("could not add TaCZ probe entities to the DEV world");
        }

        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        operator.initialData();
        operator.draw(shooter::getMainHandItem);

        long now = player.serverLevel().getGameTime();
        PENDING_SHOTS.put(shooter.getUUID(), new PendingShot(
                shooter.getUUID(), target.getUUID(), now + 20L, now + 120L));
        LivingGotham.LOGGER.info("[LG_PROBE] tacz_probe_armed shooter={} target={} gun={} dimension={}",
                shooter.getUUID(), target.getUUID(), PROBE_GUN_ID, player.serverLevel().dimension().location());
        return new ArmedProbe(shooter.getUUID(), target.getUUID(), PROBE_GUN_ID);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        Iterator<PendingShot> iterator = PENDING_SHOTS.values().iterator();
        while (iterator.hasNext()) {
            PendingShot pending = iterator.next();
            Entity entity = level.getEntity(pending.shooterId);
            if (!(entity instanceof ArmorStand shooter)) {
                if (gameTime >= pending.expiresAt) {
                    cleanup(level, pending);
                    iterator.remove();
                }
                continue;
            }

            if (!pending.fired && gameTime >= pending.fireAt) {
                ShootResult result = IGunOperator.fromLivingEntity(shooter)
                        .shoot(shooter::getXRot, shooter::getYRot);
                LivingGotham.LOGGER.info("[LG_PROBE] tacz_shoot_result shooter={} result={} ammo_after={}",
                        shooter.getUUID(), result, currentAmmo(shooter.getMainHandItem()));
                if (result == ShootResult.SUCCESS) {
                    pending.fired = true;
                    pending.cleanupAt = gameTime + 60L;
                } else if (result != ShootResult.IS_DRAWING && result != ShootResult.COOL_DOWN) {
                    pending.cleanupAt = gameTime + 20L;
                }
            }

            if (gameTime >= pending.cleanupAt || gameTime >= pending.expiresAt) {
                cleanup(level, pending);
                iterator.remove();
            }
        }
    }

    public static boolean isProbeEntity(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(PROBE_MARKER);
    }

    public static String gunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? "not-a-gun" : gun.getGunId(stack).toString();
    }

    private static int currentAmmo(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? -1 : gun.getCurrentAmmoCount(stack);
    }

    private static void aimAt(ArmorStand shooter, Vec3 target) {
        Vec3 origin = shooter.getEyePosition();
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
        shooter.setYRot(yaw);
        shooter.setYHeadRot(yaw);
        shooter.setYBodyRot(yaw);
        shooter.setXRot(pitch);
    }

    private static void cleanup(ServerLevel level, PendingShot pending) {
        Entity shooter = level.getEntity(pending.shooterId);
        Entity target = level.getEntity(pending.targetId);
        if (shooter != null) {
            shooter.discard();
        }
        if (target != null) {
            target.discard();
        }
        LivingGotham.LOGGER.info("[LG_PROBE] tacz_probe_cleanup shooter={} target={} fired={}",
                pending.shooterId, pending.targetId, pending.fired);
    }

    public record Snapshot(int guns, int ammoTypes, int attachments, boolean probeGunPresent) {
    }

    public record ArmedProbe(UUID shooterId, UUID targetId, ResourceLocation gunId) {
    }

    private static final class PendingShot {
        private final UUID shooterId;
        private final UUID targetId;
        private final long fireAt;
        private final long expiresAt;
        private long cleanupAt = Long.MAX_VALUE;
        private boolean fired;

        private PendingShot(UUID shooterId, UUID targetId, long fireAt, long expiresAt) {
            this.shooterId = shooterId;
            this.targetId = targetId;
            this.fireAt = fireAt;
            this.expiresAt = expiresAt;
        }
    }
}

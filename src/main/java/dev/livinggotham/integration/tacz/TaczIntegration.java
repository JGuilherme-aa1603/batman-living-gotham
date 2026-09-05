package dev.livinggotham.integration.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import dev.livinggotham.LivingGotham;
import dev.livinggotham.debug.DevWorldSafety;
import dev.livinggotham.entity.ModEntities;
import dev.livinggotham.entity.dev.LivingGothamGunTestEntity;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Narrow, version-pinned TaCZ 1.1.8-hotfix public-API probe boundary. */
public final class TaczIntegration {
    public static final String MOD_ID = "tacz";
    public static final String PROBE_MARKER = "living_gotham:tacz_probe";
    public static final String CONTROLLED_SPREAD = "LivingGothamControlledSpread";
    private static final String COMPLETED = "living_gotham:tacz_cycle_completed";
    private static final ResourceLocation PROBE_GUN_ID = id("tacz", "glock_17");
    private static final ResourceLocation PROBE_AMMO_ID = id("tacz", "9mm");
    private static final ResourceLocation PROBE_SUPPRESSOR_ID = id("tacz", "muzzle_silencer_mirage");
    private static final Map<UUID, FiringCycle> CYCLES = new HashMap<>();
    private static final Map<UUID, AccuracySample> ACCURACY = new HashMap<>();

    private TaczIntegration() {
    }

    public static Snapshot inspect() {
        return new Snapshot(TimelessAPI.getAllCommonGunIndex().size(),
                TimelessAPI.getAllCommonAmmoIndex().size(),
                TimelessAPI.getAllCommonAttachmentIndex().size(),
                TimelessAPI.getCommonGunIndex(PROBE_GUN_ID).isPresent());
    }

    /** Starts the full 3 rounds -> empty -> real inventory reload -> fire-again cycle. */
    public static ArmedProbe startDevEntityCycle(ServerPlayer player) {
        requireDev(player);
        LivingGothamGunTestEntity shooter = createShooter(player.serverLevel(), player, "reload", 1.0F,
                0.0F, true, 2, true, 30, 2.0D);
        Zombie target = createTarget(player.serverLevel(), shooter.position().add(direction(player).scale(12.0D)),
                "Living Gotham TaCZ Reload Target");
        aimAt(shooter, target.getEyePosition());
        initialize(shooter);
        long now = player.serverLevel().getGameTime();
        CYCLES.put(shooter.getUUID(), new FiringCycle(shooter.getUUID(), target.getUUID(), "reload", now + 20L, 4));
        logArmed(shooter, target, "reload");
        return new ArmedProbe(shooter.getUUID(), target.getUUID(), PROBE_GUN_ID);
    }

    /** Two controlled public-property samples, 20 shots each; bullets are discarded before damage. */
    public static void startAccuracyProbe(ServerPlayer player) {
        requireDev(player);
        startAccuracyProfile(player, "accuracy_low", 0.05F, -2.0D);
        startAccuracyProfile(player, "accuracy_high", 12.0F, -5.0D);
    }

    /** Two real hits whose server-side Pre event uses multipliers 1.0 and 0.5. */
    public static void startDamageProbe(ServerPlayer player) {
        requireDev(player);
        startFiniteProfile(player, "damage_1.0", 1.0F, 4.0D, 1, false);
        startFiniteProfile(player, "damage_0.5", 0.5F, 7.0D, 1, false);
    }

    /** One normal and one Mirage-suppressed shot; events and projectile flow stay observable. */
    public static void startSuppressorProbe(ServerPlayer player) {
        requireDev(player);
        startFiniteProfile(player, "suppressor_off", 1.0F, 10.0D, 1, false);
        startFiniteProfile(player, "suppressor_on", 1.0F, 13.0D, 1, true);
    }

    public static PersistenceSnapshot inspectPersisted(ServerPlayer player) {
        requireDev(player);
        int count = 0;
        String last = "none";
        for (LivingGothamGunTestEntity entity : player.serverLevel().getEntitiesOfClass(
                LivingGothamGunTestEntity.class, player.getBoundingBox().inflate(256.0D))) {
            count++;
            IGun gun = IGun.getIGunOrNull(entity.getMainHandItem());
            last = "uuid=" + entity.getUUID() + ", profile=" + entity.probeProfile()
                    + ", gun=" + gunId(entity.getMainHandItem())
                    + ", ammo=" + currentAmmo(entity.getMainHandItem())
                    + ", barrel=" + (gun != null && gun.hasBulletInBarrel(entity.getMainHandItem()))
                    + ", inventory=" + stackDescription(entity.ammunition())
                    + ", muzzle=" + muzzleId(entity.getMainHandItem())
                    + ", completed=" + entity.getPersistentData().getBoolean(COMPLETED);
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_entity_persistence {}", last);
        }
        return new PersistenceSnapshot(count, last);
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<FiringCycle> iterator = CYCLES.values().iterator();
        while (iterator.hasNext()) {
            FiringCycle cycle = iterator.next();
            Entity rawShooter = level.getEntity(cycle.shooterId);
            Entity rawTarget = level.getEntity(cycle.targetId);
            if (!(rawShooter instanceof LivingGothamGunTestEntity shooter) || !(rawTarget instanceof Zombie target)) {
                if (now > cycle.nextActionAt + 400L) {
                    iterator.remove();
                }
                continue;
            }
            IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
            aimAt(shooter, target.getEyePosition());

            if (cycle.reloadRequested) {
                ReloadState state = operator.getSynReloadState();
                if (cycle.lastReloadState != state.getStateType()) {
                    LivingGotham.LOGGER.info("[LG_PROBE] tacz_reload_state shooter={} state={} countdown={} gun_ammo={} inventory={}",
                            shooter.getUUID(), state.getStateType(), state.getCountDown(),
                            currentAmmo(shooter.getMainHandItem()), stackDescription(shooter.ammunition()));
                    cycle.lastReloadState = state.getStateType();
                }
                if (cycle.sawReloading && !state.getStateType().isReloading()
                        && currentAmmo(shooter.getMainHandItem()) > 0) {
                    cycle.reloadCompleted = true;
                    cycle.reloadRequested = false;
                    cycle.nextActionAt = now + 10L;
                    LivingGotham.LOGGER.info("[LG_PROBE] tacz_reload_complete shooter={} elapsed_ticks={} gun_ammo={} inventory={}",
                            shooter.getUUID(), now - cycle.reloadAt,
                            currentAmmo(shooter.getMainHandItem()), stackDescription(shooter.ammunition()));
                } else if (state.getStateType().isReloading()) {
                    cycle.sawReloading = true;
                }
                continue;
            }

            if (now < cycle.nextActionAt) {
                continue;
            }
            if (cycle.shots >= cycle.shotsRequired) {
                finish(shooter, target, cycle);
                iterator.remove();
                continue;
            }

            ShootResult result = operator.shoot(shooter::getXRot, shooter::getYRot);
            int gunAmmo = currentAmmo(shooter.getMainHandItem());
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_entity_shoot profile={} shooter={} result={} shot={} gun_ammo={} barrel={} inventory={}",
                    cycle.profile, shooter.getUUID(), result, cycle.shots + 1, gunAmmo,
                    hasBarrelRound(shooter.getMainHandItem()), stackDescription(shooter.ammunition()));
            if (result == ShootResult.SUCCESS) {
                cycle.shots++;
                cycle.nextActionAt = now + 8L;
            } else if (result == ShootResult.NO_AMMO && cycle.profile.equals("reload") && !cycle.reloadCompleted) {
                cycle.reloadRequested = true;
                cycle.reloadAt = now;
                operator.reload();
                ReloadState state = operator.getSynReloadState();
                cycle.lastReloadState = state.getStateType();
                cycle.sawReloading = state.getStateType().isReloading();
                LivingGotham.LOGGER.info("[LG_PROBE] tacz_reload_requested shooter={} state={} gun_ammo={} inventory={}",
                        shooter.getUUID(), state.getStateType(), gunAmmo, stackDescription(shooter.ammunition()));
            } else {
                cycle.nextActionAt = now + 3L;
            }
        }
    }

    public static void applyControlledProperties(AttachmentPropertyEvent event) {
        if (!event.getGunItem().hasTag() || !event.getGunItem().getTag().contains(CONTROLLED_SPREAD)) {
            return;
        }
        float spread = event.getGunItem().getTag().getFloat(CONTROLLED_SPREAD);
        Map<InaccuracyType, Float> values = new EnumMap<>(InaccuracyType.class);
        for (InaccuracyType type : InaccuracyType.values()) {
            values.put(type, spread);
        }
        event.getCacheProperty().setCache(GunProperties.INACCURACY, values);
        event.getCacheProperty().setCache(GunProperties.AIM_INACCURACY, values);
    }

    public static void observeProjectile(EntityKineticBullet bullet) {
        if (!(bullet.getOwner() instanceof LivingGothamGunTestEntity shooter)) {
            return;
        }
        AccuracySample sample = ACCURACY.get(shooter.getUUID());
        if (sample == null) {
            return;
        }
        double deviation = TaczProbePolicy.angularDeviationDegrees(
                Vec3.directionFromRotation(shooter.getXRot(), shooter.getYRot()), bullet.getDeltaMovement());
        sample.count++;
        sample.sum += deviation;
        sample.max = Math.max(sample.max, deviation);
        bullet.discard();
        if (sample.count >= sample.expected) {
            LivingGotham.LOGGER.info("[LG_PROBE] tacz_accuracy_result profile={} configured={} shots={} mean_degrees={} max_degrees={}",
                    sample.profile, sample.configured, sample.count, sample.sum / sample.count, sample.max);
            ACCURACY.remove(shooter.getUUID());
        }
    }

    public static boolean isProbeEntity(Entity entity) {
        return entity instanceof LivingGothamGunTestEntity
                || entity != null && entity.getPersistentData().getBoolean(PROBE_MARKER);
    }

    public static String gunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? "not-a-gun" : gun.getGunId(stack).toString();
    }

    public static float damageMultiplier(Entity entity) {
        return entity instanceof LivingGothamGunTestEntity test ? test.damageMultiplier() : 1.0F;
    }

    public static String profile(Entity entity) {
        return entity instanceof LivingGothamGunTestEntity test ? test.probeProfile() : "legacy";
    }

    public static String suppressorSnapshot(ItemStack gunStack, IGunOperator operator) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        Pair<Integer, Boolean> silence = operator.getCacheProperty().getCache(GunProperties.SILENCE);
        return "muzzle=" + (gun == null ? "none" : gun.getAttachmentId(gunStack, AttachmentType.MUZZLE))
                + ", silence_distance=" + (silence == null ? "null" : silence.first())
                + ", silence_sound=" + (silence == null ? "null" : silence.second());
    }

    private static void startAccuracyProfile(ServerPlayer player, String profile, float spread, double lateral) {
        LivingGothamGunTestEntity shooter = createShooter(player.serverLevel(), player, profile, 1.0F,
                spread, false, 24, true, 0, lateral);
        Zombie target = createTarget(player.serverLevel(), shooter.position().add(direction(player).scale(20.0D)),
                "Living Gotham Accuracy Target");
        aimAt(shooter, target.getEyePosition());
        initialize(shooter);
        long now = player.serverLevel().getGameTime();
        CYCLES.put(shooter.getUUID(), new FiringCycle(shooter.getUUID(), target.getUUID(), profile, now + 20L, 20));
        ACCURACY.put(shooter.getUUID(), new AccuracySample(profile, spread, 20));
        logArmed(shooter, target, profile);
    }

    private static void startFiniteProfile(ServerPlayer player, String profile, float damage, double lateral,
                                           int shots, boolean suppressed) {
        LivingGothamGunTestEntity shooter = createShooter(player.serverLevel(), player, profile, damage,
                0.0F, suppressed, shots + 1, true, 0, lateral);
        Zombie target = createTarget(player.serverLevel(), shooter.position().add(direction(player).scale(10.0D)),
                "Living Gotham " + profile + " Target");
        aimAt(shooter, target.getEyePosition());
        IGunOperator operator = initialize(shooter);
        LivingGotham.LOGGER.info("[LG_PROBE] tacz_suppressor_state profile={} {}", profile,
                suppressorSnapshot(shooter.getMainHandItem(), operator));
        long now = player.serverLevel().getGameTime();
        CYCLES.put(shooter.getUUID(), new FiringCycle(shooter.getUUID(), target.getUUID(), profile, now + 20L, shots));
        logArmed(shooter, target, profile);
    }

    private static LivingGothamGunTestEntity createShooter(ServerLevel level, ServerPlayer player, String profile,
                                                            float damage, float spread, boolean suppressed,
                                                            int magazine, boolean barrel, int reserveAmmo,
                                                            double lateral) {
        Vec3 forward = direction(player);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 position = player.position().add(forward.scale(3.0D)).add(side.scale(lateral));
        LivingGothamGunTestEntity shooter = ModEntities.GUN_TEST.get().create(level);
        if (shooter == null) {
            throw new IllegalStateException("could not construct living_gotham:gun_test");
        }
        shooter.moveTo(position.x, position.y, position.z, player.getYRot(), 0.0F);
        shooter.setCustomName(Component.literal("Living Gotham Gun Test [" + profile + "]"));
        shooter.setCustomNameVisible(true);
        shooter.getPersistentData().putBoolean(PROBE_MARKER, true);
        ItemStack gun = buildGun(magazine, barrel, suppressed, spread);
        ItemStack ammo = reserveAmmo > 0
                ? AmmoItemBuilder.create().setId(PROBE_AMMO_ID).setCount(reserveAmmo).build()
                : ItemStack.EMPTY;
        shooter.configure(profile, gun, ammo, damage, spread);
        if (!level.addFreshEntity(shooter)) {
            throw new IllegalStateException("could not add Living Gotham gun test entity");
        }
        return shooter;
    }

    private static Zombie createTarget(ServerLevel level, Vec3 position, String name) {
        Zombie target = new Zombie(level);
        target.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        target.setNoAi(true);
        target.setSilent(true);
        target.setAbsorptionAmount(100.0F);
        target.setCustomName(Component.literal(name));
        target.getPersistentData().putBoolean(PROBE_MARKER, true);
        if (!level.addFreshEntity(target)) {
            throw new IllegalStateException("could not add TaCZ target");
        }
        return target;
    }

    private static ItemStack buildGun(int magazine, boolean barrel, boolean suppressed, float spread) {
        GunItemBuilder builder = GunItemBuilder.create().setId(PROBE_GUN_ID).setFireMode(FireMode.SEMI)
                .setAmmoCount(magazine).setAmmoInBarrel(barrel);
        if (suppressed) {
            builder.putAttachment(AttachmentType.MUZZLE, PROBE_SUPPRESSOR_ID);
        }
        ItemStack gun = builder.build();
        if (gun.isEmpty() || IGun.getIGunOrNull(gun) == null) {
            throw new IllegalStateException("TaCZ failed to build " + PROBE_GUN_ID);
        }
        gun.getOrCreateTag().putFloat(CONTROLLED_SPREAD, spread);
        return gun;
    }

    private static IGunOperator initialize(LivingGothamGunTestEntity shooter) {
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        operator.initialData();
        operator.draw(shooter::getMainHandItem);
        operator.aim(true);
        return operator;
    }

    private static void finish(LivingGothamGunTestEntity shooter, Zombie target, FiringCycle cycle) {
        shooter.getPersistentData().putBoolean(COMPLETED, true);
        target.discard();
        LivingGotham.LOGGER.info("[LG_PROBE] tacz_cycle_complete profile={} shooter={} shots={} reload_completed={} gun_ammo={} barrel={} inventory={} persisted=true",
                cycle.profile, shooter.getUUID(), cycle.shots, cycle.reloadCompleted,
                currentAmmo(shooter.getMainHandItem()), hasBarrelRound(shooter.getMainHandItem()),
                stackDescription(shooter.ammunition()));
    }

    private static void aimAt(LivingGothamGunTestEntity shooter, Vec3 target) {
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

    private static Vec3 direction(ServerPlayer player) {
        Vec3 horizontal = new Vec3(player.getLookAngle().x, 0.0D, player.getLookAngle().z);
        return horizontal.lengthSqr() < 0.0001D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
    }

    private static boolean hasBarrelRound(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gun.hasBulletInBarrel(stack);
    }

    private static int currentAmmo(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? -1 : gun.getCurrentAmmoCount(stack);
    }

    private static String muzzleId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? "none" : String.valueOf(gun.getAttachmentId(stack, AttachmentType.MUZZLE));
    }

    private static String stackDescription(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getCount() + "x" + stack.getItem();
    }

    private static void requireDev(ServerPlayer player) {
        if (!DevWorldSafety.isDisposableDevWorld(player.serverLevel())) {
            throw new IllegalStateException("refusing TaCZ write probe outside a disposable DEV world");
        }
        if (TimelessAPI.getCommonGunIndex(PROBE_GUN_ID).isEmpty()) {
            throw new IllegalStateException("TaCZ logical gun is unavailable: " + PROBE_GUN_ID);
        }
    }

    private static void logArmed(LivingGothamGunTestEntity shooter, Zombie target, String profile) {
        LivingGotham.LOGGER.info("[LG_PROBE] tacz_entity_armed profile={} shooter={} target={} gun={} gun_ammo={} barrel={} inventory={} dimension={}",
                profile, shooter.getUUID(), target.getUUID(), PROBE_GUN_ID,
                currentAmmo(shooter.getMainHandItem()), hasBarrelRound(shooter.getMainHandItem()),
                stackDescription(shooter.ammunition()), shooter.level().dimension().location());
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public record Snapshot(int guns, int ammoTypes, int attachments, boolean probeGunPresent) {
    }

    public record ArmedProbe(UUID shooterId, UUID targetId, ResourceLocation gunId) {
    }

    public record PersistenceSnapshot(int count, String detail) {
    }

    private static final class FiringCycle {
        private final UUID shooterId;
        private final UUID targetId;
        private final String profile;
        private final int shotsRequired;
        private long nextActionAt;
        private int shots;
        private boolean reloadRequested;
        private boolean sawReloading;
        private boolean reloadCompleted;
        private long reloadAt;
        private ReloadState.StateType lastReloadState;

        private FiringCycle(UUID shooterId, UUID targetId, String profile, long nextActionAt, int shotsRequired) {
            this.shooterId = shooterId;
            this.targetId = targetId;
            this.profile = profile;
            this.nextActionAt = nextActionAt;
            this.shotsRequired = shotsRequired;
        }
    }

    private static final class AccuracySample {
        private final String profile;
        private final float configured;
        private final int expected;
        private int count;
        private double sum;
        private double max;

        private AccuracySample(String profile, float configured, int expected) {
            this.profile = profile;
            this.configured = configured;
            this.expected = expected;
        }
    }
}

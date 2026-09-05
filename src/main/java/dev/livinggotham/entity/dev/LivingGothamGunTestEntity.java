package dev.livinggotham.entity.dev;

import dev.livinggotham.integration.tacz.TaczProbePolicy;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Disposable technical shooter. This is deliberately not a gameplay criminal.
 *
 * <p>The one-slot item handler is real TaCZ ammunition storage. The held gun and
 * ammunition serialize through normal entity NBT so save/reopen behavior can be measured.</p>
 */
public final class LivingGothamGunTestEntity extends Zombie {
    private static final String AMMO_INVENTORY = "LivingGothamAmmoInventory";
    private static final String DAMAGE_MULTIPLIER = "LivingGothamDamageMultiplier";
    private static final String INACCURACY = "LivingGothamInaccuracy";
    private static final String PROBE_PROFILE = "LivingGothamProbeProfile";

    private final ItemStackHandler ammunition = new ItemStackHandler(1);
    private LazyOptional<IItemHandler> ammunitionCapability = LazyOptional.of(() -> ammunition);
    private float damageMultiplier = 1.0F;
    private float inaccuracy = TaczProbePolicy.DEFAULT_INACCURACY;
    private String probeProfile = "reload";

    public LivingGothamGunTestEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public void configure(String profile, ItemStack gun, ItemStack ammo, float damage, float spread) {
        this.probeProfile = profile;
        this.damageMultiplier = damage;
        this.inaccuracy = spread;
        setItemSlot(EquipmentSlot.MAINHAND, gun);
        ammunition.setStackInSlot(0, ammo);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public ItemStack ammunition() {
        return ammunition.getStackInSlot(0);
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public float inaccuracy() {
        return inaccuracy;
    }

    public String probeProfile() {
        return probeProfile;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                      @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return ammunitionCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        ammunitionCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        ammunitionCapability = LazyOptional.of(() -> ammunition);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put(AMMO_INVENTORY, ammunition.serializeNBT());
        tag.putFloat(DAMAGE_MULTIPLIER, damageMultiplier);
        tag.putFloat(INACCURACY, inaccuracy);
        tag.putString(PROBE_PROFILE, probeProfile);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(AMMO_INVENTORY)) {
            ammunition.deserializeNBT(tag.getCompound(AMMO_INVENTORY));
        }
        damageMultiplier = tag.contains(DAMAGE_MULTIPLIER) ? tag.getFloat(DAMAGE_MULTIPLIER) : 1.0F;
        inaccuracy = tag.contains(INACCURACY) ? tag.getFloat(INACCURACY) : TaczProbePolicy.DEFAULT_INACCURACY;
        probeProfile = tag.contains(PROBE_PROFILE) ? tag.getString(PROBE_PROFILE) : "reload";
        setNoAi(true);
        setPersistenceRequired();
    }
}

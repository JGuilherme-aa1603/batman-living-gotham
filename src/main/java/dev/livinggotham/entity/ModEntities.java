package dev.livinggotham.entity;

import dev.livinggotham.LivingGotham;
import dev.livinggotham.entity.dev.LivingGothamGunTestEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registrations required only by the technical foundation. */
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LivingGotham.MOD_ID);

    public static final RegistryObject<EntityType<LivingGothamGunTestEntity>> GUN_TEST = ENTITY_TYPES.register(
            "gun_test",
            () -> EntityType.Builder.<LivingGothamGunTestEntity>of(LivingGothamGunTestEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(LivingGotham.MOD_ID + ":gun_test")
    );

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    @Mod.EventBusSubscriber(modid = LivingGotham.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {
        private Attributes() {
        }

        @SubscribeEvent
        public static void create(EntityAttributeCreationEvent event) {
            event.put(GUN_TEST.get(), LivingGothamGunTestEntity.createAttributes().build());
        }
    }
}

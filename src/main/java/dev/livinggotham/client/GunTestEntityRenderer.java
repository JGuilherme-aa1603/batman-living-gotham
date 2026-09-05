package dev.livinggotham.client;

import dev.livinggotham.LivingGotham;
import dev.livinggotham.entity.ModEntities;
import dev.livinggotham.entity.dev.LivingGothamGunTestEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary vanilla-zombie visualization; not a final NPC renderer or animation system. */
public final class GunTestEntityRenderer
        extends HumanoidMobRenderer<LivingGothamGunTestEntity, ZombieModel<LivingGothamGunTestEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");

    private GunTestEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(LivingGothamGunTestEntity entity) {
        return TEXTURE;
    }

    @Mod.EventBusSubscriber(modid = LivingGotham.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GUN_TEST.get(), GunTestEntityRenderer::new);
        }
    }
}

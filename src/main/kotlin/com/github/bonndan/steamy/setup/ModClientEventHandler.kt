package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.rendering.ChainModel
import com.github.bonndan.steamy.rendering.TrainCarRenderer
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent


@EventBusSubscriber(modid = SteamyMod.MOD_ID, value = [Dist.CLIENT])
object ModClientEventHandler {

    val MINECART_LAYER = ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("minecraft", "minecart"), "main")


    @SubscribeEvent
    fun onRegisterEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {

        event.registerEntityRenderer(ModEntityTypes.LOCOMOTIVE.get()) { ctx: EntityRendererProvider.Context ->
            TrainCarRenderer(ctx, MINECART_LAYER,ResourceLocation.withDefaultNamespace("textures/entity/minecart.png"))
        }

        event.registerEntityRenderer(ModEntityTypes.WAGON.get()) { ctx: EntityRendererProvider.Context ->
            TrainCarRenderer(ctx, MINECART_LAYER, ResourceLocation.withDefaultNamespace("textures/entity/minecart.png"))
        }
    }

    @SubscribeEvent
    fun onRegisterLayerDefinitions(event: EntityRenderersEvent.RegisterLayerDefinitions) {
        event.registerLayerDefinition(ChainModel.LAYER_LOCATION, ChainModel::createBodyLayer)
    }

    @SubscribeEvent
    fun buildTabContents(event: BuildCreativeModeTabContentsEvent) {
        ModBlocks.buildCreativeTab(event)
        ModItems.buildCreativeTab(event)
    }


}
package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.train.LinkableCart
import com.github.bonndan.steamy.train.SpringItem
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MinecartRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult.SUCCESS
import net.minecraft.world.item.ShearsItem
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent


@EventBusSubscriber(modid = SteamyMod.MOD_ID, value = [Dist.CLIENT])
object ModClientEventHandler {

    val MINECART_LAYER = ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("minecraft", "minecart"), "main")

    @SubscribeEvent
    fun onRegisterEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {

        event.registerEntityRenderer(ModEntityTypes.LOCOMOTIVE.get()) { ctx: EntityRendererProvider.Context ->
            MinecartRenderer(ctx, MINECART_LAYER)
        }

        event.registerEntityRenderer(ModEntityTypes.WAGON.get()) { ctx: EntityRendererProvider.Context ->
            MinecartRenderer(ctx, MINECART_LAYER)
        }
    }

    @SubscribeEvent
    fun buildTabContents(event: BuildCreativeModeTabContentsEvent) {
        ModBlocks.buildCreativeTab(event)
        ModItems.buildCreativeTab(event)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun entityInteract(event: PlayerInteractEvent.EntityInteract) {

        if (event.itemStack.isEmpty) return

        val item = event.itemStack.item
        if (item is SpringItem) {
            if (event.target is LinkableCart<*>) {
                item.onUsedOnEntity(event.itemStack, event.entity, event.level, event.target)
                event.setCanceled(true)
                event.cancellationResult = SUCCESS
            }
        }

        if (item is ShearsItem) {
            val target = event.target
            if (target is LinkableCart<*>) {
                target.handleShearsCut()
                event.setCanceled(true)
                event.cancellationResult = SUCCESS
            }
        }
    }

}
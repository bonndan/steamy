package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.train.LinkableCart
import com.github.bonndan.steamy.train.SpringItem
import net.minecraft.world.InteractionResult.SUCCESS
import net.minecraft.world.item.ShearsItem
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent


@EventBusSubscriber(modid = SteamyMod.MOD_ID)
object ModServerEventHandler {

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


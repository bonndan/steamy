package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.locomotive.entity.LocomotiveEntity
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.InputEvent
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = SteamyMod.MOD_ID, value = [Dist.CLIENT])
object ForgeClientEventHandler {

    @SubscribeEvent
    fun onKeyInputEvent(event: InputEvent.Key) {

        val player = Minecraft.getInstance().player

        if (event.key != GLFW.GLFW_KEY_LEFT_CONTROL && event.key != GLFW.GLFW_KEY_SPACE) {
            return
        }

        if (player !is LocalPlayer || !player.isPassenger) {
            return
        }

        val vehicle = player.vehicle

        if (event.action == InputConstants.PRESS && vehicle is LocomotiveEntity) {
            when (event.key) {
                GLFW.GLFW_KEY_SPACE -> vehicle.throttleUp()
                GLFW.GLFW_KEY_LEFT_CONTROL -> vehicle.throttleDown()
            }
            return
        }
    }
}
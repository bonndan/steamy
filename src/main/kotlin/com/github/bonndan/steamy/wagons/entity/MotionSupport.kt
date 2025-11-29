package com.github.bonndan.steamy.wagons.entity

import net.minecraft.core.Direction
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.Vec3

object MotionSupport {

    /**
     * Get the horizontal direction the cart is moving towards (if moving).
     */
    fun getCartDirection(cart: AbstractMinecart): Direction {

        val diff = Vec3(cart.x - cart.xo, 0.0, cart.z - cart.zo)
        val horizontalDirection = Direction.getNearest(diff.normalize().x.toInt(), 0, diff.normalize().z.toInt(), null)

        return horizontalDirection ?: cart.motionDirection
    }
}
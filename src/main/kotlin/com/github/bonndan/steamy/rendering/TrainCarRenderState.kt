package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.train.LinkableCart
import net.minecraft.client.renderer.entity.state.MinecartRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.Vec3
import java.util.*

class TrainCarRenderState<T> : MinecartRenderState() where T : AbstractMinecart, T : LinkableCart<T> {

    var trainCar: LinkableCart<T>? = null
    var leader: Optional<LinkableCart<T>> = Optional.empty()
    var follower: Optional<LinkableCart<T>> = Optional.empty()
    var customName: Component? = null
    var hasCustomName: Boolean = false
    var position: Vec3 = Vec3.ZERO
    var oldPosition: Vec3 = Vec3.ZERO
    var damage: Float = 0f
    var xRotO: Float = 0f
    var yRotO: Float = 0f

    fun updateFromEntity(entity: LinkableCart<T>, partialTicks: Float) {
        this.trainCar = entity
        this.leader = entity.getLeader()
        this.follower = entity.getFollower()
        this.partialTick = partialTicks

        entity as AbstractMinecart
        this.customName = entity.customName
        this.hasCustomName = entity.hasCustomName()
        this.position = entity.getPosition(partialTicks)
        this.oldPosition = Vec3(entity.xOld, entity.yOld, entity.zOld)
        this.hurtTime = entity.hurtTime.toFloat()
        this.damage = entity.damage
        this.hurtDir = entity.hurtDir
        
        // Update parent MinecartRenderState fields
        this.x = entity.x
        this.y = entity.y
        this.z = entity.z
        this.xRot = entity.xRot
        this.yRot = entity.yRot
        this.xRotO = entity.xRotO
        this.yRotO = entity.yRotO
    }
}

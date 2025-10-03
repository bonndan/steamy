package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.train.AbstractTrainCarEntity
import net.minecraft.client.renderer.entity.state.MinecartRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.util.*

class TrainCarRenderState : MinecartRenderState() {

    var trainCar: AbstractTrainCarEntity? = null
    var leader: Optional<AbstractTrainCarEntity> = Optional.empty()
    var follower: Optional<AbstractTrainCarEntity> = Optional.empty()
    var customName: Component? = null
    var hasCustomName: Boolean = false
    var position: Vec3 = Vec3.ZERO
    var oldPosition: Vec3 = Vec3.ZERO
    var hurtTime: Int = 0
    var damage: Float = 0f
    var hurtDir: Int = 0
    var xRotO: Float = 0f
    var yRotO: Float = 0f
    
    fun updateFromEntity(entity: AbstractTrainCarEntity, partialTicks: Float) {
        this.trainCar = entity
        this.leader = entity.getLeader()
        this.follower = entity.getFollower()
        this.customName = entity.customName
        this.hasCustomName = entity.hasCustomName()
        this.position = entity.getPosition(partialTicks)
        this.oldPosition = Vec3(entity.xOld, entity.yOld, entity.zOld)
        this.hurtTime = entity.hurtTime
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

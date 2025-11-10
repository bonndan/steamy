package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.train.LinkableCart
import net.minecraft.client.renderer.entity.state.MinecartRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.vehicle.AbstractMinecart
import java.util.*

class TrainCarRenderState<T> : MinecartRenderState() where T : AbstractMinecart, T : LinkableCart<T> {

    var trainCar: LinkableCart<T>? = null
    var leader: Optional<LinkableCart<T>> = Optional.empty()
    var follower: Optional<LinkableCart<T>> = Optional.empty()

    fun updateFromEntity(entity: LinkableCart<T>) {
        this.trainCar = entity
        this.leader = entity.getLeader()
        this.follower = entity.getFollower()
    }
}

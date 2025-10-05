package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.train.LinkableCart
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.Vec3


interface RenderWithAttachmentPoints<T>  where T : AbstractMinecart, T: LinkableCart<T> {

    fun renderCarAndGetAttachmentPoints(
        car: LinkableCart<T>,
        yaw: Float,
        partialTicks: Float,
        pose: PoseStack?,
        buffer: MultiBufferSource?,
        packedLight: Int
    ): Pair<Vec3, Vec3>
}

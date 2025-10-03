package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.train.AbstractTrainCarEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3


interface RenderWithAttachmentPoints {
    fun renderCarAndGetAttachmentPoints(
        car: AbstractTrainCarEntity,
        yaw: Float,
        partialTicks: Float,
        pose: PoseStack?,
        buffer: MultiBufferSource?,
        packedLight: Int
    ): Pair<Vec3, Vec3>
}

package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.SteamyMod.Companion.MOD_ID
import com.github.bonndan.steamy.train.LinkableCart
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.Vec3
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil

open class TrainCarRenderer<T>(
    context: EntityRendererProvider.Context,
    layerLocation: ModelLayerLocation,
    private val modelResourceLocation: ResourceLocation
) : AbstractMinecartRenderer<T, TrainCarRenderState<T>>(
    context,
    layerLocation
) where T : AbstractMinecart, T : LinkableCart<T> {

    private val chainModel: ChainModel = ChainModel(context.bakeLayer(ChainModel.LAYER_LOCATION))

    override fun render(
        renderState: TrainCarRenderState<T>,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
    ) {
        super.render(renderState, poseStack, bufferSource, packedLight)
        val car: LinkableCart<T> = renderState.trainCar ?: return

        if (car.getLeader().isPresent) {

            val from = if (renderState.isNewRender) renderState.renderPos else renderState.posOnRail

            val leader = car.getLeader().get()
            val tooo = leader as AbstractMinecart
            val to = tooo.getPosition(renderState.partialTick)

            if (from == null || to == null) return
            getAndRenderChain(from, to, poseStack, bufferSource, packedLight)
        }

    }

    private fun getAndRenderChain(
        from: Vec3,
        to: Vec3,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()
        val vec: Vec3 = from.vectorTo(to)
        val dist: Double = vec.length()
        val segments = ceil(dist * 4).toInt()

        // TODO: fix pitch
        poseStack.mulPose(Axis.YP.rotation(-atan2(vec.z, vec.x).toFloat()))
        poseStack.mulPose(Axis.ZP.rotation((asin(vec.y / dist)).toFloat()))
        poseStack.pushPose()
        val ivertexbuilderChain: VertexConsumer = buffer.getBuffer(chainModel.renderType(CHAIN_TEXTURE))
        for (i in 1..<segments) {
            poseStack.pushPose()
            poseStack.translate(i / 4.0, 0.2, 0.0)
            chainModel.renderToBuffer(
                poseStack,
                ivertexbuilderChain,
                packedLight,
                OverlayTexture.NO_OVERLAY
            )
            poseStack.popPose()
        }

        poseStack.popPose()
        poseStack.popPose()
    }

    // First - front anchor point
    // Second - back anchor point
    // Override this to change anchor points for larger or smaller cars
    // TODO use in rendering chain
    fun getAttachmentPoints(chainCentre: Vec3, trackDirection: Vec3): Pair<Vec3, Vec3> {
        return Pair(
            chainCentre.add(trackDirection.scale(.2)),
            chainCentre.add(trackDirection.scale(-.2))
        )
    }

    override fun shouldRender(
        entity: T,
        pCamera: Frustum,
        pCamX: Double,
        pCamY: Double,
        pCamZ: Double
    ): Boolean {
        return true
    }

    override fun createRenderState(): TrainCarRenderState<T> {
        return TrainCarRenderState()
    }

    override fun extractRenderState(
        entity: T,
        renderState: TrainCarRenderState<T>,
        partialTick: Float
    ) {
        super.extractRenderState(entity, renderState, partialTick)
        renderState.updateFromEntity(entity)
    }

    companion object {
        private val CHAIN_TEXTURE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/entity/chain.png")
    }
}

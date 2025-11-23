package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.SteamyMod.Companion.MOD_ID
import com.github.bonndan.steamy.train.LinkableCart
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.event.RenderNameTagEvent.DoRender
import net.neoforged.neoforge.common.NeoForge
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


    /**
     * AbstractMinecartRenderer.render copied and modified to add block rendering
     *
     */
    override fun render(
        renderState: TrainCarRenderState<T>,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {

        super.render(renderState, poseStack, bufferSource, packedLight)


        //render chain
        val car: LinkableCart<T> = renderState.trainCar ?: return

        if (car.getLeader().isPresent) {

            var from = if (renderState.isNewRender) renderState.renderPos else renderState.posOnRail
            if (renderState.frontPos != null) {
                from = renderState.frontPos
            }
            if (renderState.frontPos!= null) {
                from = renderState.frontPos!!
            }

            val leader = car.getLeader().get()
            val leaderCart = leader as AbstractMinecart
            val carPos = leaderCart.getPosition(renderState.partialTick)
            val to =  carPos

            if (from == null || to == null) return
            poseStack.pushPose()
            poseStack.translate(calculateChainOffset(carPos, to))
            renderChain(from, to, poseStack, bufferSource, packedLight)
            poseStack.popPose()
        }
    }

    private fun calculateChainOffset(
        carPos: Vec3,
        to: Vec3
    ): Vec3 = carPos.subtract(to).add(0.0, 0.3, 0.0)

    private fun renderChain(
        from: Vec3,
        to: Vec3,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()

        val vec: Vec3 = from.vectorTo(to)
        // TODO: fix pitch
        poseStack.mulPose(Axis.YP.rotation(-atan2(vec.z, vec.x).toFloat()))
        val dist: Double = vec.length()
        poseStack.mulPose(Axis.ZP.rotation((asin(vec.y / dist)).toFloat()))
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
        poseStack.pushPose()

        val ivertexbuilderChain: VertexConsumer = buffer.getBuffer(chainModel.renderType(CHAIN_TEXTURE))
        val segments = ceil(dist * 4).toInt()
        for (i in 1..<segments) {
            poseStack.pushPose()
            poseStack.translate(i / 4.0, 0.0, 0.0)
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

    private fun renderNameTag(
        renderState: TrainCarRenderState<T>,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        val nameTag = renderState.nameTag ?: return

        val event = DoRender(
            renderState,
            nameTag,
            this,
            poseStack,
            bufferSource,
            packedLight,
            renderState.partialTick
        )

        if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
            this.renderNameTag(
                renderState,
                nameTag,
                poseStack,
                bufferSource,
                packedLight
            )
        }
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

        renderState.trainCar = entity
        renderState.leader = entity.getLeader()
        renderState.follower = entity.getFollower()
    }

    companion object {
        private val CHAIN_TEXTURE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/entity/chain.png")
    }
}

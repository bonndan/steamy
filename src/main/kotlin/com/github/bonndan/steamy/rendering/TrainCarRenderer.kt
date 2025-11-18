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
import net.minecraft.util.Mth
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
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

        //this is from EntityRenderer, leash state is omitted
        renderNameTag(renderState, poseStack, bufferSource, packedLight)

        // start rendering
        poseStack.pushPose()
        val i: Long = renderState.offsetSeed
        val f = (((i shr 16 and 7L).toFloat() + 0.5f) / 8.0f - 0.5f) * 0.004f
        val f1 = (((i shr 20 and 7L).toFloat() + 0.5f) / 8.0f - 0.5f) * 0.004f
        val f2 = (((i shr 24 and 7L).toFloat() + 0.5f) / 8.0f - 0.5f) * 0.004f
        poseStack.translate(f, f1, f2)

        //new
        renderState.translationOffset?.let {
            poseStack.translate(it.x, it.y, it.z)
        }

        poseStack.translate(0.0, 0.375, 0.0)
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - renderState.yRot))
        poseStack.mulPose(Axis.XN.rotationDegrees(renderState.pitch ?: 0f))


        // old from here
        val f3: Float = renderState.hurtTime
        if (f3 > 0.0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(f3) * f3 * renderState.damageTime / 10.0f * renderState.hurtDir))
        }

        val blockstate: BlockState = renderState.displayBlockState
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            poseStack.pushPose()
            poseStack.scale(0.75f, 0.75f, 0.75f)
            poseStack.translate(-0.5f, (renderState.displayOffset - 8) / 16.0f, 0.5f)
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0f))
            this.renderMinecartContents(renderState, blockstate, poseStack, bufferSource, packedLight)
            poseStack.popPose()
        }

        poseStack.scale(-1.0f, -1.0f, 1.0f)
        this.model.setupAnim(renderState)
        val vertexconsumer = bufferSource.getBuffer(this.model.renderType(modelResourceLocation))
        this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY)
        poseStack.popPose()


        //render chain
        val car: LinkableCart<T> = renderState.trainCar ?: return

        if (car.getLeader().isPresent) {

            var from = if (renderState.isNewRender) renderState.renderPos else renderState.posOnRail
            if (renderState.frontPos != null) {
                from = renderState.frontPos
            }
            if (car.linkingHandler.attachmentFrontPos != null) {
                from = car.linkingHandler.attachmentFrontPos!!
            }

            val leader = car.getLeader().get()
            val leaderCart = leader as AbstractMinecart
            val carPos = leaderCart.getPosition(renderState.partialTick)
            val to = if (leader.linkingHandler.attachmentBackPos != null) {
                leader.linkingHandler.attachmentBackPos!!
            } else carPos

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
        entity.calcTrackDirectionBasedValues(partialTick)?.let {
            renderState.pitch = it.pitch
            renderState.translationOffset = it.translationOffset
            renderState.yRot = it.yRot
            entity.linkingHandler.attachmentFrontPos = it.frontPos
            entity.linkingHandler.attachmentBackPos = it.backPos
        }
    }

    companion object {
        private val CHAIN_TEXTURE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/entity/chain.png")
    }
}

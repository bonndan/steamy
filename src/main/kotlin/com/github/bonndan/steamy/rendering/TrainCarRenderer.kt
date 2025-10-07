package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.SteamyMod.Companion.MOD_ID
import com.github.bonndan.steamy.train.LinkableCart
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

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
        val car = renderState.trainCar ?: return

        return renderFollowers(
            car = car,
            yaw = renderState.yRot,
            pPartialTicks = renderState.partialTick,
            pose = poseStack,
            buffer = bufferSource,
            packedLight,
            renderState
        )
    }

    private fun renderFollowers(
        car: LinkableCart<T>,
        yaw: Float,
        pPartialTicks: Float,
        pose: PoseStack,
        buffer: MultiBufferSource,
        pPackedLight: Int,
        renderState: TrainCarRenderState<T>
    ) {
        if (car.getLeader().isPresent) return

        pose.pushPose()

        // render
        var t: LinkableCart<T> = car
        var attachmentPoints =
            renderCarAndGetAttachmentPoints(car, yaw, pPartialTicks, pose, buffer, pPackedLight, renderState)

        while (t.getFollower().isPresent) {
            val nextT = t.getFollower().get()
            val cart = nextT as AbstractMinecart
            val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(nextT as AbstractMinecart)
            renderer as TrainCarRenderer<T>
            // translate to next train location
            val nextTPos: Vec3 = cart.getPosition(pPartialTicks)
            val tPos: Vec3 = (t as AbstractMinecart).getPosition(pPartialTicks)
            var offset: Vec3 = nextTPos.subtract(tPos)
            pose.translate(offset.x, offset.y, offset.z)
            val newAttachmentPoints: Pair<Vec3, Vec3> = renderer.renderCarAndGetAttachmentPoints(
                nextT,
                nextT.yRot,
                pPartialTicks,
                pose,
                buffer,
                pPackedLight,
                renderState
            )
            val from: Vec3 = newAttachmentPoints.first
            val to: Vec3 = attachmentPoints.second

            // translate to "from" position
            pose.pushPose()
            offset = from.subtract(nextTPos)
            pose.translate(offset.x, offset.y, offset.z)
            getAndRenderChain(from, to, pose, buffer, pPackedLight)
            pose.popPose()

            attachmentPoints = newAttachmentPoints

            t = nextT
        }

        pose.popPose()
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
        val segments = kotlin.math.ceil(dist * 4).toInt()

        // TODO: fix pitch
        poseStack.mulPose(Axis.YP.rotation(-kotlin.math.atan2(vec.z, vec.x).toFloat()))
        poseStack.mulPose(Axis.ZP.rotation((kotlin.math.asin(vec.y / dist)).toFloat()))
        poseStack.pushPose()
        val ivertexbuilderChain: VertexConsumer = buffer.getBuffer(chainModel.renderType(CHAIN_TEXTURE))
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

    // First - front anchor point
    // Second - back anchor point
    // Override this to change anchor points for larger or smaller cars
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
        renderState.updateFromEntity(entity, partialTick)
    }

    fun renderCarAndGetAttachmentPoints(
        entity: LinkableCart<T>,
        yaw: Float,
        partialTicks: Float,
        pose: PoseStack?,
        buffer: MultiBufferSource?,
        packedLight: Int,
        renderState: TrainCarRenderState<T>
    ): Pair<Vec3, Vec3> {

        var yaw = yaw
        val car = entity as AbstractMinecart
        var attach = Pair(
            car.getPosition(partialTicks).add(0.0, .44, 0.0),
            car.getPosition(partialTicks).add(0.0, .44, 0.0)
        )

        if (pose == null || buffer == null) return attach

        pose.pushPose()
        var i = car.id.toLong() * 493286711L
        i = i * i * 4392167121L + i * 98761L
        val f = (((i shr 16 and 7L).toFloat() + 0.5f) / 8.0f - 0.5f) * 0.004f
        val f1 = (((i shr 20 and 7L).toFloat() + 0.5f) / 8.0f - 0.5f) * 0.004f
        val f2 = (((i shr 24 and 7L).toFloat() + 0.5f) / 8.0f - 0.5f) * 0.004f
        pose.translate(f.toDouble(), f1.toDouble(), f2.toDouble())
        val d0: Double = Mth.lerp(partialTicks.toDouble(), car.xOld, car.x)
        val d1: Double = Mth.lerp(partialTicks.toDouble(), car.yOld, car.y)
        val d2: Double = Mth.lerp(partialTicks.toDouble(), car.zOld, car.z)
        val pos: Vec3? = entity.getPos(d0, d1, d2)
        var pitch: Float = Mth.lerp(partialTicks, car.xRotO, car.xRot)
        if (pos != null) {
            var forwardDir = entity.getPosOffs(d0, d1, d2, 0.3)
            var backDir: Vec3? = entity.getPosOffs(d0, d1, d2, -0.3)
            if (forwardDir == null) {
                forwardDir = pos
            }

            if (backDir == null) {
                backDir = pos
            }

            val centre = Vec3(pos.x, (forwardDir.y + backDir.y) / 2.0, pos.z)
            val offset: Vec3 = centre.subtract(d0, d1, d2)

            pose.translate(offset.x, offset.y, offset.z)
            var trackDirection: Vec3 = forwardDir.subtract(backDir)
            if (trackDirection.length() != 0.0) {
                trackDirection = trackDirection.normalize()
                yaw =
                    (kotlin.math.atan2(-trackDirection.z, -trackDirection.x) * 180.0 / Math.PI + 90).toFloat()
                pitch = (kotlin.math.atan(-trackDirection.y) * 73.0).toFloat()
            }

            val chainCentre: Vec3 = centre.add(0.0, .22, 0.0)
            attach = getAttachmentPoints(chainCentre, trackDirection)
        }

        pose.translate(0.0, 0.375, 0.0)
        pose.mulPose(Axis.YP.rotationDegrees(270.0f - yaw)) // extra 90 for standard minecart models
        pose.mulPose(Axis.XN.rotationDegrees(pitch))
        val f5 = car.hurtTime.toFloat() - partialTicks
        var f6: Float = car.damage - partialTicks
        if (f6 < 0.0f) {
            f6 = 0.0f
        }

        if (f5 > 0.0f) {
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(f5) * f5 * f6 / 10.0f * car.hurtDir.toFloat()))
        }

        //pose.translate(0.0, 1.1, 0.0) this extra offset was added in littlelogistics for their custom models
        pose.scale(-1.0f, -1.0f, 1.0f)

        this.model.setupAnim(renderState)
        val vertexconsumer: VertexConsumer = buffer.getBuffer(this.model.renderType(modelResourceLocation))
        this.model.renderToBuffer(
            pose,
            vertexconsumer,
            packedLight,
            OverlayTexture.NO_OVERLAY
        )
        renderBlockState(renderState, pose, buffer, packedLight)

        renderAdditional(entity, yaw, partialTicks, pose, buffer, packedLight)
        pose.popPose()

        if (car.hasCustomName()) {
            car.customName?.let { name ->
                this.renderNameTag(renderState, name, pose, buffer, packedLight)
            }
        }

        return attach
    }



    protected fun renderBlockState(
        renderState: TrainCarRenderState<T>,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        val blockstate: BlockState = renderState.displayBlockState
        if (blockstate.renderShape != RenderShape.INVISIBLE) {
            poseStack.pushPose()
            poseStack.scale(0.75f, 0.75f, 0.75f)
            poseStack.translate(-0.5f, (renderState.displayOffset - 16) / 16.0f, 0.5f)
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0f))
            this.renderMinecartContents(renderState, blockstate, poseStack, bufferSource, packedLight)
            poseStack.popPose()
        }
    }

    protected fun renderAdditional(
        pEntity: LinkableCart<T>?,
        pEntityYaw: Float,
        pPartialTicks: Float,
        pMatrixStack: PoseStack?,
        pBuffer: MultiBufferSource?,
        pPackedLight: Int
    ) {
        // Override in subclasses for additional rendering
    }

    companion object {
        private val CHAIN_TEXTURE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/entity/chain.png")
    }
}

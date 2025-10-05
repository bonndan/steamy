package com.github.bonndan.steamy.rendering

import com.github.bonndan.steamy.SteamyMod.Companion.MOD_ID
import com.github.bonndan.steamy.train.LinkableCart
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.Vec3
import java.util.function.Function

open class TrainCarRenderer<T>(
    context: EntityRendererProvider.Context,
    baseModel: Function<ModelPart, EntityModel<TrainCarRenderState<T>>>,
    layerLocation: ModelLayerLocation,
    baseTexture: ResourceLocation?
) : AbstractMinecartRenderer<T, TrainCarRenderState<T>>(context, layerLocation),
    RenderWithAttachmentPoints<T> where T : AbstractMinecart, T : LinkableCart<T> {

    private val entityModel: EntityModel<TrainCarRenderState<T>> = baseModel.apply(context.bakeLayer(layerLocation))
    private val texture: ResourceLocation =
        baseTexture ?: ResourceLocation.withDefaultNamespace("textures/entity/minecart.png")

    private val chainModel: ChainModel = ChainModel(context.bakeLayer(ChainModel.LAYER_LOCATION))

    override fun render(
        renderState: TrainCarRenderState<T>,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
    ) {
        val car = renderState.trainCar ?: return

        return render(
            car = car,
            yaw = renderState.yRot,
            pPartialTicks = 0.0f, // partialTicks werden in extractRenderState berechnet
            pose = poseStack,
            buffer = bufferSource,
            packedLight
        )
    }

    fun render(
        car: LinkableCart<T>,
        yaw: Float,
        pPartialTicks: Float,
        pose: PoseStack,
        buffer: MultiBufferSource,
        pPackedLight: Int
    ) {
        if (car.getLeader().isPresent) return

        pose.pushPose()

        // render
        var t: LinkableCart<T> = car
        var attachmentPoints = renderCarAndGetAttachmentPoints(car, yaw, pPartialTicks, pose, buffer, pPackedLight)

        while (t.getFollower().isPresent) {
            val nextT = t.getFollower().get()
            val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(nextT as AbstractMinecart)
            if (renderer is RenderWithAttachmentPoints<*>) {

                renderer as RenderWithAttachmentPoints<T>
                // translate to next train location
                val nextTPos: Vec3 = nextT.getPosition(pPartialTicks)
                val tPos: Vec3 = (t as AbstractMinecart).getPosition(pPartialTicks)
                var offset: Vec3 = nextTPos.subtract(tPos)
                pose.translate(offset.x, offset.y, offset.z)
                val newAttachmentPoints: Pair<Vec3, Vec3> = renderer.renderCarAndGetAttachmentPoints(
                    nextT,
                    nextT.yRot,
                    pPartialTicks,
                    pose,
                    buffer,
                    pPackedLight
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
            }

            t = nextT
        }

        pose.popPose()
    }

    private fun getAndRenderChain(
        from: Vec3,
        to: Vec3,
        matrixStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        matrixStack.pushPose()
        val vec: Vec3 = from.vectorTo(to)
        val dist: Double = vec.length()
        val segments = kotlin.math.ceil(dist * 4).toInt()

        // TODO: fix pitch
        matrixStack.mulPose(com.mojang.math.Axis.YP.rotation(-kotlin.math.atan2(vec.z, vec.x).toFloat()))
        matrixStack.mulPose(com.mojang.math.Axis.ZP.rotation((kotlin.math.asin(vec.y / dist)).toFloat()))
        matrixStack.pushPose()
        val ivertexbuilderChain: VertexConsumer =
            buffer.getBuffer(chainModel.renderType(CHAIN_TEXTURE))
        for (i in 1..<segments) {
            matrixStack.pushPose()
            matrixStack.translate(i / 4.0, 0.0, 0.0)
            chainModel.renderToBuffer(
                matrixStack,
                ivertexbuilderChain,
                packedLight,
                OverlayTexture.NO_OVERLAY
            )
            matrixStack.popPose()
        }

        matrixStack.popPose()
        matrixStack.popPose()
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

    override fun renderCarAndGetAttachmentPoints(
        entity: LinkableCart<T>,
        yaw: Float,
        partialTicks: Float,
        pose: PoseStack?,
        buffer: MultiBufferSource?,
        packedLight: Int
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
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - yaw))
        pose.mulPose(com.mojang.math.Axis.XN.rotationDegrees(pitch))
        val f5 = car.hurtTime.toFloat() - partialTicks
        var f6: Float = car.damage - partialTicks
        if (f6 < 0.0f) {
            f6 = 0.0f
        }

        if (f5 > 0.0f) {
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Mth.sin(f5) * f5 * f6 / 10.0f * car.hurtDir.toFloat()))
        }

        pose.translate(0.0, 1.1, 0.0)

        pose.scale(-1.0f, -1.0f, 1.0f)

        // Create a render state for the model
        val renderState = TrainCarRenderState<T>()
        renderState.updateFromEntity(entity, partialTicks)

        this.entityModel.setupAnim(renderState)
        val vertexconsumer: VertexConsumer = buffer.getBuffer(this.entityModel.renderType(texture))
        this.entityModel.renderToBuffer(
            pose,
            vertexconsumer,
            packedLight,
            OverlayTexture.NO_OVERLAY
        )
        renderAdditional(entity, yaw, partialTicks, pose, buffer, packedLight)
        pose.popPose()

        if (car.hasCustomName()) {
            car.customName?.let { name ->
                this.renderNameTag(renderState, name, pose, buffer, packedLight)
            }
        }

        return attach
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

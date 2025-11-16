package com.github.bonndan.steamy.wagons.entity

import com.github.bonndan.steamy.setup.ModItems
import com.github.bonndan.steamy.setup.ModItems.CONDUCTORS_WRENCH
import com.github.bonndan.steamy.setup.ModItems.SPRING
import com.github.bonndan.steamy.setup.SetThrottlePacket
import com.github.bonndan.steamy.setup.VehiclePacketHandler.sendToServer
import com.github.bonndan.steamy.train.LinkableCart
import com.github.bonndan.steamy.train.LinkingHandler
import com.github.bonndan.steamy.train.RailHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.EntityDataSerializers.INT
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.network.syncher.SynchedEntityData.defineId
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.vehicle.MinecartFurnace
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.sqrt

class LocomotiveEntity(entityType: EntityType<out MinecartFurnace>, level: Level) :
    MinecartFurnace(entityType, level), LinkableCart<LocomotiveEntity> {

    val FULL_AHEAD = 1.0f
    val ZERO_SPEED = 0f
    val BRAKES = -0.5f

    override val linkingHandler = LinkingHandler(this)


    init {
        linkingHandler.initWithEntityAndPosition(level, x, y, z)
    }

    override fun getDominatedIdAccessor(): EntityDataAccessor<Int> = DOMINATED_ID

    override fun getDominantIdAccessor(): EntityDataAccessor<Int> = DOMINANT_ID

    override fun getOnPos(): BlockPos {
        return linkingHandler.getOnPos(this as AbstractMinecart)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_ID_THROTTLE, 0f)
        builder.define(DOMINANT_ID, -1)
        builder.define(DOMINATED_ID, -1)
    }

    override fun getMaxSpeed(serverLevel: ServerLevel): Double {
        return if (this.isInWater) super.getMaxSpeed(serverLevel) * 0.75 else super.getMaxSpeed(serverLevel) * 0.5
    }

    override fun getDropItem(): Item {
        return ModItems.LOCOMOTIVE.get()
    }

    override fun getPickResult(): ItemStack {
        return ItemStack(ModItems.LOCOMOTIVE.get())
    }

    private fun calculateNewPushAlong(p_374438_: Vec3): Vec3 {
        return if (this.push.horizontalDistanceSqr() > 1.0E-4 && p_374438_.horizontalDistanceSqr() > 0.001)
            this.push.projectedOn(p_374438_).normalize().scale(this.push.length())
        else
            this.push
    }

    override fun tick() {

        linkingHandler.tickLoad()
        this.yRot = linkingHandler.computeYaw()
        val yrot = this.yRot
        super.tick()
        this.yRot = yrot
        if (!level().isClientSide) {
            linkingHandler.doChainMath()
        }

    }

    protected override fun readAdditionalSaveData(valueInput: ValueInput) {
        super.readAdditionalSaveData(valueInput)
        linkingHandler.readAdditionalSaveData(valueInput)
    }

    protected override fun addAdditionalSaveData(valueOutput: ValueOutput) {
        super.addAdditionalSaveData(valueOutput)
        linkingHandler.addAdditionalSaveData(valueOutput)
    }

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        linkingHandler.onSyncedDataUpdated(key)
    }


    override fun interact(player: Player, hand: InteractionHand): InteractionResult {

        val ret = furnaceInteract(player, hand)
        if (ret.consumesAction()) return ret

        val isNotSecondary = !player.isSecondaryUseActive
        val isNotVehicle = !this.isVehicle

        //TODO overcomplicated here, simplify
        val usesTrainTool = getUsesTrainTool(player.getItemInHand(hand))
        val isClientSideOrStartRiding = this.level().isClientSide || (!usesTrainTool && player.startRiding(this))

        if (isNotSecondary && isNotVehicle && !usesTrainTool && isClientSideOrStartRiding) {
            // TODO this.playerRotationOffset = this.rotationOffset
            if (!this.level().isClientSide) {
                return (if (player.startRiding(this)) InteractionResult.CONSUME else InteractionResult.PASS) as InteractionResult
            } else {
                return InteractionResult.SUCCESS
            }
        } else {
            return InteractionResult.PASS
        }
    }

    private fun getUsesTrainTool(itemStack: ItemStack): Boolean =
        itemStack.`is`(CONDUCTORS_WRENCH) || itemStack.`is`(SPRING)


    /**
     * Interaction with the furnace minecart but without the push change.
     */
    private fun furnaceInteract(
        player: Player,
        hand: InteractionHand
    ): InteractionResult {

        val push = Vec3(this.push.x, this.push.y, this.push.z)
        val ret = super.interact(player, hand)

        //undo the push change from super.interact
        this.push = push

        if (ret.consumesAction()) {
            val itemstack = player.getItemInHand(hand)
            // only return SUCCESS if the item is a valid fuel item, seems to be a bug in the original code
            return if (itemstack.`is`(ItemTags.FURNACE_MINECART_FUEL)) ret else InteractionResult.PASS
        }

        return ret
    }

    override fun isRideable(): Boolean {
        return true
    }

    /**
     * Speed is doubled if the player is giving movement input (WASD).
     */
    override fun makeStepAlongTrack(pos: BlockPos, railShape: RailShape, speed: Double): Double {

        var speedFactor = 1.0

        // for the new behavior, only apply speed boost if the player is giving movement input
        if (useExperimentalMovement(this.level())) {
            if (hasActiveThrottle()) {
                speedFactor = getThrottle().toDouble()
            }
        }
        return super.makeStepAlongTrack(pos, railShape, speed * speedFactor)
    }

    override fun remove(r: RemovalReason) {
        linkingHandler.handleLinkableKill()
        super.remove(r)
    }

    override fun destroy(serverLevel: ServerLevel, item: Item) {

        super.destroy(serverLevel, item)

        this.remove(RemovalReason.KILLED)
        if (serverLevel.gameRules.getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            val stack: ItemStack = this.pickResult
            if (this.hasCustomName()) {
                stack.set(DataComponents.ITEM_NAME, this.customName)
            }

            val chains = Stream.of(linkingHandler.leader, linkingHandler.follower)
                .filter { obj -> obj.isPresent }
                .count()
                .toInt()
            this.spawnAtLocation(serverLevel, stack)
            for (j in 0..<chains) {
                spawnChain()
            }
        }
    }

    fun superApplyNaturalSlowdown(p_363865_: Vec3): Vec3 {
        var vec3: Vec3?
        if (this.push.lengthSqr() > 1.0E-7) {
            this.push = this.calculateNewPushAlong(p_363865_)
            vec3 = p_363865_.multiply(0.8, 0.0, 0.8).add(this.push)
            if (this.isInWater) {
                vec3 = vec3.scale(0.1)
            }
        } else {
            vec3 = p_363865_.multiply(0.98, 0.0, 0.98)
        }

        return super.applyNaturalSlowdown(vec3)
    }

    override fun applyNaturalSlowdown(speed: Vec3): Vec3 {

        // for the old behavior, always apply speed boost when a player is movement input
        if (!useExperimentalMovement(this.level())) {
            if (hasActiveThrottle()) {

                //this is the minecart default behavior without the push
                val slowdownFactor = this.behavior.slowdownFactor
                val speedEffect = getThrottle().toDouble()
                var newSpeed: Vec3 = speed.multiply(slowdownFactor + speedEffect, 0.0, slowdownFactor + speedEffect)
                if (this.isInWater) {
                    newSpeed = newSpeed.scale(0.95)
                }
                println("new speed [${newSpeed}]")
                return newSpeed
            }
        }

        return superApplyNaturalSlowdown(speed)
    }

    private fun hasActiveThrottle() =
        hasFuel() && this.firstPassenger is ServerPlayer && getThrottle() != ZERO_SPEED

    override fun dismountTo(x: Double, y: Double, z: Double) {
        setThrottle(0f)
        super.dismountTo(x, y, z)
    }

    fun throttleUp() {
        val throttle = getThrottle()
        when {
            throttle >= FULL_AHEAD -> return

            throttle == ZERO_SPEED && hasFuel() -> {
                sendFeedback("Thrust on")
                return sendToServer(SetThrottlePacket(id, FULL_AHEAD))
            }

            else -> {
                sendFeedback("Loosing brakes")
                return sendToServer(SetThrottlePacket(id, ZERO_SPEED))
            }
        }
    }

    fun throttleDown() {
        val throttle = getThrottle()
        when {
            throttle <= BRAKES -> return
            throttle <= ZERO_SPEED -> {
                sendFeedback("Brakes on")
                return sendToServer(SetThrottlePacket(id, BRAKES))
            }

            else -> {
                sendFeedback("Thrust off")
                return sendToServer(SetThrottlePacket(id, ZERO_SPEED))
            }
        }
    }

    private fun sendFeedback(string: String) {
        Minecraft.getInstance().player?.displayClientMessage(Component.literal(string), false)
    }

    fun getThrottle(): Float {
        return this.entityData.get(DATA_ID_THROTTLE)
    }

    fun setThrottle(throttle: Float) {
        this.entityData.set(DATA_ID_THROTTLE, throttle)
    }

    override fun push(pEntity: Entity) {
        if (!this.level().isClientSide && !pEntity.noPhysics && !this.noPhysics) {
            // fix carts with passengers falling behind
            if (!this.hasPassenger(pEntity) || this.getLeader().isPresent) {
                var d0 = pEntity.x - this.x
                var d1 = pEntity.z - this.z
                var d2 = d0 * d0 + d1 * d1
                if (d2 >= 1.0E-4) {
                    d2 = sqrt(d2)
                    d0 /= d2
                    d1 /= d2
                    var d3 = 1.0 / d2
                    if (d3 > 1.0) {
                        d3 = 1.0
                    }

                    d0 *= d3
                    d1 *= d3
                    d0 *= 0.1
                    d1 *= 0.1
                    d0 *= 0.5
                    d1 *= 0.5
                    if (pEntity is AbstractMinecart) {
                        val d4 = pEntity.x - this.x
                        val d5 = pEntity.z - this.z
                        val vec3: Vec3 = (Vec3(d4, 0.0, d5)).normalize()
                        val vec31: Vec3 = (Vec3(
                            Mth.cos(this.yRot * (Math.PI.toFloat() / 180f)).toDouble(),
                            0.0,
                            Mth.sin(this.yRot * (Math.PI.toFloat() / 180f)).toDouble()
                        )).normalize()
                        val d6: Double = abs(vec3.dot(vec31))
                        if (d6 < 0.8) {
                            return
                        }

                        val vec32: Vec3 = this.deltaMovement
                        val vec33: Vec3 = pEntity.deltaMovement

                        if (isPoweredCart(pEntity) && !isPoweredCart(this)) {
                            this.deltaMovement = vec32.multiply(0.2, 1.0, 0.2)
                            this.push(vec33.x - d0, 0.0, vec33.z - d1)
                            pEntity.deltaMovement = vec33.multiply(0.95, 1.0, 0.95)
                        } else if (!isPoweredCart(pEntity) && isPoweredCart(this)) {
                            pEntity.deltaMovement = vec33.multiply(0.2, 1.0, 0.2)
                            pEntity.push(vec32.x + d0, 0.0, vec32.z + d1)
                            this.deltaMovement = vec32.multiply(0.95, 1.0, 0.95)
                        } else {
                            val d7: Double = (vec33.x + vec32.x) / 2.0
                            val d8: Double = (vec33.z + vec32.z) / 2.0
                            this.deltaMovement = vec32.multiply(0.2, 1.0, 0.2)
                            this.push(d7 - d0, 0.0, d8 - d1)
                            pEntity.deltaMovement = vec33.multiply(0.2, 1.0, 0.2)
                            pEntity.push(d7 + d0, 0.0, d8 + d1)
                        }
                    } else {
                        this.push(-d0, 0.0, -d1)
                        pEntity.push(d0 / 4.0, 0.0, d1 / 4.0)
                    }
                }
            }
        }
    }

    private fun isPoweredCart(entity: AbstractMinecart): Boolean {
        return entity is LocomotiveEntity || entity is MinecartFurnace
    }

    companion object {
        val DATA_ID_THROTTLE: EntityDataAccessor<Float> =
            defineId(LocomotiveEntity::class.java, EntityDataSerializers.FLOAT)

        val DOMINANT_ID: EntityDataAccessor<Int> = defineId<Int>(LocomotiveEntity::class.java, INT)
        val DOMINATED_ID: EntityDataAccessor<Int> = defineId<Int>(LocomotiveEntity::class.java, INT)
    }

}
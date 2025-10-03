package com.github.bonndan.steamy.locomotive.entity

import com.github.bonndan.steamy.setup.ModItems
import com.github.bonndan.steamy.setup.ModItems.CONDUCTORS_WRENCH
import com.github.bonndan.steamy.setup.ModItems.SPRING
import com.github.bonndan.steamy.setup.SetThrottlePacket
import com.github.bonndan.steamy.setup.VehiclePacketHandler.sendToServer
import com.github.bonndan.steamy.train.AbstractTrainCarEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FurnaceBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3

class LocomotiveEntity(entityType: EntityType<out AbstractTrainCarEntity>, level: Level) :
    AbstractTrainCarEntity(entityType, level) {

    val FULL_AHEAD = 1.0f
    val ZERO_SPEED = 0f
    val BRAKES = -0.5f

    private var rotationOffset = 0f
    private var playerRotationOffset = 0f
    private var fuel = 0
    var push: Vec3 = DEFAULT_PUSH

    override fun isFurnace(): Boolean {
        return true
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_ID_FUEL, false)
        builder.define(DATA_ID_THROTTLE, 0f)
    }

    override fun tick() {
        val d0 = this.yRot.toDouble()
        val vec3 = this.position()
        super.tick()
        if (!this.level().isClientSide()) {
            if (this.fuel > 0) {
                this.fuel--
            }

            if (this.fuel <= 0) {
                this.push = Vec3.ZERO
            }

            this.setHasFuel(this.fuel > 0)
        }

        if (this.hasFuel() && this.random.nextInt(4) == 0) {
            this.level()
                .addParticle(ParticleTypes.LARGE_SMOKE, this.x, this.y + 0.8, this.z, 0.0, 0.0, 0.0)
        }
        val d1 = (this.yRot.toDouble() - d0) % 360.0
        if (this.level().isClientSide && vec3.distanceTo(this.position()) > 0.01) {
            this.rotationOffset += d1.toFloat()
            this.rotationOffset %= 360.0f
        }
        this.behavior
    }

    override fun getMaxSpeed(serverLevel: ServerLevel): Double {
        return if (this.isInWater) super.getMaxSpeed(serverLevel) * 0.75 else super.getMaxSpeed(serverLevel) * 0.5
    }

    override fun getDropItem(): Item {
        return ModItems.LOCOMOTIVE.get()
    }

    override fun getPickResult(): ItemStack {
        return ItemStack(Items.FURNACE_MINECART)
    }


    private fun calculateNewPushAlong(p_374438_: Vec3): Vec3 {
        return if (this.push.horizontalDistanceSqr() > 1.0E-4 && p_374438_.horizontalDistanceSqr() > 0.001)
            this.push.projectedOn(p_374438_).normalize().scale(this.push.length())
        else
            this.push
    }


    override fun addAdditionalSaveData(valueOutput: ValueOutput) {
        super.addAdditionalSaveData(valueOutput)
        valueOutput.putDouble("PushX", this.push.x)
        valueOutput.putDouble("PushZ", this.push.z)
        valueOutput.putShort("Fuel", this.fuel.toShort())
    }

    override fun readAdditionalSaveData(valueInput: ValueInput) {
        super.readAdditionalSaveData(valueInput)
        val d0 = valueInput.getDoubleOr("PushX", DEFAULT_PUSH.x)
        val d1 = valueInput.getDoubleOr("PushZ", DEFAULT_PUSH.z)
        this.push = Vec3(d0, 0.0, d1)
        this.fuel = valueInput.getShortOr("Fuel", 0.toShort())
    }

    private fun hasFuel(): Boolean {
        return this.entityData.get(DATA_ID_FUEL) ?: false
    }

    private fun setHasFuel(fuel: Boolean) {
        this.entityData.set(DATA_ID_FUEL, fuel)
    }

    override fun getDefaultDisplayBlockState(): BlockState =
        Blocks.FURNACE.defaultBlockState()
            .setValue(FurnaceBlock.FACING, Direction.NORTH)
            .setValue(FurnaceBlock.LIT, this.hasFuel())


    override fun interact(player: Player, hand: InteractionHand): InteractionResult {

        val ret = furnaceInteract(player, hand)
        if (ret.consumesAction()) return ret

        val isNotSecondary = !player.isSecondaryUseActive
        val isNotVehicle = !this.isVehicle

        //TODO overcomplicated here, simplify
        val usesTrainTool = getUsesTrainTool(player.getItemInHand(hand))
        val isClientSideOrStartRiding = this.level().isClientSide || (!usesTrainTool && player.startRiding(this))

        if (isNotSecondary && isNotVehicle && !usesTrainTool && isClientSideOrStartRiding) {
            this.playerRotationOffset = this.rotationOffset
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

    private fun superInteract(p_38562_: Player, p_38563_: InteractionHand): InteractionResult {
        val ret = super.interact(p_38562_, p_38563_)
        if (ret.consumesAction()) return ret
        val itemstack = p_38562_.getItemInHand(p_38563_)
        if (itemstack.`is`(ItemTags.FURNACE_MINECART_FUEL) && this.fuel + FUEL_TICKS_PER_ITEM <= MAX_FUEL_TICKS) {
            itemstack.consume(1, p_38562_)
            this.fuel += 3600
        }

        if (this.fuel > 0) {
            this.push = this.position().subtract(p_38562_.position()).horizontal()
        }

        return InteractionResult.SUCCESS
    }


    /**
     * Interaction with the furnace minecart but without the push change.
     */
    private fun furnaceInteract(
        player: Player,
        hand: InteractionHand
    ): InteractionResult {

        val push = Vec3(this.push.x, this.push.y, this.push.z)
        val ret = superInteract(player, hand)

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


    override fun positionRider(passenger: Entity, callback: MoveFunction) {
        super.positionRider(passenger, callback)
        if (this.level().isClientSide && passenger is Player && passenger.shouldRotateWithMinecart() && useExperimentalMovement(
                this.level()
            )
        ) {
            val f = Mth.rotLerp(0.5, this.playerRotationOffset.toDouble(), this.rotationOffset.toDouble()).toFloat()
            passenger.yRot = passenger.yRot - (f - this.playerRotationOffset)
            this.playerRotationOffset = f
        }
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

    companion object {
        val DATA_ID_THROTTLE: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(LocomotiveEntity::class.java, EntityDataSerializers.FLOAT)

        val DATA_ID_FUEL: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(LocomotiveEntity::class.java, EntityDataSerializers.BOOLEAN)
        const val FUEL_TICKS_PER_ITEM: Int = 3600
        const val MAX_FUEL_TICKS: Int = 32000
        const val DEFAULT_FUEL: Short = 0
        val DEFAULT_PUSH: Vec3 = Vec3.ZERO
    }

}
package com.github.bonndan.steamy.wagons.entity

import com.github.bonndan.steamy.setup.ModItems
import com.github.bonndan.steamy.setup.ModItems.CONDUCTORS_WRENCH
import com.github.bonndan.steamy.setup.ModItems.SPRING
import com.github.bonndan.steamy.train.LinkableCart
import com.github.bonndan.steamy.train.LinkingHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers.INT
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.network.syncher.SynchedEntityData.defineId
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.vehicle.Minecart
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.stream.Stream

class WagonEntity(entityType: EntityType<out Minecart>, level: Level) : Minecart(entityType, level),
    LinkableCart<WagonEntity> {

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
        builder.define(DOMINANT_ID, -1)
        builder.define(DOMINATED_ID, -1)
    }

    override fun getDropItem(): Item {
        return ModItems.WAGON.get()
    }

    override fun getPickResult(): ItemStack {
        return ItemStack(ModItems.WAGON.get())
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

    // force render since we delegate rendering to the head of the train
    override fun shouldRender(pX: Double, pY: Double, pZ: Double): Boolean {
        return true
    }

    override fun getMotionDirection(): Direction {
        return Direction.fromYRot((this.yRot).toDouble())
    }


    override fun interact(player: Player, hand: InteractionHand): InteractionResult {

        val isNotSecondary = !player.isSecondaryUseActive
        val isNotVehicle = !this.isVehicle

        //TODO overcomplicated here, simplify
        val usesTrainTool = getUsesTrainTool(player.getItemInHand(hand))
        val isClientSideOrStartRiding = this.level().isClientSide || (!usesTrainTool)

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

    companion object {

        val DOMINANT_ID: EntityDataAccessor<Int> = defineId<Int>(WagonEntity::class.java, INT)
        val DOMINATED_ID: EntityDataAccessor<Int> = defineId<Int>(WagonEntity::class.java, INT)
    }

}
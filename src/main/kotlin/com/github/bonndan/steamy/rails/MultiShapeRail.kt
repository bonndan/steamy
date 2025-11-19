package com.github.bonndan.steamy.rails

import com.github.bonndan.steamy.rails.SwitchRail.OutDirection
import com.github.bonndan.steamy.setup.ModTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.block.state.properties.RailShape

abstract class MultiShapeRail(pProperties: Properties) : BaseRailBlock(false, pProperties) {

    @Deprecated("")
    override fun getShapeProperty(): Property<RailShape> {
        return RAIL_SHAPE
    }

    override fun updateState(pState: BlockState, pLevel: Level, pPos: BlockPos, pIsMoving: Boolean): BlockState {
        // Multi-shape rails don't auto-update their own shape
        // They maintain their configured shape and don't respond to neighbor changes
        return pState
    }

    override fun canMakeSlopes(state: BlockState, world: BlockGetter, pos: BlockPos): Boolean {
        return false
    }

    protected fun isCrouchingOrHasWrench(
        pPlayer: Player,
        pHand: InteractionHand
    ): Boolean = pPlayer.pose == Pose.CROUCHING && pPlayer.getItemInHand(pHand).isEmpty ||
            (pPlayer.pose != Pose.CROUCHING && pPlayer.getItemInHand(pHand).tags
                .anyMatch(ModTags.Items.WRENCHES::equals))

    abstract fun getVanillaRailShapeFromDirection(
        state: BlockState,
        pos: BlockPos,
        level: Level,
        direction: Direction
    ): RailShape

    companion object {

        // facing denotes direction of straight out
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val OUT_DIRECTION = EnumProperty.create("out_direction", OutDirection::class.java)
    }
}
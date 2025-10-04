package com.github.bonndan.steamy.rails

import com.github.bonndan.steamy.rails.SwitchRail.OutDirection
import com.github.bonndan.steamy.setup.ModTags
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

abstract class AbstractMultiShapeRail(pProperties: Properties) : BaseRailBlock(false, pProperties), MultiShapeRail {

    val SHAPE_FLAT: VoxelShape = column(16.0, 0.0, 2.0)

    val CODEC: MapCodec<RailBlock> = simpleCodec { RailBlock(it) }

    @Deprecated("")
    override fun getShapeProperty(): Property<RailShape> {
        return RAIL_SHAPE
    }

    override fun codec(): MapCodec<out BaseRailBlock> {
        return CODEC
    }

    public override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext
    ): VoxelShape {
        return SHAPE_FLAT
    }

    override fun updateState(pState: BlockState, pLevel: Level, pPos: BlockPos, pIsMoving: Boolean): BlockState {
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

    @Deprecated("")
    override fun isValidRailShape(shape: RailShape): Boolean {
        return RAIL_SHAPE.getPossibleValues().contains(shape)
    }

    companion object {
        // for compatibilty issues
        val RAIL_SHAPE = RailShapeUtil.RAIL_SHAPE_STRAIGHT_FLAT

        // facing denotes direction of straight out
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val OUT_DIRECTION = EnumProperty.create("out_direction", OutDirection::class.java)

        // is this rail track engaged?
        val POWERED = BlockStateProperties.POWERED
    }
}
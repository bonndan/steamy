package com.github.bonndan.steamy.rails

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE
import net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE_STRAIGHT
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

class JunctionRail(pProperties: Properties) : MultiShapeRail(pProperties) {

    override fun codec(): MapCodec<out BaseRailBlock> {
        return CODEC
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState {
        val fluidstate: FluidState = pContext.level.getFluidState(pContext.clickedPos)
        val flag = fluidstate.type === Fluids.WATER
        val blockstate: BlockState = super.defaultBlockState()
        return setFacing(blockstate, pContext.horizontalDirection)
            .setValue(WATERLOGGED, flag)
    }

    fun setFacing(state: BlockState, facing: Direction): BlockState =
        state
            .setValue(
                RAIL_SHAPE,
                if (facing.axis === Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
            )
            .setValue(FACING, facing)

    override fun getRailDirection(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        cart: AbstractMinecart?
    ): RailShape {
        if (cart == null) {
            return state.getValue(RAIL_SHAPE)
        }

        // For carts, return shape based on their movement direction
        // Use getMotionDirection() which is more reliable than velocity
        val cartDirection = cart.motionDirection
        return if (cartDirection.axis === Axis.X)
            RailShape.EAST_WEST
        else
            RailShape.NORTH_SOUTH
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WATERLOGGED, RAIL_SHAPE, FACING)
    }

    override fun getVanillaRailShapeFromDirection(
        state: BlockState,
        pos: BlockPos,
        level: Level,
        direction: Direction
    ): RailShape {
        return if (direction == Direction.EAST || direction == Direction.WEST)
            RailShape.EAST_WEST
        else
            RailShape.NORTH_SOUTH
    }

    @Deprecated("")
    override fun isValidRailShape(shape: RailShape): Boolean {
        return RAIL_SHAPE_STRAIGHT.getPossibleValues().contains(shape)
    }

    companion object {
        val CODEC: MapCodec<JunctionRail> = simpleCodec(::JunctionRail)
    }
}

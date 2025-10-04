package com.github.bonndan.steamy.rails

import com.github.bonndan.steamy.train.RailHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

class JunctionRail(pProperties: Properties) : AbstractMultiShapeRail(pProperties) {

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState {
        val fluidstate: FluidState = pContext.level.getFluidState(pContext.clickedPos)
        val flag = fluidstate.type === Fluids.WATER
        val blockstate: BlockState = super.defaultBlockState()
        return blockstate
            .setValue(WATERLOGGED, flag)
            .setValue(BlockStateProperties.RAIL_SHAPE, RailShapeUtil.DEFAULT)
    }

    override fun getRailDirection(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        cart: AbstractMinecart?
    ): RailShape {
        if (cart == null) {
            return state.getValue(this.shapeProperty)
        }

        return if (RailHelper.directionFromVelocity(cart.deltaMovement)
                .axis === Direction.Axis.X
        ) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
    }

    public override fun rotate(pState: BlockState, pRot: Rotation): BlockState {
        return pState
    }

    public override fun mirror(pState: BlockState, pMirror: Mirror): BlockState {
        return pState
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WATERLOGGED, BlockStateProperties.RAIL_SHAPE)
    }

    override fun setRailState(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        `in`: Direction,
        out: Direction
    ): Boolean {
        return `in`.axis.isHorizontal && `in`.opposite == out
    }

    override fun getPossibleOutputDirections(state: BlockState, inputSide: Direction): Set<Direction> {
        if (inputSide.axis.isHorizontal) {
            return setOf<Direction>(inputSide.opposite)
        }
        return NO_POSSIBILITIES
    }

    override fun getPriorityDirectionsToCheck(state: BlockState, entrance: Direction): Set<Direction> {
        return if (entrance == Direction.EAST || entrance == Direction.WEST) {
            mutableSetOf(Direction.NORTH, Direction.SOUTH)
        } else mutableSetOf()
    }

    override fun getVanillaRailShapeFromDirection(
        state: BlockState,
        pos: BlockPos,
        level: Level,
        direction: Direction
    ): RailShape {
        return if (direction == Direction.EAST || direction == Direction.WEST) {
            RailShape.EAST_WEST
        } else RailShape.NORTH_SOUTH
    }

    companion object {

        val NO_POSSIBILITIES: Set<Direction> = setOf<Direction>()
    }
}

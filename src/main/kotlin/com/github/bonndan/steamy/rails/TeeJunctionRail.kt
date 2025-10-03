package com.github.bonndan.steamy.rails

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.*
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.Orientation

class TeeJunctionRail(pProperties: Properties, private val automaticSwitching: Boolean) :
    AbstractMultiShapeRail(pProperties) {

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState {
        val fluidstate: FluidState = pContext.level.getFluidState(pContext.clickedPos)
        val flag = fluidstate.type === Fluids.WATER
        val blockstate: BlockState = super.defaultBlockState()
        return setFacing(blockstate, pContext.horizontalDirection)
            .setValue(WATERLOGGED, flag)
            .setValue(POWERED,
                !automaticSwitching && pContext.level.hasNeighborSignal(pContext.clickedPos)
            )
    }

    private fun getRailShapeFromFacing(facing: Direction): RailShape {
        return if (facing.axis === Direction.Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
    }

    fun setFacing(state: BlockState, facing: Direction): BlockState {
        return state
            .setValue(RAIL_SHAPE, getRailShapeFromFacing(facing))
            .setValue(FACING, facing)
    }

    override fun getPriorityDirectionsToCheck(state: BlockState, entrance: Direction): Set<Direction> {
        val c = getRailConfiguration(state)
        return if (entrance == c.poweredDirection) setOf(c.unpoweredDirection) else setOf()
    }

    private fun getRailConfiguration(state: BlockState): BranchingRailConfiguration {
        val facing: Direction = state.getValue<Direction>(FACING)
        val unpoweredDirection = facing.clockWise
        val poweredDirection = facing.counterClockWise
        val rootDirection = facing.opposite

        return BranchingRailConfiguration(rootDirection, unpoweredDirection, poweredDirection)
    }

    override fun getRailDirection(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        cart: AbstractMinecart?
    ): RailShape {
        val c = getRailConfiguration(state)
        val outDirection = if (state.getValue(POWERED)) c.poweredDirection else c.unpoweredDirection
        return RailShapeUtil.getRailShape(c.rootDirection, outDirection)
    }

    override fun setRailState(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        `in`: Direction,
        out: Direction
    ): Boolean {
        val c = getRailConfiguration(state)
        val possibilities= getPossibleOutputDirections(state, `in`)

        if (!automaticSwitching) {
            return possibilities.contains(out)
        }

        if (!possibilities.contains(out)) return false

        if (`in` == c.rootDirection) {
            if (out == c.poweredDirection) {
                world.setBlock(pos, state.setValue(POWERED, true), 2)
                return true
            } else if (out == c.unpoweredDirection) {
                world.setBlock(pos, state.setValue<Boolean?, Boolean?>(POWERED, false), 2)
                return true
            }
            return false
        }

        if (`in` == c.unpoweredDirection && out == c.rootDirection) {
            world.setBlock(pos, state.setValue<Boolean?, Boolean?>(POWERED, false), 2)
            return true
        }

        if (`in` == c.poweredDirection && out == c.rootDirection) {
            world.setBlock(pos, state.setValue<Boolean?, Boolean?>(POWERED, true), 2)
            return true
        }

        return false
    }

    override fun getPossibleOutputDirections(state: BlockState, inputSide: Direction): Set<Direction> {
        val powered: Boolean = state.getValue(POWERED)
        val poss = getRailConfiguration(state).getPossibleDirections(inputSide, automaticSwitching, powered)
        return poss
    }

    override fun getVanillaRailShapeFromDirection(
        state: BlockState,
        pos: BlockPos,
        level: Level,
        direction: Direction
    ): RailShape {
        return getRailDirection(state, level, pos, null)
    }

    public override fun rotate(pState: BlockState, pRot: Rotation): BlockState {
        return setFacing(pState, pRot.rotate(pState.getValue(FACING)))
    }

    public override fun mirror(pState: BlockState, pMirror: Mirror): BlockState {
        return pState
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WATERLOGGED, FACING, RAIL_SHAPE, POWERED)
    }

    override fun neighborChanged(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        p_49380_: Block,
        p_361387_: Orientation?,
        p_49382_: Boolean
    ) {
        super.neighborChanged(state, world, pos, p_49380_, p_361387_, p_49382_)
        if (automaticSwitching) return

        if (!world.isClientSide) {
            val flag: Boolean = state.getValue(POWERED)
            if (flag != world.hasNeighborSignal(pos)) {
                world.setBlock(pos, state.cycle(POWERED), 2)
            }
        }
    }

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: Direction?): Boolean {
        return true
    }

    override fun isAutomaticSwitching(): Boolean
         = automaticSwitching
}
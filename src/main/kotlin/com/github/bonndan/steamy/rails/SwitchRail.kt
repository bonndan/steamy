package com.github.bonndan.steamy.rails

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

class SwitchRail(pProperties: Properties, private val automaticSwitching: Boolean) :
    AbstractMultiShapeRail(pProperties) {

    enum class OutDirection(private val serializedName: String) : StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        fun getOutDirection(inDirection: Direction): Direction {
            return if (this == RIGHT) inDirection.counterClockWise else inDirection.clockWise
        }

        fun opposite(): OutDirection {
            return if (this == LEFT) RIGHT else LEFT
        }

        override fun getSerializedName(): String {
            return serializedName
        }
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState {
        val fluidstate: FluidState = pContext.level.getFluidState(pContext.clickedPos)
        val flag = fluidstate.type === Fluids.WATER
        val blockstate: BlockState = super.defaultBlockState()
        return setFacing(blockstate, pContext.horizontalDirection)
            .setValue(WATERLOGGED, flag)
            .setValue(
                POWERED,
                !automaticSwitching && pContext.level.hasNeighborSignal(pContext.clickedPos)
            )
            .setValue(OUT_DIRECTION, OutDirection.RIGHT)
    }

    private fun getRailShapeFromFacing(facing: Direction): RailShape {
        return if (facing.axis === Direction.Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
    }

    fun setFacing(state: BlockState, facing: Direction): BlockState {
        return state
            .setValue(RAIL_SHAPE, getRailShapeFromFacing(facing))
            .setValue<Direction?, Direction?>(FACING, facing)
    }

    private fun getRailConfiguration(state: BlockState): BranchingRailConfiguration {
        val out: OutDirection = state.getValue(OUT_DIRECTION)

        val unpoweredDirection: Direction = state.getValue<Direction>(FACING)
        val rootDirection = unpoweredDirection.opposite
        val poweredDirection = out.getOutDirection(rootDirection)

        return BranchingRailConfiguration(rootDirection, unpoweredDirection, poweredDirection)
    }

    override fun getRailDirection(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        cart: AbstractMinecart?
    ): RailShape {
        val c: BranchingRailConfiguration = getRailConfiguration(state)
        return RailShapeUtil.getRailShape(
            c.rootDirection,
            if (state.getValue(POWERED)) c.poweredDirection else c.unpoweredDirection
        )
    }

    override fun setRailState(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        `in`: Direction,
        out: Direction
    ): Boolean {

        val configuration = getRailConfiguration(state)
        val possibilities = getPossibleOutputDirections(state, `in`)

        if (!automaticSwitching) {
            return possibilities.contains(out)
        }

        if (!possibilities.contains(out)) return false

        if (`in` == configuration.rootDirection) {
            if (out == configuration.poweredDirection) {
                world.setBlock(pos, state.setValue<Boolean, Boolean>(POWERED, true), 2)
                return true
            } else if (out == configuration.unpoweredDirection) {
                world.setBlock(pos, state.setValue<Boolean, Boolean>(POWERED, false), 2)
                return true
            }
            return false
        }

        if (`in` == configuration.unpoweredDirection && out == configuration.rootDirection) {
            world.setBlock(pos, state.setValue<Boolean, Boolean>(POWERED, false), 2)
            return true
        }

        if (`in` == configuration.poweredDirection && out == configuration.rootDirection) {
            world.setBlock(pos, state.setValue<Boolean, Boolean>(POWERED, true), 2)
            return true
        }

        return false
    }


    override fun getPossibleOutputDirections(state: BlockState, inputSide: Direction): Set<Direction> {
        val c: BranchingRailConfiguration = getRailConfiguration(state)
        val powered: Boolean = state.getValue(POWERED)
        return c.getPossibleDirections(inputSide, automaticSwitching, powered)
    }

    override fun getPriorityDirectionsToCheck(state: BlockState, entrance: Direction): Set<Direction> {
        val c: BranchingRailConfiguration = getRailConfiguration(state)
        return if (entrance == c.poweredDirection) setOf(c.unpoweredDirection) else setOf()
    }

    override fun getVanillaRailShapeFromDirection(
        state: BlockState,
        pos: BlockPos,
        level: Level,
        direction: Direction
    ): RailShape {
        return getRailDirection(state, level, pos, null)
    }

    override fun isAutomaticSwitching(): Boolean
         = automaticSwitching

    public override fun rotate(pState: BlockState, pRot: Rotation): BlockState {
        return setFacing(pState, pRot.rotate(pState.getValue(FACING)))
    }

    public override fun mirror(pState: BlockState, pMirror: Mirror): BlockState {
        if (pMirror == Mirror.LEFT_RIGHT) {
            return pState.setValue(
                OUT_DIRECTION,
                pState.getValue(OUT_DIRECTION).opposite()
            )
        } else if (pMirror == Mirror.FRONT_BACK) return rotate(
            pState,
            pMirror.getRotation(pState.getValue<Direction>(FACING))
        )
        return pState
    }

    override fun useItemOn(
        p_316304_: ItemStack,
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        p_316140_: BlockHitResult
    ): InteractionResult {
        if (isCrouchingOrHasWrench(pPlayer, pHand)) {
            pLevel.setBlockAndUpdate(pPos, this.mirror(pState, Mirror.LEFT_RIGHT))
            return InteractionResult.SUCCESS
        }
        return InteractionResult.PASS
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WATERLOGGED, FACING, RAIL_SHAPE, OUT_DIRECTION, POWERED)
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
            val flag = state.getValue(POWERED)
            if (flag != world.hasNeighborSignal(pos)) {
                world.setBlock(pos, state.cycle<Boolean>(POWERED), 2)
            }
        }
    }

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: Direction?): Boolean {
        return true
    }
}

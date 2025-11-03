package com.github.bonndan.steamy.rails

import com.mojang.serialization.MapCodec
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
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

class SwitchRail(pProperties: Properties) : AbstractMultiShapeRail(pProperties) {

    override fun codec(): MapCodec<out BaseRailBlock> {
        return CODEC
    }

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
            .setValue(OUT_DIRECTION, OutDirection.RIGHT)
    }

    private fun getRailShapeFromFacing(facing: Direction): RailShape {
        return if (facing.axis === Direction.Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
    }

    fun setFacing(state: BlockState, facing: Direction): BlockState {
        return state
            .setValue(BlockStateProperties.RAIL_SHAPE, getRailShapeFromFacing(facing))
            .setValue(FACING, facing)
    }

    private fun getRailConfiguration(state: BlockState): BranchingRailConfiguration {
        val out: OutDirection = state.getValue(OUT_DIRECTION)

        val unpoweredDirection: Direction = state.getValue(FACING)
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
            if (state.getValue(BlockStateProperties.POWERED)) c.poweredDirection else c.unpoweredDirection
        )
    }

    override fun setRailState(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        `in`: Direction,
        out: Direction
    ): Boolean {

        return getPossibleOutputDirections(state, `in`).contains(out)
    }


    override fun getPossibleOutputDirections(state: BlockState, inputSide: Direction): Set<Direction> {
        val c: BranchingRailConfiguration = getRailConfiguration(state)
        val powered: Boolean = state.getValue(BlockStateProperties.POWERED)
        return c.getPossibleDirections(inputSide, false, powered)
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
        pBuilder.add(WATERLOGGED, FACING, BlockStateProperties.RAIL_SHAPE, OUT_DIRECTION, BlockStateProperties.POWERED)
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

        if (!world.isClientSide) {
            val flag = state.getValue(BlockStateProperties.POWERED)
            if (flag != world.hasNeighborSignal(pos)) {
                world.setBlock(pos, state.cycle(BlockStateProperties.POWERED), 2)
            }
        }
    }

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: Direction?): Boolean {
        return true
    }

    companion object {
        val CODEC: MapCodec<SwitchRail> = simpleCodec(::SwitchRail)
    }
}

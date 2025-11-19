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
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.Orientation

class TeeJunctionRail(pProperties: Properties) : MultiShapeRail(pProperties) {

    override fun codec(): MapCodec<out BaseRailBlock> {
        return CODEC
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState {
        val fluidstate: FluidState = pContext.level.getFluidState(pContext.clickedPos)
        val flag = fluidstate.type === Fluids.WATER
        val blockstate: BlockState = super.defaultBlockState()

        return setFacing(blockstate, pContext.horizontalDirection)
            .setValue(WATERLOGGED, flag)
            .setValue(BlockStateProperties.POWERED, pContext.level.hasNeighborSignal(pContext.clickedPos))
    }

    fun setFacing(state: BlockState, facing: Direction): BlockState =
        state
            .setValue(
                BlockStateProperties.RAIL_SHAPE,
                if (facing.axis === Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
            )
            .setValue(FACING, facing)

    private fun getRailConfiguration(state: BlockState): BranchingRailConfiguration {
        val facing: Direction = state.getValue(FACING)
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
        val outDirection =
            if (state.getValue(BlockStateProperties.POWERED)) c.poweredDirection else c.unpoweredDirection
        return RailShapeUtil.getRailShape(c.rootDirection, outDirection)
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

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WATERLOGGED, FACING, BlockStateProperties.RAIL_SHAPE, BlockStateProperties.POWERED)
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
            val flag: Boolean = state.getValue(BlockStateProperties.POWERED)
            if (flag != world.hasNeighborSignal(pos)) {
                world.setBlock(pos, state.cycle(BlockStateProperties.POWERED), 2)
            }
        }
    }

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: Direction?): Boolean {
        return true
    }

    companion object {
        val CODEC: MapCodec<TeeJunctionRail> = simpleCodec(::TeeJunctionRail)
    }
}
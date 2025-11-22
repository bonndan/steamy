package com.github.bonndan.steamy.rails

import com.github.bonndan.steamy.rails.RailShapeUtil.createRailShape
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

class SwitchRail(pProperties: Properties) : MultiShapeRail(pProperties) {

    override fun codec(): MapCodec<out BaseRailBlock> {
        return CODEC
    }

    enum class SwitchType(private val serializedName: String) : StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        fun opposite(): SwitchType {
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

        return blockstate
            .setValue(
                RAIL_SHAPE,
                if (pContext.horizontalDirection.axis === Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
            )
            .setValue(FACING, pContext.horizontalDirection)
            .setValue(WATERLOGGED, flag)
            .setValue(SWITCH_TYPE, SwitchType.RIGHT)
    }

    override fun getRailDirection(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        cart: AbstractMinecart?
    ): RailShape {

        //straight forward in the direction of placement, never switches
        val facingDirection: Direction = state.getValue(FACING)
        val switchType = state.getValue(SWITCH_TYPE)
        val powered = state.getValue(BlockStateProperties.POWERED)
        val cartDirection = cart?.motionDirection

        return createRailShape(
            from = facingDirection,
            to = getCurrentOutDirection(facingDirection, switchType, powered, cartDirection)
        )
    }

    private fun getCurrentOutDirection(
        facingDirection: Direction,
        switchType: SwitchType,
        powered: Boolean,
        cartDirection: Direction?
    ): Direction {

        //straight forward through the switch
        val unpoweredDirection = facingDirection.opposite

        if (cartDirection == facingDirection) {
            //entering from the back, always go straight
            return unpoweredDirection
        }

        val poweredDirection =
            if (switchType == SwitchType.RIGHT) facingDirection.counterClockWise else facingDirection.clockWise

        return if (powered) poweredDirection else unpoweredDirection
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
        val facing = pRot.rotate(pState.getValue(FACING))
        return pState
            .setValue(
                RAIL_SHAPE,
                if (facing.axis === Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
            )
            .setValue(FACING, facing)
    }

    public override fun mirror(pState: BlockState, pMirror: Mirror): BlockState {
        if (pMirror == Mirror.LEFT_RIGHT) {
            return pState.setValue(
                SWITCH_TYPE,
                pState.getValue(SWITCH_TYPE).opposite()
            )
        } else if (pMirror == Mirror.FRONT_BACK) return rotate(
            pState,
            pMirror.getRotation(pState.getValue(FACING))
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
        pBuilder.add(WATERLOGGED, FACING, RAIL_SHAPE, SWITCH_TYPE, BlockStateProperties.POWERED)
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

        /**
         * The turn direction when the cart enters from the root direction
         */
        val SWITCH_TYPE: EnumProperty<SwitchType> = EnumProperty.create("out_direction", SwitchType::class.java)
    }
}

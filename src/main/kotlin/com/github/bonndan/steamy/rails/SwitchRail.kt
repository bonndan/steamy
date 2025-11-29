package com.github.bonndan.steamy.rails

import com.github.bonndan.steamy.rails.RailShapeUtil.createRailShape
import com.github.bonndan.steamy.setup.ModItems
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.Item
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED
import net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.phys.Vec3

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

        val isWaterlogged = pContext.level.getFluidState(pContext.clickedPos).type === Fluids.WATER

        return super.defaultBlockState()
            .setValue(
                RAIL_SHAPE,
                if (pContext.horizontalDirection.axis === Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH
            )
            .setValue(FACING, pContext.horizontalDirection)
            .setValue(WATERLOGGED, isWaterlogged)
            .setValue(SWITCH_TYPE, SwitchType.RIGHT)
            .setValue(POWERED, false)
    }

    override fun getRailDirection(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        cart: AbstractMinecart?
    ): RailShape {

        val facingDirection: Direction = state.getValue(FACING)
        val switchType = state.getValue(SWITCH_TYPE)
        val powered = state.getValue(POWERED)
        val straight = createRailShape(from = facingDirection, to = facingDirection.opposite)

        if (cart == null) {
            return straight
        }

        val cartDirection = getCartDirection(cart)
        val comingFromSide = (cartDirection.clockWise == facingDirection || cartDirection.counterClockWise == facingDirection)


        if (cartDirection == facingDirection) {
            return straight
        }

        if (powered) {
            val exitDirection =
                if (switchType == SwitchType.RIGHT) facingDirection.counterClockWise else facingDirection.clockWise
            return createRailShape(from = facingDirection, to = exitDirection)
        }

        if (comingFromSide) {
            return createRailShape(from = cartDirection.opposite, to = facingDirection)
        }

        return straight
    }

    private fun getCartDirection(cart: AbstractMinecart): Direction {

        val diff = Vec3(cart.x - cart.xo, 0.0, cart.z - cart.zo)
        val horizontalDirection = Direction.getNearest(diff.normalize().x.toInt(), 0, diff.normalize().z.toInt(), null)

        return horizontalDirection?: cart.motionDirection
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
        }

        if (pMirror == Mirror.FRONT_BACK) {
            val pRot = pMirror.getRotation(pState.getValue(FACING))
            return rotate(pState, pRot)
        }

        return pState
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(WATERLOGGED, FACING, RAIL_SHAPE, SWITCH_TYPE, POWERED)
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
            val flag = state.getValue(POWERED)
            if (flag != world.hasNeighborSignal(pos)) {
                world.setBlock(pos, state.cycle(POWERED), 2)
            }
        }
    }

    override fun canConnectRedstone(state: BlockState, world: BlockGetter, pos: BlockPos, side: Direction?) = true

    override fun getDrops(state: BlockState, builder: LootParams.Builder): List<ItemStack> =
        listOf(ItemStack(ModItems.SWITCH_RAIL_ITEM.get()))

    override fun asItem(): Item = ModItems.SWITCH_RAIL_ITEM.get()

    companion object {
        val CODEC: MapCodec<SwitchRail> = simpleCodec(::SwitchRail)

        /**
         * The turn direction when the cart enters from the root direction
         */
        val SWITCH_TYPE: EnumProperty<SwitchType> = EnumProperty.create("out_direction", SwitchType::class.java)
    }
}

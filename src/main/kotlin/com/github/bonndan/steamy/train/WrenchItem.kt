package com.github.bonndan.steamy.train

import net.minecraft.network.chat.Component
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.state.properties.RailShape
import java.util.Map
import java.util.function.Consumer

class WrenchItem(pProperties: Properties) : Item(pProperties) {

    private val wrenchInfo: Component = Component.translatable("item.steamy.conductors_wrench.description")

    @Deprecated("")
    override fun appendHoverText(
        stack: ItemStack,
        p_339594_: TooltipContext,
        p_399753_: TooltipDisplay,
        tooltip: Consumer<Component?>,
        flagIn: TooltipFlag
    ) {
        super.appendHoverText(stack, p_339594_, p_399753_, tooltip, flagIn)
        tooltip.accept(wrenchInfo)
    }

    override fun useOn(pContext: UseOnContext): InteractionResult {

        val state = pContext.level.getBlockState(pContext.clickedPos)

        if ( state.`is`(BlockTags.RAILS)) {
            val shape = state.getValue(RailBlock.SHAPE)
            if (shape.isSlope) {
                return InteractionResult.PASS
            }
            if (!pContext.level.isClientSide()) {
                pContext.level.setBlock(
                    pContext.clickedPos,
                    state.setValue<RailShape, RailShape>(RailBlock.SHAPE, nextShapes.getOrDefault(shape, shape)), 2
                )
            }
            return InteractionResult.SUCCESS
        } else {
            return super.useOn(pContext)
        }
    }

    companion object {
        private val nextShapes: MutableMap<RailShape, RailShape> = Map.ofEntries(
            Map.entry<RailShape, RailShape>(RailShape.EAST_WEST, RailShape.NORTH_SOUTH),
            Map.entry<RailShape, RailShape>(RailShape.NORTH_SOUTH, RailShape.NORTH_EAST),
            Map.entry<RailShape, RailShape>(RailShape.NORTH_EAST, RailShape.NORTH_WEST),
            Map.entry<RailShape, RailShape>(RailShape.NORTH_WEST, RailShape.SOUTH_WEST),
            Map.entry<RailShape, RailShape>(RailShape.SOUTH_WEST, RailShape.SOUTH_EAST),
            Map.entry<RailShape, RailShape>(RailShape.SOUTH_EAST, RailShape.EAST_WEST)
        )
    }
}

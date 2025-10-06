package com.github.bonndan.steamy.data.client

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.setup.ModBlocks
import com.github.bonndan.steamy.setup.ModItems
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.ModelProvider
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator
import net.minecraft.client.data.models.blockstates.PropertyDispatch
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.client.data.models.model.TextureMapping
import net.minecraft.data.PackOutput
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.RailShape


class ModItemModelProvider(output: PackOutput) : ModelProvider(output, SteamyMod.MOD_ID) {

    override fun registerModels(blockModels: BlockModelGenerators, itemModels: ItemModelGenerators) {

        itemModels.generateFlatItem(ModItems.LOCOMOTIVE.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.WAGON.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.SPRING.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.CONDUCTORS_WRENCH.get(), ModelTemplates.FLAT_ITEM)

        createPassiveRail(ModBlocks.SWITCH_RAIL.get(), blockModels)
        createPassiveRail(ModBlocks.JUNCTION_RAIL.get(), blockModels)
        createPassiveRail(ModBlocks.TEE_JUNCTION_RAIL.get(), blockModels)

    }

    /**
     * This is a copy of createPassiveRail from BlockModelGenerators with minor adjustments to fit the mod setup
     */
    fun createPassiveRail(block: Block, blockModels: BlockModelGenerators) {

        val flatTemplate = ModelTemplates.RAIL_FLAT.extend().renderType("minecraft:cutout").build()
        val curvedTemplate = ModelTemplates.RAIL_CURVED.extend().renderType("minecraft:cutout").build()

        val flatModel = flatTemplate.create(
            block,
            TextureMapping.rail(block),
            blockModels.modelOutput
        )

        val multivariant = BlockModelGenerators.plainVariant(flatModel)

        val multivariant1 = BlockModelGenerators.plainVariant(
            curvedTemplate.create(
                block,
                TextureMapping.rail(TextureMapping.getBlockTexture(block, "_corner")),
                blockModels.modelOutput
            )
        )

        blockModels.registerSimpleFlatItemModel(block)
        blockModels.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(block)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.RAIL_SHAPE)
                            .select(RailShape.NORTH_SOUTH, multivariant)
                            .select(RailShape.EAST_WEST, multivariant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.ASCENDING_EAST, multivariant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.ASCENDING_WEST, multivariant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.ASCENDING_NORTH, multivariant)
                            .select(RailShape.ASCENDING_SOUTH, multivariant)
                            .select(RailShape.SOUTH_EAST, multivariant1)
                            .select(RailShape.SOUTH_WEST, multivariant1.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.NORTH_WEST, multivariant1.with(BlockModelGenerators.Y_ROT_180))
                            .select(RailShape.NORTH_EAST, multivariant1.with(BlockModelGenerators.Y_ROT_270))
                    )
            )
    }
}

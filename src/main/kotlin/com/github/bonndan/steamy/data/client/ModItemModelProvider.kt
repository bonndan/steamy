package com.github.bonndan.steamy.data.client

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.rails.SwitchRail
import com.github.bonndan.steamy.setup.ModBlocks
import com.github.bonndan.steamy.setup.ModItems
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.ModelProvider
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator
import net.minecraft.client.data.models.blockstates.PropertyDispatch
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.client.data.models.model.TextureMapping
import net.minecraft.core.Direction
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.RailShape


class ModItemModelProvider(output: PackOutput) : ModelProvider(output, SteamyMod.MOD_ID) {

    override fun registerModels(blockModels: BlockModelGenerators, itemModels: ItemModelGenerators) {

        itemModels.generateFlatItem(ModItems.LOCOMOTIVE.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.WAGON.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.SPRING.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.CONDUCTORS_WRENCH.get(), ModelTemplates.FLAT_ITEM)

        createSwitchRail(blockModels)
        createJunctionRail(blockModels)
    }


    private fun createSwitchRail(blockModels: BlockModelGenerators) {

        val block = ModBlocks.SWITCH_RAIL.get()
        val railTemplate = ModelTemplates.RAIL_FLAT.extend().renderType("minecraft:cutout").build()

        // Create models for all combinations
        val models = mutableMapOf<String, ResourceLocation>()

        for (outDir in SwitchRail.SwitchType.entries) {
            for (powered in listOf(true, false)) {
                val poweredStr = if (powered) "on" else "off"
                val modelKey = "${outDir.serializedName}_$poweredStr"
                val textureLoc = getBlTx("switch_rail_$modelKey")

                // Create a unique ResourceLocation for this model variant
                val modelLocation = ResourceLocation.fromNamespaceAndPath(
                    SteamyMod.MOD_ID,
                    "block/switch_rail_$modelKey"
                )

                val model = railTemplate.create(
                    modelLocation,
                    TextureMapping.rail(textureLoc),
                    blockModels.modelOutput
                )
                models[modelKey] = model
            }
        }

        // Build the property dispatch using the three-property constructor
        val dispatch = PropertyDispatch.initial(
            SwitchRail.SWITCH_TYPE,
            BlockStateProperties.POWERED,
            BlockStateProperties.HORIZONTAL_FACING
        )

        for (outDir in SwitchRail.SwitchType.entries) {
            for (powered in listOf(true, false)) {
                val poweredStr = if (powered) "on" else "off"
                val modelKey = "${outDir.serializedName}_$poweredStr"

                for (facing in Direction.Plane.HORIZONTAL) {
                    val rotation = when (facing) {
                        Direction.NORTH -> BlockModelGenerators.Y_ROT_180
                        Direction.EAST -> BlockModelGenerators.Y_ROT_270
                        Direction.WEST -> BlockModelGenerators.Y_ROT_90
                        else -> BlockModelGenerators.NOP  // SOUTH
                    }

                    val variant = BlockModelGenerators.plainVariant(models[modelKey]!!).with(rotation)

                    dispatch.select(outDir, powered, facing, variant)
                }
            }
        }

        blockModels.registerSimpleFlatItemModel(block)
        blockModels.blockStateOutput.accept(
            MultiVariantGenerator.dispatch(block).with(dispatch)
        )
    }


    /**
     * This is a copy of createPassiveRail from BlockModelGenerators with minor adjustments to fit the mod setup
     */
    fun createJunctionRail(blockModels: BlockModelGenerators) {

        val block = ModBlocks.JUNCTION_RAIL.get()
        val flatTemplate = ModelTemplates.RAIL_FLAT.extend().renderType("minecraft:cutout").build()

        val flatModel = flatTemplate.create(
            block,
            TextureMapping.rail(block),
            blockModels.modelOutput
        )

        val variant = BlockModelGenerators.plainVariant(flatModel)

        blockModels.registerSimpleFlatItemModel(block)
        blockModels.blockStateOutput
            .accept(
                MultiVariantGenerator.dispatch(block)
                    .with(
                        PropertyDispatch.initial(BlockStateProperties.RAIL_SHAPE)
                            .select(RailShape.NORTH_SOUTH, variant)
                            .select(RailShape.EAST_WEST, variant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.ASCENDING_EAST, variant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.ASCENDING_WEST, variant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.ASCENDING_NORTH, variant)
                            .select(RailShape.ASCENDING_SOUTH, variant)
                            .select(RailShape.SOUTH_EAST, variant)
                            .select(RailShape.SOUTH_WEST, variant.with(BlockModelGenerators.Y_ROT_90))
                            .select(RailShape.NORTH_WEST, variant.with(BlockModelGenerators.Y_ROT_180))
                            .select(RailShape.NORTH_EAST, variant.with(BlockModelGenerators.Y_ROT_270))
                    )
            )
    }

    companion object {
        fun getBlTx(name: String): ResourceLocation {
            return ResourceLocation.fromNamespaceAndPath(SteamyMod.MOD_ID, String.format("block/%s", name))
        }
    }
}

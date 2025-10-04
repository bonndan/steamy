package com.github.bonndan.steamy.data.client

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.setup.ModBlocks
import com.github.bonndan.steamy.setup.ModItems
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.ModelProvider
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.data.PackOutput


class ModItemModelProvider(output: PackOutput) : ModelProvider(output, SteamyMod.MOD_ID) {

    override fun registerModels(blockModels: BlockModelGenerators, itemModels: ItemModelGenerators) {

        itemModels.generateFlatItem(ModItems.LOCOMOTIVE.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.SPRING.get(), ModelTemplates.FLAT_ITEM)
        itemModels.generateFlatItem(ModItems.CONDUCTORS_WRENCH.get(), ModelTemplates.FLAT_ITEM)

        blockModels.createPassiveRail(ModBlocks.SWITCH_RAIL.get())
        blockModels.createPassiveRail(ModBlocks.JUNCTION_RAIL.get())
        blockModels.createPassiveRail(ModBlocks.TEE_JUNCTION_RAIL.get())

    }

}

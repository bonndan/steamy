package com.github.bonndan.steamy.data

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.setup.ModBlocks
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.tags.BlockTags
import net.neoforged.neoforge.common.data.BlockTagsProvider
import java.util.concurrent.CompletableFuture

class ModBlockTagsProvider(
    output: PackOutput,
    lookupProvider: CompletableFuture<HolderLookup.Provider>
) : BlockTagsProvider(output, lookupProvider, SteamyMod.MOD_ID) {

    override fun addTags(provider: HolderLookup.Provider) {
        tag(BlockTags.RAILS)
            .add(ModBlocks.SWITCH_RAIL.get())

        tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .add(ModBlocks.SWITCH_RAIL.get())
    }
}

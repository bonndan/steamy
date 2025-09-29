package com.github.bonndan.steamy.data

import com.github.bonndan.steamy.setup.ModItems
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Items
import java.util.concurrent.CompletableFuture

class ModRecipeProvider(recipeOutput: RecipeOutput, pRegistries: HolderLookup.Provider) :
    RecipeProvider(pRegistries, recipeOutput) {

    override fun buildRecipes() {

        this.shaped(RecipeCategory.TRANSPORTATION, Items.RAIL, 16)
            .pattern("I I")
            .pattern("WWW")
            .pattern("GGG")
            .define('I', Items.IRON_INGOT)
            .define('G', Items.GRAVEL)
            .define('W', ItemTags.LOGS)
            .unlockedBy("has_item", has(Items.FURNACE))
            .save(output, "steamy:rails")


        this.shapeless(RecipeCategory.TRANSPORTATION, ModItems.LOCOMOTIVE.get(), 1)
            .requires ( Items.FURNACE_MINECART)
            .requires ( Items.LEVER)
            .unlockedBy("has_item", has(Items.FURNACE))
            .save(output)
    }

    class Runner(output: PackOutput, lookupProvider: CompletableFuture<HolderLookup.Provider>) :
        RecipeProvider.Runner(output, lookupProvider) {

        @Override
        override fun createRecipeProvider(lookupProvider: HolderLookup.Provider, output: RecipeOutput) =
            ModRecipeProvider(output, lookupProvider)

        @Override
        override fun getName(): String {
            return "Steamy Recipes"
        }
    }
}

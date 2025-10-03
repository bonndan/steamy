package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod.Companion.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.core.registries.Registries.ITEM
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceLocation.fromNamespaceAndPath
import net.minecraft.tags.TagKey
import net.minecraft.tags.TagKey.create
import net.minecraft.world.item.Item


class ModTags {

    object Items {
        val WRENCHES: TagKey<Item> = create(ITEM, fromNamespaceAndPath("forge", "tools/wrench"))

        private fun mod(path: String): TagKey<Item?> {
            return TagKey.create<Item?>(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
            )
        }
    }
}

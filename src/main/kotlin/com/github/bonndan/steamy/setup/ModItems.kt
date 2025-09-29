package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.locomotive.item.LocomotiveItem
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import java.util.function.Supplier

object ModItems {

    private val PRIVATE_TAB_REGISTRY = MultiMap<ResourceKey<CreativeModeTab>, Supplier<out Item>>()

    val LOCOMOTIVE = Registration.ITEMS.registerItem<Item>(
        "locomotive",
        { it: Item.Properties -> LocomotiveItem(it) },
        defaultItemProperties(1)
    )

    init {
        PRIVATE_TAB_REGISTRY.putInsert(CreativeModeTabs.TOOLS_AND_UTILITIES, LOCOMOTIVE)
    }

    fun buildCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        PRIVATE_TAB_REGISTRY.getOrDefault(event.tabKey, ArrayList())
            .forEach { supplier -> event.accept(supplier.get()) }
    }

    fun register() {}


    private fun defaultItemProperties(pMaxStackSize: Int = 64): Item.Properties {
        return Item.Properties().stacksTo(pMaxStackSize)
    }
}

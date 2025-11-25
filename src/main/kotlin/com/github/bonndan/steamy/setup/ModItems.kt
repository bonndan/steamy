package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.setup.ModBlocks.SWITCH_RAIL
import com.github.bonndan.steamy.train.SpringItem
import com.github.bonndan.steamy.train.WrenchItem
import com.github.bonndan.steamy.wagons.item.LocomotiveItem
import com.github.bonndan.steamy.wagons.item.WagonItem
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModItems {

    private val PRIVATE_TAB_REGISTRY = MultiMap<ResourceKey<CreativeModeTab>, Supplier<out Item>>()

    lateinit var LOCOMOTIVE : DeferredItem<Item>
    lateinit var WAGON : DeferredItem<Item>
    lateinit var SPRING: DeferredItem<Item>
    lateinit var CONDUCTORS_WRENCH: DeferredItem<Item>

    lateinit var SWITCH_RAIL_ITEM: DeferredItem<BlockItem>

    fun register(itemRegister: DeferredRegister.Items) {

        LOCOMOTIVE = itemRegister.registerItem(
            "locomotive",
            { it: Item.Properties -> LocomotiveItem(it) },
            defaultItemProperties(1)
        )

        WAGON = itemRegister.registerItem(
            "wagon",
            { it: Item.Properties -> WagonItem(it) },
            defaultItemProperties(1)
        )

        SPRING = itemRegister.registerItem(
            "spring",
            { it: Item.Properties -> SpringItem(it) },
            defaultItemProperties()
        )

         CONDUCTORS_WRENCH= itemRegister.registerItem(
            "conductors_wrench",
            { it -> WrenchItem(it) },
            defaultItemProperties(1)
        )

        SWITCH_RAIL_ITEM = itemRegister.registerItem(
            "switch_rail",
            { properties -> BlockItem(SWITCH_RAIL.get(), properties) },
            defaultItemProperties()
        )

        PRIVATE_TAB_REGISTRY.putInsert(CreativeModeTabs.TOOLS_AND_UTILITIES, LOCOMOTIVE)
        PRIVATE_TAB_REGISTRY.putInsert(CreativeModeTabs.TOOLS_AND_UTILITIES, WAGON)
        PRIVATE_TAB_REGISTRY.putInsert(CreativeModeTabs.TOOLS_AND_UTILITIES, SPRING)
        PRIVATE_TAB_REGISTRY.putInsert(CreativeModeTabs.TOOLS_AND_UTILITIES, CONDUCTORS_WRENCH)
        PRIVATE_TAB_REGISTRY.putInsert(CreativeModeTabs.TOOLS_AND_UTILITIES, SWITCH_RAIL_ITEM)

    }

    fun buildCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        PRIVATE_TAB_REGISTRY.getOrDefault(event.tabKey, ArrayList())
            .forEach { supplier -> event.accept(supplier.get()) }
    }

    private fun defaultItemProperties(pMaxStackSize: Int = 64): Item.Properties {
        return Item.Properties().stacksTo(pMaxStackSize)
    }
}

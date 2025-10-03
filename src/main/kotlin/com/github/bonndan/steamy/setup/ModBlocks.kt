package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.rails.JunctionRail
import com.github.bonndan.steamy.rails.SwitchRail
import com.github.bonndan.steamy.rails.TeeJunctionRail
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier


object ModBlocks {

    private val PRIVATE_TAB_REGISTRY = MultiMap<ResourceKey<CreativeModeTab>, Supplier<BlockItem>>()
    private val RAIL_BLOCK_BEHAVIOUR = BlockBehaviour.Properties.ofFullCopy(Blocks.RAIL)

    lateinit var SWITCH_RAIL: DeferredHolder<Block, SwitchRail>

    lateinit var TEE_JUNCTION_RAIL: DeferredHolder<Block, TeeJunctionRail>

    lateinit var JUNCTION_RAIL: DeferredHolder<Block, JunctionRail>

    fun buildCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        PRIVATE_TAB_REGISTRY.getOrDefault(event.tabKey, ArrayList())
            .forEach { supplier: Supplier<BlockItem> -> event.accept(supplier.get()) }
    }

    fun register(register: DeferredRegister.Blocks) {

        SWITCH_RAIL = register.registerBlock(
            "switch_rail",
             { properties ->SwitchRail(properties, false) }
        )

        TEE_JUNCTION_RAIL = register.registerBlock(
            "tee_junction_rail",
             { properties -> TeeJunctionRail(properties, false) }

        )

        JUNCTION_RAIL = register.registerBlock(
            "junction_rail",
             {properties -> JunctionRail(properties) },
            RAIL_BLOCK_BEHAVIOUR
        )
    }
}

package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod
import com.google.common.base.Supplier
import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object ModDataComponents {

    private val DATA_COMPONENTS: DeferredRegister<DataComponentType<*>> =
        DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, SteamyMod.MOD_ID)

    val LINKED_ENTITY: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> = DATA_COMPONENTS.register(
        "linked_entity",
        Supplier { DataComponentType.builder<Int>().persistent(Codec.INT).build() }
    )

    fun register(eventBus: IEventBus) {
        DATA_COMPONENTS.register(eventBus)
    }
}


package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.locomotive.entity.LocomotiveEntity
import com.github.bonndan.steamy.setup.Registration.ENTITIES
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.Level
import java.util.function.Supplier


object ModEntityTypes {

    fun register() {
    }

    val LOCOMOTIVE: Supplier<EntityType<LocomotiveEntity>> =
        ENTITIES.register("locomotive", Supplier {
            EntityType.Builder.of(
                { type: EntityType<LocomotiveEntity>, level: Level -> LocomotiveEntity(type, level) },
                MobCategory.MISC
            )
                .sized(1.0f, 1.0f)
                .clientTrackingRange(8)
                .setShouldReceiveVelocityUpdates(true)
                .build(asResourceKey("locomotive"))
        })


    private fun asResourceKey(path: String): ResourceKey<EntityType<*>?> = ResourceKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath(SteamyMod.MOD_ID, path)
    )
}

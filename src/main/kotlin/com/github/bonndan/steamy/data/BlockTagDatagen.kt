package com.github.bonndan.steamy.data

import com.github.bonndan.steamy.SteamyMod
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent

@EventBusSubscriber(modid = SteamyMod.MOD_ID)
object BlockTagDatagen {

    @SubscribeEvent
    fun gatherData(event: GatherDataEvent.Server) {
        val gen = event.generator
        val packOutput = gen.packOutput
        val lookupProvider = event.lookupProvider

        gen.addProvider(true, ModBlockTagsProvider(packOutput, lookupProvider))
    }
}

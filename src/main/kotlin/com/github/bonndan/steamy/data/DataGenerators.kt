@file:Suppress("unused")

package com.github.bonndan.steamy.data

import com.github.bonndan.steamy.SteamyMod
import com.github.bonndan.steamy.data.client.ModItemModelProvider
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent

@EventBusSubscriber(modid = SteamyMod.MOD_ID)
object DataGenerators {

    @SubscribeEvent
    fun gatherData(gatherDataEvent: GatherDataEvent.Client) {

        val gen = gatherDataEvent.generator
        val pack = gen.packOutput
        val lookupProvider = gatherDataEvent.lookupProvider

        gen.addProvider(true, ModItemModelProvider(pack))

        gatherDataEvent.createProvider {
            ModRecipeProvider.Runner(pack, lookupProvider)
        }
    }
}

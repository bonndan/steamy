package com.github.bonndan.steamy

import com.github.bonndan.steamy.setup.Registration
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod

@Mod(SteamyMod.MOD_ID)
class SteamyMod(modBus: IEventBus, container: ModContainer) {

    init {
        Registration.register(modBus)
    }

    companion object {
        // The value here should match an entry in the META-INF/mods.toml file
        const val MOD_ID: String = "steamy"
    }
}

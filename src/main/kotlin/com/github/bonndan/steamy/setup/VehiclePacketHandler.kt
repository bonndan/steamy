package com.github.bonndan.steamy.setup

import com.github.bonndan.steamy.wagons.entity.LocomotiveEntity
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.*

/**
 * Handles event that are sent from client to server.
 */
object VehiclePacketHandler {

    @SubscribeEvent
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        registrar.playBidirectional(
            SetThrottlePacket.TYPE,
            SetThrottlePacket.STREAM_CODEC,
            { obj, ctx -> handleSetThrottle(obj, ctx) },
            { obj, ctx -> handleSetThrottleClient(obj, ctx) }
        )
    }

    fun sendToServer(payload: CustomPacketPayload) {
        ClientPacketDistributor.sendToServer(payload)
    }

    private fun handleSetThrottle(operation: SetThrottlePacket, ctx: IPayloadContext) {
        ctx.enqueueWork {
            Optional.of(ctx)
                .map { it.player() }
                .ifPresent { serverPlayer ->
                    val entity = serverPlayer.level().getEntity(operation.locoId)
                    if (entity != null && entity.distanceTo(serverPlayer) < 6 && entity is LocomotiveEntity) {
                        println("set throttle = [${operation.throttle}]")
                        entity.setThrottle(operation.throttle)
                    }
                }
        }
    }

    private fun handleSetThrottleClient(operation: SetThrottlePacket, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val client = Minecraft.getInstance()
            val loco = client.level?.getEntity(operation.locoId)
            if (loco is LocomotiveEntity) {
                loco.setThrottle(operation.throttle)
            }
        }
    }
}

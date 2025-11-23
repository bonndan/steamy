package com.github.bonndan.steamy.wagons.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.vehicle.AbstractMinecart.exits
import net.minecraft.world.entity.vehicle.OldMinecartBehavior
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FixedBehavior(private val behavior: OldMinecartBehavior, private val abstractMinecart: AbstractMinecart) : OldMinecartBehavior(abstractMinecart) {


    override fun tick() {
        behavior.tick()
    }

    override fun moveAlongTrack(p_376849_: ServerLevel) {
        behavior.moveAlongTrack(p_376849_)
    }

    override fun stepAlongTrack(
        p_360466_: BlockPos,
        p_361422_: RailShape,
        p_363822_: Double
    ): Double {
        return behavior.stepAlongTrack(p_360466_, p_361422_, p_363822_)
    }

    override fun pushAndPickupEntities(): Boolean {
        return behavior.pushAndPickupEntities()
    }

    override fun getMaxSpeed(serverLevel: ServerLevel): Double {
        return behavior.getMaxSpeed(serverLevel)
    }

    override fun getSlowdownFactor(): Double {
        return behavior.getSlowdownFactor()
    }

    /**
     * This method returns the specific position on the track at
     * pOffset blocks from the current position. This overridden
     * method takes into account of the minecart's yRot, which
     * the vanilla code does not (leading to lots of flipping)
     *
     * Possible workaround for https://bugs.mojang.com/browse/MC/issues/MC-9551
     * Compare this with OldMinecartBehavior#getPosOffs, which could maybe be overridden be overwriting the getBehavior
     * method in AbstractMinecart
     */
    override fun getPosOffs(pX: Double, pY: Double, pZ: Double, pOffset: Double): Vec3? {

        val entity = this.abstractMinecart
        var pX = pX
        var pY = pY
        var pZ = pZ
        val i: Int = Mth.floor(pX)
        var j: Int = Mth.floor(pY)
        val k: Int = Mth.floor(pZ)
        if (entity.level().getBlockState(BlockPos(i, j - 1, k)).`is`(BlockTags.RAILS)) {
            --j
        }

        val blockstate: BlockState = entity.level().getBlockState(BlockPos(i, j, k))
        if (BaseRailBlock.isRail(blockstate)) {
            val railshape: RailShape = (blockstate.block as BaseRailBlock).getRailDirection(
                blockstate,
                entity.level(),
                BlockPos(i, j, k),
                entity
            )
            pY = j.toDouble()
            if (railshape.isSlope) {
                pY = (j + 1).toDouble()
            }

            val pair = exits(railshape)
            var exit1: Vec3i = pair.first
            var exit2: Vec3i = pair.second

            // check if need to swap end points to make calculation correct
            val yawX = -sin(Math.toRadians(entity.yRot.toDouble()))
            val yawZ = cos(Math.toRadians(entity.yRot.toDouble()))
            if (Vec3(yawX, 0.0, yawZ).dot(
                    Vec3(
                        (exit2.x - exit1.x).toDouble(),
                        (exit2.y - exit1.y).toDouble(),
                        (exit2.z - exit1.z).toDouble()
                    )
                ) <= 0
            ) {
                val temp: Vec3i = exit1
                exit1 = exit2
                exit2 = temp
            }

            // get direction from e1 to e2
            var xDiff = (exit2.x - exit1.x).toDouble()
            var zDiff = (exit2.z - exit1.z).toDouble()
            // normalize x and z diff
            val dist = sqrt(xDiff * xDiff + zDiff * zDiff)
            xDiff /= dist
            zDiff /= dist
            pX += xDiff * pOffset
            pZ += zDiff * pOffset
            if (exit1.y != 0 && Mth.floor(pX) - i == exit1.x && Mth.floor(pZ) - k == exit1.z) {
                pY += exit1.y.toDouble()
            } else if (exit2.y != 0 && Mth.floor(pX) - i == exit2.x && Mth.floor(pZ) - k == exit2.z) {
                pY += exit2.y.toDouble()
            }

            return this.getPos(pX, pY, pZ)
        } else {
            return null
        }
    }
}

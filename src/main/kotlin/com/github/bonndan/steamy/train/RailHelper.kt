package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.rails.MultiShapeRail
import com.google.common.collect.Maps
import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.abs


object RailHelper {

    class RailDir {
        var horizontal: Direction
        var above: Boolean

        internal constructor(h: Direction, v: Boolean) {
            horizontal = h
            above = v
        }

        internal constructor(h: Direction) {
            horizontal = h
            above = false
        }
    }

    fun getShape(minecart: AbstractMinecart, pos: BlockPos, direction: Direction): RailShape {
        val state: BlockState = minecart.level().getBlockState(pos)
        if (state.block is MultiShapeRail) {
            return (state.block as MultiShapeRail).getVanillaRailShapeFromDirection(
                state, pos, minecart.level(), direction
            )
        }

        return (state.block as BaseRailBlock).getRailDirection(state, minecart.level(), pos, minecart)
    }


    fun traverseBi(
        minecart: AbstractMinecart,
        railPos: BlockPos,
        predicate: BiPredicate<Direction, BlockPos>,
        limit: Int,
    ): Optional<Pair<Direction, Int>> {

        return getRail(railPos, minecart.level())
            .flatMap(Function { pos ->
                val shape: RailShape = getShape(minecart, pos, minecart.direction.opposite)
                val dirs = EXITS_DIRECTION[shape]!!
                val first = traverse(minecart, pos, minecart.level(), dirs.second.horizontal.opposite, predicate, limit)
                val second = traverse(minecart, pos, minecart.level(), dirs.first.horizontal.opposite, predicate, limit)
                val result: Optional<Pair<Direction, Int>> = if (second.isEmpty) {
                    first.map(Function { i -> Pair(dirs.first.horizontal, i) })
                } else if (first.isEmpty) {
                    second.map(Function { i -> Pair(dirs.second.horizontal, i) })
                } else {
                    Optional.of(
                        if (first.get() < second.get()) Pair(dirs.first.horizontal, first.get())
                        else Pair(dirs.second.horizontal, second.get())
                    )
                }

                result
            })
    }

    fun traverse(
        minecart: AbstractMinecart,
        railPos: BlockPos,
        level: Level,
        prevExitTaken: Direction,
        predicate: BiPredicate<Direction, BlockPos>,
        limit: Int
    ): Optional<Int> {
        if (predicate.test(prevExitTaken, railPos)) {
            return Optional.of(0)
        } else if (limit < 1) {
            return Optional.empty()
        }
        val entrance = prevExitTaken.opposite
        return getRail(railPos, level).flatMap(Function { pos ->
            val shape: RailShape = getShape(minecart, pos, prevExitTaken)
            getOtherExit(entrance, shape).flatMap(Function { raildir: RailDir? ->
                traverse(
                    minecart,
                    if (raildir!!.above) pos.relative(raildir.horizontal)
                        .above() else pos.relative(raildir.horizontal),
                    level,
                    raildir.horizontal,
                    predicate,
                    limit - 1
                ).map(Function { ans: Int? -> ans!! + 1 })
            })
        })
    }

    private fun getNormal(dir: Direction): Vec3i {
        return Vec3i(dir.stepX, dir.stepY, dir.stepZ)
    }

    val EXITS: MutableMap<RailShape?, Pair<Vec3i, Vec3i>> =
        Util.make(
            Maps.newEnumMap<RailShape, Pair<Vec3i, Vec3i>>(RailShape::class.java),
            Consumer { map ->
                val west: Vec3i = getNormal(Direction.WEST)
                val east: Vec3i = getNormal(Direction.EAST)
                val north: Vec3i = getNormal(Direction.NORTH)
                val south: Vec3i = getNormal(Direction.SOUTH)
                val westb: Vec3i = west.below()
                val eastb: Vec3i = east.below()
                val nothb: Vec3i = north.below()
                val southb: Vec3i = south.below()
                map[RailShape.NORTH_SOUTH] = Pair(north, south)
                map[RailShape.EAST_WEST] = Pair(west, east)
                map[RailShape.ASCENDING_EAST] = Pair(westb, east)
                map[RailShape.ASCENDING_WEST] = Pair(west, eastb)
                map[RailShape.ASCENDING_NORTH] = Pair(north, southb)
                map[RailShape.ASCENDING_SOUTH] = Pair(nothb, south)
                map[RailShape.SOUTH_EAST] = Pair(south, east)
                map[RailShape.SOUTH_WEST] = Pair(south, west)
                map[RailShape.NORTH_WEST] = Pair(north, west)
                map[RailShape.NORTH_EAST] = Pair(north, east)
            })


    val EXITS_DIRECTION: MutableMap<RailShape, Pair<RailDir, RailDir>> =
        Util.make<EnumMap<RailShape, Pair<RailDir, RailDir>>>(
            Maps.newEnumMap<RailShape, Pair<RailDir, RailDir>>(
                RailShape::class.java
            ), Consumer { map ->
                map[RailShape.NORTH_SOUTH] = Pair(RailDir(Direction.NORTH), RailDir(Direction.SOUTH))
                map[RailShape.EAST_WEST] = Pair(
                    RailDir(Direction.WEST), RailDir(Direction.EAST)
                )
                map[RailShape.ASCENDING_EAST] = Pair(
                    RailDir(Direction.WEST), RailDir(Direction.EAST, true)
                )
                map[RailShape.ASCENDING_WEST] = Pair(
                    RailDir(
                        Direction.WEST, true
                    ), RailDir(Direction.EAST)
                )
                map[RailShape.ASCENDING_NORTH] = Pair(
                    RailDir(
                        Direction.NORTH, true
                    ), RailDir(Direction.SOUTH)
                )
                map[RailShape.ASCENDING_SOUTH] = Pair(
                    RailDir(Direction.NORTH), RailDir(Direction.SOUTH, true)
                )
                map[RailShape.SOUTH_EAST] = Pair(
                    RailDir(Direction.SOUTH), RailDir(Direction.EAST)
                )
                map[RailShape.SOUTH_WEST] = Pair(
                    RailDir(Direction.WEST), RailDir(Direction.SOUTH)
                )
                map[RailShape.NORTH_WEST] = Pair(
                    RailDir(Direction.WEST), RailDir(Direction.NORTH)
                )
                map[RailShape.NORTH_EAST] = Pair(
                    RailDir(Direction.NORTH), RailDir(Direction.EAST)
                )
            })

    fun getShape(pos: BlockPos, level: Level): RailShape {
        val state: BlockState = level.getBlockState(pos)
        return (state.block as BaseRailBlock).getRailDirection(state, level, pos, null)
    }

    fun getRail(
        inpos: BlockPos,
        level: Level
    ): Optional<BlockPos> { // if using with carts, pass in getOnPos.above
        for (pos in listOf(inpos, inpos.below())) { // check for ascending rail.
            val state: BlockState = level.getBlockState(pos)
            if (state.block is BaseRailBlock) {
                return Optional.of(pos)
            }
        }
        return Optional.empty()
    }

    fun directionFromVelocity(deltaMovement: Vec3): Direction =
        if (abs(deltaMovement.x) > abs(deltaMovement.z)) {
            if (deltaMovement.x > 0) Direction.EAST else Direction.WEST
        } else {
            if (deltaMovement.z > 0) Direction.SOUTH else Direction.NORTH
        }

    fun getOtherExit(direction: Direction?, shape: RailShape?): Optional<RailDir> {

        val dirs = EXITS_DIRECTION[shape]!!

        return if (dirs.first.horizontal == direction) {
            Optional.of(dirs.second)
        } else if (dirs.second.horizontal == direction) {
            Optional.of(dirs.first)
        } else {
            Optional.empty()
        }
    }

    fun getDirectionToOtherExit(
        direction: Direction, shape: RailShape?
    ): Optional<Vec3i?> {
        return getOtherExit(direction, shape).map<Vec3i?>(Function { other: RailDir? ->
            getNormal(direction).subtract(getNormal(other!!.horizontal))
        })
    }

    fun samePositionPredicate(entity: AbstractMinecart): BiPredicate<Direction, BlockPos> {
        val targetRail = getRail(entity.getOnPos().above(), entity.level())
        return BiPredicate { direction: Direction?, p ->
            getRail(p, entity.level())
                .flatMap(Function { pos: BlockPos? -> targetRail.map(Function { rp: BlockPos? -> rp == pos }) })
                .orElse(false)
        }
    }

}

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
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Function


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

        return getRailAt(railPos, minecart.level())
            .flatMap(Function { pos ->
                val shape: RailShape = getShape(minecart, pos, minecart.direction.opposite)
                val directions: Pair<RailDir, RailDir> = EXITS_DIRECTION[shape]!!
                val first =
                    traverse(minecart, pos, minecart.level(), directions.second.horizontal.opposite, predicate, limit)
                val second =
                    traverse(minecart, pos, minecart.level(), directions.first.horizontal.opposite, predicate, limit)
                val result: Optional<Pair<Direction, Int>> = when {
                    second.isEmpty -> {
                        first.map { Pair(directions.first.horizontal, it) }
                    }

                    first.isEmpty -> {
                        second.map { Pair(directions.second.horizontal, it) }
                    }

                    else -> {
                        Optional.of(
                            if (first.get() < second.get()) Pair(directions.first.horizontal, first.get())
                            else Pair(directions.second.horizontal, second.get())
                        )
                    }
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
        return getRailAt(railPos, level)
            .flatMap { pos ->
                val shape: RailShape = getShape(minecart, pos, prevExitTaken)
                getOtherExit(entrance, shape)
                    .flatMap { raildir ->

                        val railPos1 = if (raildir.above) {
                            pos.relative(raildir.horizontal).above()
                        } else {
                            pos.relative(raildir.horizontal)
                        }

                        traverse(
                            minecart,
                            railPos1,
                            level,
                            raildir.horizontal,
                            predicate,
                            limit - 1
                        ).map { ans -> ans + 1 }
                    }
            }
    }


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

    fun getRailAt(
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

    fun getOtherExit(direction: Direction, shape: RailShape): Optional<RailDir> {

        val dirs = EXITS_DIRECTION[shape]!!

        return if (dirs.first.horizontal == direction) {
            Optional.of(dirs.second)
        } else if (dirs.second.horizontal == direction) {
            Optional.of(dirs.first)
        } else {
            Optional.empty()
        }
    }


}

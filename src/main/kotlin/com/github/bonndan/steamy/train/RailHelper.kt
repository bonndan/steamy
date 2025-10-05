package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.rails.MultiShapeRail
import com.google.common.collect.Maps
import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import kotlin.math.abs


class RailHelper(private val minecart: AbstractTrainCarEntity) {

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

    fun getShape(pos: BlockPos, direction: Direction): RailShape {
        val state: BlockState = minecart.level().getBlockState(pos)
        if (state.block is MultiShapeRail) {
            return (state.block as MultiShapeRail).getVanillaRailShapeFromDirection(
                state, pos, minecart.level(), direction
            )
        } else {
            return (state.block as BaseRailBlock).getRailDirection(state, minecart.level(), pos, minecart)
        }
    }


    fun traverseBi(
        railPos: BlockPos,
        predicate: BiPredicate<Direction, BlockPos>,
        limit: Int,
        car: AbstractTrainCarEntity
    ): Optional<Pair<Direction, Int>> {

        return getRail(railPos, minecart.level())
            .flatMap(Function { pos ->
                val shape: RailShape = getShape(pos, car.direction.opposite)
                val dirs = EXITS_DIRECTION.get(shape)!!
                val first = traverse(pos, minecart.level(), dirs.second.horizontal.opposite, predicate, limit)
                val second = traverse(pos, minecart.level(), dirs.first.horizontal.opposite, predicate, limit)
                val result: Optional<Pair<Direction, Int>> = if (second.isEmpty) {
                    first.map(Function { i -> Pair(dirs.first!!.horizontal, i) })
                } else if (first.isEmpty) {
                    second.map(Function { i -> Pair(dirs.second!!.horizontal, i) })
                } else {
                    Optional.of(
                        if (first.get() < second.get()) Pair(dirs.first.horizontal, first.get())
                        else Pair(dirs.second!!.horizontal, second.get())
                    )
                }

                result
            })
    }

    fun traverse(
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
            val shape: RailShape = getShape(pos, prevExitTaken)
            getOtherExit(entrance, shape).flatMap(Function { raildir: RailDir? ->
                traverse(
                    if (raildir!!.above) pos.relative(raildir.horizontal)
                        .above() else pos.relative(raildir.horizontal),
                    level,
                    raildir.horizontal,
                    predicate,
                    limit - 1
                ).map<Int?>(Function { ans: Int? -> ans!! + 1 })
            })
        })
    }

    fun getNext(
        railpos: BlockPos, direction: Direction
    ): Optional<Pair<BlockPos?, Direction?>?> {
        val shape: RailShape = getShape(railpos, direction)
        val entrance = direction.opposite

        return getOtherExit(entrance, shape).flatMap<Pair<BlockPos?, Direction?>?>(Function { raildir: RailDir? ->
            getRail(
                if (raildir!!.above) railpos.relative(raildir.horizontal)
                    .above() else railpos.relative(raildir.horizontal), minecart.level()
            ).map(Function { pos: BlockPos? ->
                Pair<BlockPos?, Direction?>(
                    pos, raildir.horizontal
                )
            })
        })
    }

    class RailPathFindNode(
        var pos: BlockPos,
        var prevExitTaken: Direction,
        var pathLength: Int,
        var heuristicValue: Double
    ) : Comparable<RailPathFindNode> {

        override fun compareTo(o: RailPathFindNode): Int {
            if (this.heuristicValue == o.heuristicValue) {
                return this.pathLength - o.pathLength
            } else return if (this.heuristicValue - o.heuristicValue < 0) -1 else 1
        }
    }

    /**
     * @param prevExitTaken the direction of travel for the train
     */
    private fun getNextNodes(
        pos: BlockPos, prevExitTaken: Direction
    ): MutableList<RailDir> {
        val inputSide = prevExitTaken.opposite

        // todo: we need to check if blocks are actually loaded
        val state: BlockState = minecart.level().getBlockState(pos)
        if (state.block is MultiShapeRail) {
            // if rail is a MultiShapeRail, return all possible outputs from the input side
            // it doesn't matter if this rail is automatically switching.
            val r = state.block as MultiShapeRail
            return r.getPossibleOutputDirections(state, inputSide).stream()
                .map { RailDir(it) }
                .collect(Collectors.toList())
        }

        val shape: RailShape = getShape(pos, prevExitTaken)
        val shapes: MutableList<RailShape?> = mutableListOf(shape)
        return shapes.stream().map<RailDir?> { shape1: RailShape? ->
            val dirs = EXITS_DIRECTION[shape]!!
            if (dirs.first.horizontal == inputSide) {
                return@map dirs.second
            } else if (dirs.second.horizontal == inputSide) {
                return@map dirs.first
            }
            null
        }.filter { obj -> Objects.nonNull(obj) }.collect(Collectors.toList())
    }

    fun pathfind(
        railPos: BlockPos, prevDirTaken: Direction, heuristic: Function<BlockPos, Double>
    ): Optional<RailPathFindNode> {
        val visited: MutableSet<Pair<BlockPos?, Direction?>?> = HashSet()
        val queue = PriorityQueue<RailPathFindNode>()
        val ends = PriorityQueue<RailPathFindNode>()
        queue.add(RailPathFindNode(railPos, prevDirTaken, 0, heuristic.apply(railPos)!!))

        while (!queue.isEmpty() && visited.size < MAX_VISITED && queue.peek().heuristicValue > 0.0) {
            val curr = queue.poll()
            // already explored this path
            if (visited.contains(
                    Pair<BlockPos?, Direction?>(
                        curr.pos, curr.prevExitTaken
                    )
                )
            ) continue

            visited.add(
                Pair<BlockPos?, Direction?>(
                    curr.pos, curr.prevExitTaken
                )
            )

            getNextNodes(curr.pos, curr.prevExitTaken)!!.forEach(Consumer { raildir: RailDir? ->
                val pos: BlockPos = if (raildir!!.above) curr.pos.relative(raildir.horizontal)
                    .above() else curr.pos.relative(raildir.horizontal)
                if (minecart.level().getBlockState(pos).`is`(Blocks.VOID_AIR)) {
                    ends.add(RailPathFindNode(pos, raildir.horizontal, curr.pathLength + 1, heuristic.apply(pos)!!))
                } else {
                    getRail(pos, minecart.level()).ifPresent(Consumer { nextPos ->
                        queue.add(
                            RailPathFindNode(
                                nextPos,
                                raildir.horizontal,
                                curr.pathLength + 1,
                                heuristic.apply(nextPos)
                            )
                        )
                    })
                }
            })
        }

        queue.addAll(ends)
        return if (queue.isEmpty()) Optional.empty() else Optional.of(queue.peek())
    }

    fun pickCheaperDir(
        directions: MutableList<Direction>,
        pos: BlockPos,
        heuristic: Function<BlockPos, Double>,
        level: Level
    ): Direction {
        // get all directions where output has a possible rail
        val hasOutputDirections: MutableList<Pair<Direction, BlockPos>> =
            directions.stream().map { d ->
                Pair(d, getRail(pos.relative(d), level))
            }.filter { p ->
                p!!.second!!.isPresent
            }.map { p ->
                Pair(p!!.first, p.second.get())
            }.collect(Collectors.toList())

        // fallback
        if (hasOutputDirections.isEmpty()) return directions.get(0)

        val hasPath: MutableList<Pair<Direction, RailPathFindNode>> = hasOutputDirections.stream()
            .map { p -> Pair(p!!.first, pathfind(p.second, p.first!!, heuristic)) }
            .filter { p -> p!!.second!!.isPresent }
            .map { p -> Pair(p!!.first, p.second!!.get()) }
            .collect(Collectors.toList())

        // fallback
        if (hasPath.isEmpty()) return hasOutputDirections[0].first

        val best = hasPath.stream()
            .min(Comparator.comparing(Function { obj -> obj!!.second }))
            .get()
        return best.first
    }

    companion object {
        private fun getNormal(dir: Direction): Vec3i {
            return Vec3i(dir.stepX, dir.stepY, dir.stepZ)
        }

        val EXITS: MutableMap<RailShape?, Pair<Vec3i, Vec3i>> =
            Util.make<EnumMap<RailShape, Pair<Vec3i, Vec3i>>>(
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

        private const val MAX_VISITED = 200

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
            inpos: BlockPos, level: Level
        ): Optional<BlockPos> { // if using with carts, pass in getOnPos.above
            for (pos in listOf(inpos, inpos.below())) { // check for ascending rail.
                val state: BlockState = level.getBlockState(pos)
                if (state.block is BaseRailBlock) {
                    return Optional.of(pos)
                }
            }
            return Optional.empty()
        }

        fun directionFromVelocity(deltaMovement: Vec3): Direction {
            if (abs(deltaMovement.x) > abs(deltaMovement.z)) {
                return if (deltaMovement.x > 0) Direction.EAST else Direction.WEST
            } else {
                return if (deltaMovement.z > 0) Direction.SOUTH else Direction.NORTH
            }
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

        fun samePositionPredicate(entity: AbstractTrainCarEntity): BiPredicate<Direction, BlockPos> {
            val targetRail = getRail(entity.getOnPos().above(), entity.level())
            return BiPredicate { direction: Direction?, p ->
                getRail(p, entity.level())
                    .flatMap(Function { pos: BlockPos? -> targetRail.map(Function { rp: BlockPos? -> rp == pos }) })
                    .orElse(false)
            }
        }

        fun samePositionHeuristic(p: BlockPos): Function<BlockPos, Double> {
            return (Function { p_123332_ -> p.distSqr(p_123332_) })
        }

        fun samePositionHeuristicSet(potentialDestinations: MutableSet<BlockPos>): Function<BlockPos, Double> {
            return (Function { pos ->
                potentialDestinations.stream()
                    .map { p -> p.distSqr(pos) }
                    .min(Comparator { obj, anotherDouble -> obj!!.compareTo(anotherDouble!!) })
                    .orElse(0.0)
            })
        }

        fun toVec3(dir: Vec3i): Vec3 {
            return Vec3(dir.x.toDouble(), dir.y.toDouble(), dir.z.toDouble())
        }
    }


}

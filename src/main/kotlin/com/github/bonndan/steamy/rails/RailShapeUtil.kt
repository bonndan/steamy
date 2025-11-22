package com.github.bonndan.steamy.rails

import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.RailShape

object RailShapeUtil {

    val DEFAULT: RailShape = RailShape.NORTH_SOUTH

    fun createRailShape(from: Direction, to: Direction): RailShape =
        when (from) {
            Direction.NORTH -> when (to) {
                Direction.SOUTH -> RailShape.NORTH_SOUTH
                Direction.EAST -> RailShape.NORTH_EAST
                Direction.WEST -> RailShape.NORTH_WEST
                else -> DEFAULT
            }

            Direction.EAST -> when (to) {
                Direction.WEST -> RailShape.EAST_WEST
                Direction.NORTH -> RailShape.NORTH_EAST
                Direction.SOUTH -> RailShape.SOUTH_EAST
                else -> DEFAULT
            }

            Direction.SOUTH -> when (to) {
                Direction.NORTH -> RailShape.NORTH_SOUTH
                Direction.EAST -> RailShape.SOUTH_EAST
                Direction.WEST -> RailShape.SOUTH_WEST
                else -> DEFAULT
            }

            Direction.WEST -> when (to) {
                Direction.EAST -> RailShape.EAST_WEST
                Direction.NORTH -> RailShape.NORTH_WEST
                Direction.SOUTH -> RailShape.SOUTH_WEST
                else -> DEFAULT
            }

            else -> DEFAULT
        }
}

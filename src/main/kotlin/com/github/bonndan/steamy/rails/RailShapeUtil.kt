package com.github.bonndan.steamy.rails

import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.RailShape

object RailShapeUtil {

    val RAIL_SHAPE_STRAIGHT_FLAT: EnumProperty<RailShape> = EnumProperty.create(
        "rail_shape",
        RailShape::class.java
    ) { it == RailShape.NORTH_SOUTH || it == RailShape.EAST_WEST }

    val DEFAULT: RailShape = RailShape.NORTH_SOUTH

    fun getRailShape(node1: Direction, node2: Direction?): RailShape {
        return when (node1) {
            Direction.NORTH -> when (node2) {
                Direction.SOUTH -> RailShape.NORTH_SOUTH
                Direction.EAST -> RailShape.NORTH_EAST
                Direction.WEST -> RailShape.NORTH_WEST
                else -> DEFAULT
            }

            Direction.EAST -> when (node2) {
                Direction.WEST -> RailShape.EAST_WEST
                Direction.NORTH -> RailShape.NORTH_EAST
                Direction.SOUTH -> RailShape.SOUTH_EAST
                else -> DEFAULT
            }

            Direction.SOUTH -> when (node2) {
                Direction.NORTH -> RailShape.NORTH_SOUTH
                Direction.EAST -> RailShape.SOUTH_EAST
                Direction.WEST -> RailShape.SOUTH_WEST
                else -> DEFAULT
            }

            Direction.WEST -> when (node2) {
                Direction.EAST -> RailShape.EAST_WEST
                Direction.NORTH -> RailShape.NORTH_WEST
                Direction.SOUTH -> RailShape.SOUTH_WEST
                else -> DEFAULT
            }

            else -> DEFAULT
        }
    }
}

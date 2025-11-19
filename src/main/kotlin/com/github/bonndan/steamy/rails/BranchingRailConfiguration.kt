package com.github.bonndan.steamy.rails

import net.minecraft.core.Direction


class BranchingRailConfiguration(
    val rootDirection: Direction,
    val unpoweredDirection: Direction,
    val poweredDirection: Direction
) {

    fun getPossibleDirections(
        inputSide: Direction?,
        powered: Boolean
    ): Set<Direction> {

        if (inputSide == rootDirection) {
            return if (powered) setOf(poweredDirection) else setOf(unpoweredDirection)
        }

        if (inputSide == unpoweredDirection) {
            return if (powered) NO_POSSIBILITIES else setOf(rootDirection)
        }

        if (inputSide == poweredDirection) {
            return if (powered) setOf(rootDirection) else NO_POSSIBILITIES
        }

        return NO_POSSIBILITIES
    }

    companion object {
        val NO_POSSIBILITIES: Set<Direction> = setOf<Direction>()
    }
}
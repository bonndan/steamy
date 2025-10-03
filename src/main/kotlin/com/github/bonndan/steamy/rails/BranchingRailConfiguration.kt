package com.github.bonndan.steamy.rails

import net.minecraft.core.Direction


class BranchingRailConfiguration(
    val rootDirection: Direction,
    val unpoweredDirection: Direction,
    val poweredDirection: Direction
) {

    fun getPossibleDirections(
        inputSide: Direction?,
        automaticSwitching: Boolean,
        powered: Boolean
    ): Set<Direction> {
        if (inputSide == rootDirection) {
            if (automaticSwitching) {
                return setOf(unpoweredDirection, poweredDirection)
            } else {
                return if (powered) setOf(poweredDirection) else setOf(unpoweredDirection)
            }
        }

        if (inputSide == unpoweredDirection) {
            if (automaticSwitching) {
                return setOf(rootDirection)
            } else {
                return if (powered) NO_POSSIBILITIES else setOf(rootDirection)
            }
        }

        if (inputSide == poweredDirection) {
            if (automaticSwitching) {
                return setOf(rootDirection)
            } else {
                return if (powered) setOf(rootDirection) else NO_POSSIBILITIES
            }
        }

        return NO_POSSIBILITIES
    }

    companion object {
        val NO_POSSIBILITIES: Set<Direction> = setOf<Direction>()
    }
}
package com.github.bonndan.steamy.rails

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape

interface MultiShapeRail {

    /**
     * Set the automatic rail state of this rail
     * @param state current blockstate of the rail
     * @return if state was set automatically (or in the case of manual rail,
     * if the state conforms to the inputs already)
     */
    fun setRailState(state: BlockState, world: Level, pos: BlockPos, `in`: Direction, out: Direction): Boolean

    fun getPossibleOutputDirections(state: BlockState, inputSide: Direction): Set<Direction>

    fun getPriorityDirectionsToCheck(state: BlockState, entrance: Direction): Set<Direction>

    /**
     * @param direction Direction of travel for the train
     */
    fun getVanillaRailShapeFromDirection(
        state: BlockState,
        pos: BlockPos,
        level: Level,
        direction: Direction
    ): RailShape

}

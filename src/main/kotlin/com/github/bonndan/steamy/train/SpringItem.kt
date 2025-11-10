package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.setup.ModDataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class SpringItem(properties: Properties) : Item(properties) {

    companion object {
        fun getState(stack: ItemStack): State {
            return if (stack.get(ModDataComponents.LINKED_ENTITY.get()) != null) State.WAITING_NEXT else State.READY
        }
    }

    private val springInfo: Component = Component.translatable("item.steamy.spring.description")

    override fun isBarVisible(stack: ItemStack): Boolean {
        return getState(stack) == State.WAITING_NEXT
    }

    override fun getBarWidth(stack: ItemStack): Int {
        return if (getState(stack) == State.WAITING_NEXT) 13 else 0
    }

    override fun getBarColor(stack: ItemStack): Int {
        return if (getState(stack) == State.WAITING_NEXT) 0x00FF00 else 0xFFFFFF // Grün für WAITING_NEXT
    }

    // because 'itemInteractionForEntity' is only for Living entities
    fun onUsedOnEntity(stack: ItemStack, player: Player, world: Level, target: Entity) {

        if (world.isClientSide) {
            return
        }

        val state = getState(stack)
        when (state) {
            State.WAITING_NEXT -> createSpringHelper(stack, player, world, target)
            else -> setDominant(stack, target)
        }
    }

    private fun createSpringHelper(stack: ItemStack, player: Player, world: Level, target: Entity) {

        val dominant = getDominant(world, stack) ?: return

        if (dominant === target) {
            player.displayClientMessage(Component.translatable("item.steamy.spring.notToSelf"), true)
            resetLinked(stack)
            return
        }

        if (dominant !is LinkableCart<*> || target !is LinkableCart<*>) {
            player.displayClientMessage(Component.translatable("item.steamy.spring.notLinkable"), true)
            resetLinked(stack)
            return
        }

        if (dominant.linkEntities(player, target) && !player.isCreative) {
            stack.shrink(1)
        }
        resetLinked(stack)
    }

    private fun setDominant(stack: ItemStack, entity: Entity) {
        stack.set(ModDataComponents.LINKED_ENTITY.get(), entity.id)
    }

    private fun getDominant(world: Level, stack: ItemStack): Entity? {
        val id = stack.get(ModDataComponents.LINKED_ENTITY.get())
        if (id != null) {
            return world.getEntity(id)
        }
        resetLinked(stack)
        return null
    }

    private fun resetLinked(stack: ItemStack) {
        stack.remove(ModDataComponents.LINKED_ENTITY.get())
    }

    override fun use(worldIn: Level, playerIn: Player, handIn: InteractionHand): InteractionResult {
        resetLinked(playerIn.getItemInHand(handIn))
        return super.use(worldIn, playerIn, handIn)
    }


    enum class State {
        WAITING_NEXT,
        READY
    }

}

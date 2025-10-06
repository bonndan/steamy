package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.wagons.entity.LocomotiveEntity
import net.minecraft.world.entity.vehicle.AbstractMinecart
import java.util.*
import java.util.function.Consumer

class Train<T>(private var head: LinkableCart<T>) where T : AbstractMinecart, T : LinkableCart<T> {

    val tug: Optional<LocomotiveEntity> =
        if (head is LocomotiveEntity) Optional.of(head as LocomotiveEntity) else Optional.empty()
    private var tail: LinkableCart<T> = head

    fun asList(): MutableList<LinkableCart<T>> {
        if (this.head.checkNoLoopsDominated()) {
            // just in case - to avoid crashing the world.
            this.head.removeDominated()
            this.head.getFollower().ifPresent(Consumer { obj -> obj.removeDominant() })
            return ArrayList()
        }

        val list: MutableList<LinkableCart<T>> = ArrayList()
        var barge = Optional.of(head)
        while (barge.isPresent) {
            list.add(barge.get())
            barge = getNext(barge.get())
        }
        return list
    }

    fun getNext(entity: LinkableCart<T>): Optional<LinkableCart<T>> {
        return entity.getFollower().map({ t -> t as LinkableCart<T> })
    }

    fun setHead(newHead: LinkableCart<T>) {
        this.head = newHead
    }

    fun setTail(newTail: LinkableCart<T>) {
        this.tail = newTail
    }

    fun getHead(): LinkableCart<T> {
        return this.head
    }

    fun getTail(): LinkableCart<T> {
        return this.tail
    }
}

package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.locomotive.entity.LocomotiveEntity
import com.github.bonndan.steamy.train.LinkableCart
import net.minecraft.world.entity.vehicle.AbstractMinecart
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

class Train<T>(private var head: LinkableCart<T>) where T : AbstractMinecart, T : LinkableCart<T> {

    val tug: Optional<LocomotiveEntity> =
        if (head is LocomotiveEntity) Optional.of(head as LocomotiveEntity) else Optional.empty()
    private var tail: LinkableCart<T> = head

    fun asListOfTugged(): MutableList<LinkableCart<T>> {

        if (this.head.checkNoLoopsDominated()) {
            // just in case - to avoid crashing the world.
            this.head.removeDominated()
            this.head.getFollower().ifPresent(Consumer { obj -> obj.removeDominant() })
            return ArrayList()
        }

        return tug.map(Function { tugEntity ->
            val barges = ArrayList<LinkableCart<T>>()
            var barge = getNext(tugEntity as LinkableCart<T>)
            while (barge.isPresent) {
                barges.add(barge.get())
                barge = getNext(barge.get())
            }
            barges
        }).orElse(ArrayList<LinkableCart<T>>())
    }

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

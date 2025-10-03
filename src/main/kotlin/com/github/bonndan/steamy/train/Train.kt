package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.locomotive.entity.LocomotiveEntity
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

class Train(private var head: AbstractTrainCarEntity) {

    val tug: Optional<LocomotiveEntity> =
        if (head is LocomotiveEntity) Optional.of(head as LocomotiveEntity) else Optional.empty()
    private var tail: AbstractTrainCarEntity = head

    fun asListOfTugged(): MutableList<AbstractTrainCarEntity> {

        if (this.head.checkNoLoopsDominated()) {
            // just in case - to avoid crashing the world.
            this.head.removeDominated()
            this.head.getFollower().ifPresent(Consumer { obj -> obj.removeDominant() })
            return ArrayList()
        }

        return tug.map(Function { tugEntity ->
            val barges = ArrayList<AbstractTrainCarEntity>()
            var barge = getNext(tugEntity)
            while (barge.isPresent) {
                barges.add(barge.get())
                barge = getNext(barge.get())
            }
            barges
        }).orElse(ArrayList<AbstractTrainCarEntity>())
    }

    fun asList(): MutableList<AbstractTrainCarEntity> {
        if (this.head.checkNoLoopsDominated()) {
            // just in case - to avoid crashing the world.
            this.head.removeDominated()
            this.head.getFollower().ifPresent(Consumer { obj -> obj.removeDominant() })
            return ArrayList()
        }

        val list: MutableList<AbstractTrainCarEntity> = ArrayList()
        var barge: Optional<AbstractTrainCarEntity> = Optional.of(head)
        while (barge.isPresent) {
            list.add(barge.get())
            barge = getNext(barge.get())
        }
        return list
    }

    fun getNext(entity: AbstractTrainCarEntity): Optional<AbstractTrainCarEntity> {
        return entity.getFollower().map(Function { t: AbstractTrainCarEntity? -> t as AbstractTrainCarEntity })
    }

    fun setHead(newHead: AbstractTrainCarEntity) {
        this.head = newHead
    }

    fun setTail(newTail: AbstractTrainCarEntity) {
        this.tail = newTail
    }

    fun getHead(): AbstractTrainCarEntity {
        return this.head
    }

    fun getTail(): AbstractTrainCarEntity {
        return this.tail
    }
}

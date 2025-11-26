package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.setup.ModItems
import com.github.bonndan.steamy.train.RailHelper.getRailAt
import com.github.bonndan.steamy.wagons.entity.LocomotiveEntity
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.function.Function
import java.util.stream.Stream

/**
 * Override
 * fun getOnPos(): BlockPos {
 *          return linkingHandler.getOnPos(this as AbstractMinecart)
 *      }
 *
 *  - add data accessors for the concrete subclass, for example:
 *    val DOMINANT_ID: EntityDataAccessor<Int> = defineId<Int>(XXX::class.java, INT)
 *    val DOMINATED_ID: EntityDataAccessor<Int> = defineId<Int>(XXX::class.java, INT)
 *
 *    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
 *         super.defineSynchedData(builder)
 *         builder.define(xxx, 0f)
 *         builder.define(DOMINANT_ID, -1)
 *         builder.define(DOMINATED_ID, -1)
 *     }
 */
interface LinkableCart<T> where T : AbstractMinecart, T : LinkableCart<T> {

    val linkingHandler: LinkingHandler<T>

    fun getDominantIdAccessor(): EntityDataAccessor<Int>
    fun getDominatedIdAccessor(): EntityDataAccessor<Int>

    fun getTrain(): Train<T> {
        return linkingHandler.train
    }

    fun getLeader(): Optional<LinkableCart<T>> {
        return linkingHandler.leader
    }

    fun setLeader(leader: Optional<LinkableCart<T>>) {
        linkingHandler.leader = leader
    }

    fun getFollower(): Optional<LinkableCart<T>> {
        return linkingHandler.follower
    }

    fun setDominated(entity: LinkableCart<T>) {
        linkingHandler.follower = Optional.of(entity)
    }


    fun setDominant(entity: LinkableCart<T>) {
        linkingHandler.train = entity.getTrain()
        linkingHandler.leader = Optional.of(entity)
    }

    fun removeDominated() {
        if (!(this as AbstractMinecart).isAlive) {
            return
        }
        linkingHandler.follower = Optional.empty()
        linkingHandler.train.setTail(this)
    }

    fun removeDominant() {
        if (!(this as AbstractMinecart).isAlive) {
            return
        }
        setLeader(Optional.empty())
        linkingHandler.train = Train(this)
    }

    fun checkNoLoopsDominated(): Boolean {
        return checkNoLoopsHelper(this, (Function { obj -> obj.getFollower() }), HashSet())
    }

    fun checkNoLoopsDominant(): Boolean {
        return checkNoLoopsHelper(
            this,
            (Function { obj -> obj.getLeader() }),
            HashSet<LinkableCart<T>>()
        )
    }

    private fun checkNoLoopsHelper(
        entity: LinkableCart<T>,
        next: Function<LinkableCart<T>, Optional<LinkableCart<T>>>,
        set: MutableSet<LinkableCart<T>>
    ): Boolean {
        if (set.contains(entity)) {
            return true
        }
        set.add(entity)
        val nextEntity = next.apply(entity)
        return nextEntity.map(Function { e -> this.checkNoLoopsHelper(e, next, set) }).orElse(false)
    }

    fun handleShearsCut(): Boolean {
        if (!(this as AbstractMinecart).level().isClientSide && linkingHandler.leader.isPresent) {
            spawnChain()
        }
        linkingHandler.leader.ifPresent { it.removeDominated() }
        removeDominant()
        return true
    }

    fun spawnChain() {
        val stack = ItemStack(ModItems.SPRING.get())
        val minecart = this as AbstractMinecart
        minecart.spawnAtLocation(minecart.level() as ServerLevel, stack)
    }

    fun linkEntities(player: Player, target: LinkableCart<*>): Boolean {

        target as LinkableCart<T>
        val train1 = target.getTrain()
        val train2 = this.getTrain()

        if (train1.tug.isPresent && train2.tug.isPresent) {
            player.displayClientMessage(Component.translatable("item.steamy.spring.noTwoLoco"), true)
            return false
        }

        if (train2 == train1) {
            player.displayClientMessage(Component.translatable("item.steamy.spring.noLoops"), true)
            return false
        }

        tryFindAndPrepareClosePair(train1, train2).fold(
            {
                createLinks(it.first, it.second)
                return true
            },
            {
                player.displayClientMessage(Component.translatable(it.message!!), true)
                return false
            }
        )
    }

    private fun createLinks(dominant: LinkableCart<T>, dominated: LinkableCart<T>) {
        dominated.setDominant(dominant)
        dominant.setDominated(dominated)
    }

    private fun tryFindAndPrepareClosePair(
        train1: Train<T>,
        train2: Train<T>
    ): Result<Pair<LinkableCart<T>, LinkableCart<T>>> {

        val closest = findClosestPair(train1, train2)
        if (closest.isFailure) {
            return closest
        }

        val closestPair = closest.getOrThrow()
        if (closestPair.first == train1.getHead() && closestPair.second == train2.getHead()) {
            // if trying to attach to head loco then loco is solo
            if (train1.tug.isPresent) {
                return closest
            } else {
                invertTrain(train2)
                return Result.success(swap(closestPair))
            }
        } else if (closestPair.first == train1.getHead() && closestPair.second == train2.getTail()) {
            return Result.success(caseTailHead(train2, train1, swap(closestPair)))
        } else if (closestPair.first == train1.getTail() && closestPair.second == train2.getHead()) {
            return Result.success(caseTailHead(train1, train2, closestPair))
        } else if (closestPair.first == train1.getTail() && closestPair.second == train2.getTail()) {
            if (train2.tug.isPresent) {
                invertTrain(train1)
                return Result.success(swap(closestPair))
            } else {
                invertTrain(train2)
                return Result.success(closestPair)
            }
        }

        return Result.failure(Exception("Unreachable code reached"))
    }


    private fun findClosestPair(
        train1: Train<T>,
        train2: Train<T>
    ): Result<Pair<LinkableCart<T>, LinkableCart<T>>> {

        var mindistance = Int.MAX_VALUE
        var pair: Pair<LinkableCart<T>, LinkableCart<T>>? = null
        val pairs = listOf(
            Pair(train1.getHead(), train2.getTail()),
            Pair(train1.getTail(), train2.getHead()),
            Pair(train1.getTail(), train2.getTail()),
            Pair(train1.getHead(), train2.getHead())
        )
        for (combination in pairs) {
            val distance = calculateDistanceOnRails(
                combination.first as AbstractMinecart,
                combination.second as AbstractMinecart
            )
            if (distance.isPresent && distance.get() < mindistance) {
                mindistance = distance.get()
                pair = combination
            }
        }

        if (pair == null) {
            return Result.failure(Exception("No close pairs found"))
        }

        return if (!isNonLocoOrNotFollowed(pair.first)) {
            Result.failure(Exception("The first end is a loco with followers"))
        } else if (!isNonLocoOrNotFollowed(pair.second)) {
            Result.failure(Exception("The second end is a loco with followers"))
        } else {
            Result.success(pair)
        }
    }

    private fun isNonLocoOrNotFollowed(first: LinkableCart<T>): Boolean {
        return first !is LocomotiveEntity || first.getFollower().isEmpty
    }

    private fun calculateDistanceOnRails(car1: AbstractMinecart, car2: AbstractMinecart): Optional<Int> {

        return RailHelper.traverseBi(
            car1,
            car1.onPos.above(),
            { direction, blockPos ->
                getRailAt(car2.onPos.above(), car2.level())
                    .map({ rp -> rp.equals(blockPos) })
                    .orElse(false)
            },
            5,
        ).map({ obj -> obj.second })
    }

    private fun caseTailHead(
        trainTail: Train<T>,
        trainHead: Train<T>,
        targetPair: Pair<LinkableCart<T>, LinkableCart<T>>
    ): Pair<LinkableCart<T>, LinkableCart<T>> {
        if (trainHead.tug.isPresent) {
            invertTrain(trainHead)
            invertTrain(trainTail)
            return Pair(targetPair.second, targetPair.first)
        } else {
            return targetPair
        }
    }

    private fun invertTrain(train: Train<T>) {
        val head = train.getHead()
        val tail = train.getTail()
        train.asList().forEach { obj -> obj.invertDoms() }
        train.setHead(tail)
        train.setTail(head)
    }

    private fun invertDoms() {
        val temp = linkingHandler.leader
        linkingHandler.leader = linkingHandler.follower
        linkingHandler.follower = temp
    }

    fun <U> applyWithDominant(function: Function<LinkableCart<T>, U?>): Stream<U?> {
        val ofThis = Stream.of<U?>(function.apply(this))

        return if (checkNoLoopsDominant()) ofThis else this.getLeader().map<Stream<U?>?>(Function { dom ->
            Stream.concat<U?>(
                ofThis,
                dom.applyWithDominant<U?>(function)
            )
        }
        ).orElse(ofThis)
    }

    fun <U> applyWithDominated(function: Function<LinkableCart<T>, U?>): Stream<U?> {
        val ofThis = Stream.of<U?>(function.apply(this))

        return if (checkNoLoopsDominated()) ofThis else this.getFollower().map<Stream<U?>?>(Function { dom ->
            Stream.concat<U?>(
                ofThis,
                dom.applyWithDominated<U?>(function)
            )
        }
        ).orElse(ofThis)
    }


    private fun swap(pair: Pair<LinkableCart<T>, LinkableCart<T>>): Pair<LinkableCart<T>, LinkableCart<T>> {
        return Pair(pair.second, pair.first)
    }

}

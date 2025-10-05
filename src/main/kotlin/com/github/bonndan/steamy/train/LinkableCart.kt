package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.locomotive.entity.LocomotiveEntity
import com.github.bonndan.steamy.setup.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.vehicle.AbstractMinecart.exits
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Stream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Override
 * fun getOnPos(): BlockPos {
 *          return linkingHandler.getOnPos(this as AbstractMinecart)
 *      }
 */
interface LinkableCart<T> where T : AbstractMinecart, T : LinkableCart<T> {

    val linkingHandler: LinkingHandler<T>


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

    fun linkEntities(player: Player, target: Entity): Boolean {
        if (target is LinkableCart<*>) {
            target as LinkableCart<T>
            val train1 = target.getTrain()
            val train2 = this.getTrain()
            if (train2.tug.isPresent && train1.tug.isPresent) {
                player.displayClientMessage(Component.translatable("item.steamy.spring.noTwoLoco"), true)
                return false
            } else if (train2.equals(train1)) {
                player.displayClientMessage(Component.translatable("item.steamy.spring.noLoops"), true)
                return false
            } else {
                tryFindAndPrepareClosePair(train1, train2)
                    .ifPresentOrElse(Consumer { pair ->
                        createLinks(pair.first, pair.second)
                    }, Runnable {
                        player.displayClientMessage(
                            Component.translatable("item.steamy.spring.tooFar"), true
                        )
                    })
            }

            return true
        } else {
            player.displayClientMessage(Component.translatable("item.steamy.spring.badTypes"), true)
            return false
        }
    }

    private fun createLinks(dominant: LinkableCart<T>, dominated: LinkableCart<T>) {
        dominated.setDominant(dominant)
        dominant.setDominated(dominated)
    }

    private fun tryFindAndPrepareClosePair(
        train1: Train<T>,
        train2: Train<T>
    ): Optional<Pair<LinkableCart<T>, LinkableCart<T>>> {
        return findClosestPair(train1, train2)
            .flatMap(Function { targetPair ->
                if (targetPair.first == train1.getHead() && targetPair.second == train2.getHead()) {
                    // if trying to attach to head loco then loco is solo
                    if (train1.tug.isPresent) {
                        Optional.of(targetPair)
                    } else {
                        invertTrain(train2)
                        Optional.of(swap(targetPair))
                    }
                } else if (targetPair.first == train1.getHead() && targetPair.second == train2.getTail()) {
                    Optional.of(caseTailHead(train2, train1, swap(targetPair)))
                } else if (targetPair.first == train1.getTail() && targetPair.second == train2.getHead()) {
                    Optional.of(caseTailHead(train1, train2, targetPair))
                } else if (targetPair.first == train1.getTail() && targetPair.second == train2.getTail()) {
                    if (train2.tug.isPresent) {
                        invertTrain(train1)
                        Optional.of(swap(targetPair))
                    } else {
                        invertTrain(train2)
                        Optional.of(targetPair)
                    }
                }
                Optional.empty()
            })
    }


    private fun findClosestPair(
        train1: Train<T>,
        train2: Train<T>
    ): Optional<Pair<LinkableCart<T>, LinkableCart<T>>> {

        var mindistance = Int.MAX_VALUE
        var curr: Optional<Pair<LinkableCart<T>, LinkableCart<T>>> = Optional.empty()
        val pairs = listOf(
            Pair(train1.getHead(), train2.getTail()),
            Pair(train1.getTail(), train2.getHead()),
            Pair(train1.getTail(), train2.getTail()),
            Pair(train1.getHead(), train2.getHead())
        )
        for (pair in pairs) {
            val d = distHelper(pair.first, pair.second)
            if (d.isPresent && d.get() < mindistance) {
                mindistance = d.get()
                curr = Optional.of(pair)
            }
        }

        return curr.filter(Predicate { pair ->
            (pair.first !is LocomotiveEntity || pair.first.getFollower().isEmpty)
                    && (pair.second !is LocomotiveEntity || pair.second.getFollower().isEmpty)
        })
    }

    private fun distHelper(car1: LinkableCart<T>, car2: LinkableCart<T>): Optional<Int> {

        car2 as AbstractMinecart
        return RailHelper.traverseBi(
            car1 as AbstractMinecart,
            car1.onPos.above(),
            { l, p ->
                RailHelper.getRail(car2.onPos.above(), car2.level())
                    .map({ rp -> rp.equals(p) }).orElse(false)
            }, 5, car1
        ).map({ obj -> obj.second })
    }

    private fun caseTailHead(
        trainTail: Train<T>,
        trainHead: Train<T>,
        targetPair: Pair<LinkableCart<*>, LinkableCart<*>>
    ): Pair<LinkableCart<*>, LinkableCart<*>> {
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

    fun <U> applyWithAll(function: Function<LinkableCart<T>, U?>): Stream<U?> {
        return getTrain().getHead().applyWithDominated<U?>(function)
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

    /**
     * This method returns the specific position on the track at
     * pOffset blocks from the current position. This overridden
     * method takes into account of the minecart's yRot, which
     * the vanilla code does not (leading to lots of flipping)
     *
     * Possible workaround for https://bugs.mojang.com/browse/MC/issues/MC-9551
     */
    fun getPosOffs(pX: Double, pY: Double, pZ: Double, pOffset: Double): Vec3? {

        val entity = this as AbstractMinecart
        var pX = pX
        var pY = pY
        var pZ = pZ
        val i: Int = Mth.floor(pX)
        var j: Int = Mth.floor(pY)
        val k: Int = Mth.floor(pZ)
        if (entity.level().getBlockState(BlockPos(i, j - 1, k)).`is`(BlockTags.RAILS)) {
            --j
        }

        val blockstate: BlockState = entity.level().getBlockState(BlockPos(i, j, k))
        if (BaseRailBlock.isRail(blockstate)) {
            val railshape: RailShape = (blockstate.block as BaseRailBlock).getRailDirection(
                blockstate,
                entity.level(),
                BlockPos(i, j, k),
                entity
            )
            pY = j.toDouble()
            if (railshape.isSlope) {
                pY = (j + 1).toDouble()
            }

            val pair = RailHelper.EXITS[railshape]!!
            var exit1: Vec3i = pair.first
            var exit2: Vec3i = pair.second

            // check if need to swap end points to make calculation correct
            val yawX = -sin(Math.toRadians(entity.yRot.toDouble()))
            val yawZ = cos(Math.toRadians(entity.yRot.toDouble()))
            if (Vec3(yawX, 0.0, yawZ).dot(
                    Vec3(
                        (exit2.x - exit1.x).toDouble(),
                        (exit2.y - exit1.y).toDouble(),
                        (exit2.z - exit1.z).toDouble()
                    )
                ) <= 0
            ) {
                val temp: Vec3i = exit1
                exit1 = exit2
                exit2 = temp
            }

            // get direction from e1 to e2
            var xDiff = (exit2.x - exit1.x).toDouble()
            var zDiff = (exit2.z - exit1.z).toDouble()
            // normalize x and z diff
            val dist = sqrt(xDiff * xDiff + zDiff * zDiff)
            xDiff /= dist
            zDiff /= dist
            pX += xDiff * pOffset
            pZ += zDiff * pOffset
            if (exit1.y != 0 && Mth.floor(pX) - i == exit1.x && Mth.floor(pZ) - k == exit1.z) {
                pY += exit1.y.toDouble()
            } else if (exit2.y != 0 && Mth.floor(pX) - i == exit2.x && Mth.floor(pZ) - k == exit2.z) {
                pY += exit2.y.toDouble()
            }

            return this.getPos(pX, pY, pZ)
        } else {
            return null
        }
    }


    /**
     * old vanilla method to get position on track
     */
    fun getPos(pX: Double, pY: Double, pZ: Double): Vec3? {

        val entity = this as AbstractMinecart
        var pX = pX
        var pY = pY
        var pZ = pZ
        val i = Mth.floor(pX)
        var j = Mth.floor(pY)
        val k = Mth.floor(pZ)
        if (entity.level().getBlockState(BlockPos(i, j - 1, k)).`is`(BlockTags.RAILS)) {
            --j
        }

        val blockstate = entity.level().getBlockState(BlockPos(i, j, k))
        if (BaseRailBlock.isRail(blockstate)) {
            val railshape = (blockstate.block as BaseRailBlock).getRailDirection(
                blockstate,
                entity.level(),
                BlockPos(i, j, k),
                entity
            )
            val pair = exits(railshape)
            val vec3i = pair.getFirst()
            val vec3i1 = pair.getSecond()
            val d0 = i.toDouble() + 0.5 + vec3i.x.toDouble() * 0.5
            val d1 = j.toDouble() + 0.0625 + vec3i.y.toDouble() * 0.5
            val d2 = k.toDouble() + 0.5 + vec3i.z.toDouble() * 0.5
            val d3 = i.toDouble() + 0.5 + vec3i1.x.toDouble() * 0.5
            val d4 = j.toDouble() + 0.0625 + vec3i1.y.toDouble() * 0.5
            val d5 = k.toDouble() + 0.5 + vec3i1.z.toDouble() * 0.5
            val d6 = d3 - d0
            val d7 = (d4 - d1) * 2.0
            val d8 = d5 - d2
            val d9: Double
            if (d6 == 0.0) {
                d9 = pZ - k.toDouble()
            } else if (d8 == 0.0) {
                d9 = pX - i.toDouble()
            } else {
                val d10 = pX - d0
                val d11 = pZ - d2
                d9 = (d10 * d6 + d11 * d8) * 2.0
            }

            pX = d0 + d6 * d9
            pY = d1 + d7 * d9
            pZ = d2 + d8 * d9
            if (d7 < 0.0) {
                ++pY
            } else if (d7 > 0.0) {
                pY += 0.5
            }

            return Vec3(pX, pY, pZ)
        } else {
            return null
        }
    }


    companion object {
        //TODO AbstractMinecart might not work
        val DOMINANT_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId<Int>(AbstractMinecart::class.java, EntityDataSerializers.INT)
        val DOMINATED_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId<Int>(AbstractMinecart::class.java, EntityDataSerializers.INT)

        private fun swap(pair: Pair<LinkableCart<*>, LinkableCart<*>>): Pair<LinkableCart<*>, LinkableCart<*>> {
            return Pair(pair.second, pair.first)
        }


    }
}

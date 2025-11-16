package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.setup.ModItems
import com.github.bonndan.steamy.wagons.entity.LocomotiveEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
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
import java.util.stream.Stream
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
            val d = distHelper(combination.first as AbstractMinecart, combination.second as AbstractMinecart)
            if (d.isPresent && d.get() < mindistance) {
                mindistance = d.get()
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

    private fun distHelper(car1: AbstractMinecart, car2: AbstractMinecart): Optional<Int> {

        return RailHelper.traverseBi(
            car1,
            car1.onPos.above(),
            { direction, blockPos ->
                RailHelper.getRail(car2.onPos.above(), car2.level()).map({ rp -> rp.equals(blockPos) }).orElse(false)
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
     * Compare this with OldMinecartBehavior#getPosOffs, which could maybe be overridden be overwriting the getBehavior
     * method in AbstractMinecart
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

    private fun swap(pair: Pair<LinkableCart<T>, LinkableCart<T>>): Pair<LinkableCart<T>, LinkableCart<T>> {
        return Pair(pair.second, pair.first)
    }

    /**
     * This was the getPosOffs method in LinkableCart
     */
    fun calcTrackDirectionBasedValues(partialTicks: Float): TrackDirectionValues? {

        val linkable = this
        val car = this as AbstractMinecart
        val pos: Vec3 = car.getPosition(partialTicks) ?: return null

        val dx = Mth.lerp(partialTicks.toDouble(), car.xo, car.x)
        val dy = Mth.lerp(partialTicks.toDouble(), car.yo, car.y)
        val dz = Mth.lerp(partialTicks.toDouble(), car.zo, car.z)
        val forwardDir = linkable.getPosOffs(dx, dy, dz, 0.3) ?: pos
        val backDir = linkable.getPosOffs(dx, dy, dz, -0.3) ?: pos



        val centre = Vec3(pos.x, (forwardDir.y + backDir.y) / 2.0, pos.z)
        val offset = centre.subtract(dx, dy, dz)


        var trackDirection = forwardDir.subtract(backDir)
        var pitch = Mth.lerp(partialTicks, car.xRotO, car.xRot)
        var yRot : Float = car.yRot
        if (trackDirection.length() != 0.0) {
            trackDirection = trackDirection.normalize()
            yRot = (atan2(-trackDirection.z, -trackDirection.x) * 180.0 / Math.PI).toFloat()
            pitch = (atan(-trackDirection.y) * 73.0).toFloat()
        }

        val chainCentre = centre.add(0.0, .22, 0.0)


        return TrackDirectionValues(
            pitch =pitch,
            yRot = yRot,
            frontPos = chainCentre.add(trackDirection.scale(.2)),
            backPos = chainCentre.add(trackDirection.scale(-.2)),
            translationOffset = offset
        )
    }

    data class TrackDirectionValues(
        val pitch: Float,
        val yRot: Float,
        val frontPos: Vec3,
        val backPos: Vec3,
        val translationOffset: Vec3
    )
}

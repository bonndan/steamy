package com.github.bonndan.steamy.train

import com.github.bonndan.steamy.locomotive.entity.LocomotiveEntity
import com.github.bonndan.steamy.setup.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.vehicle.MinecartFurnace
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Stream
import kotlin.math.*

abstract class AbstractTrainCarEntity : AbstractMinecart {

    protected val linkingHandler = LinkingHandler(this, DOMINANT_ID, DOMINATED_ID)
    protected lateinit var railHelper: RailHelper

    constructor(entityType: EntityType<*>, level: Level) : super(entityType, level) {
        linkingHandler.train = Train(this)
        railHelper = RailHelper(this)
        resetAttributes()
    }

    constructor(entityType: EntityType<*>, level: Level, x: Double, y: Double, z: Double) : super(
        entityType,
        level,
        x,
        y,
        z
    ) {
        val pos: BlockPos = BlockPos.containing(x, y, z)
        val state: BlockState = level().getBlockState(pos)
        if (state.block is BaseRailBlock) {
            val railshape: RailShape = (state.block as BaseRailBlock).getRailDirection(state, this.level(), pos, this)
            val exit = RailHelper.EXITS[railshape]!!.first
            this.yRot =
                RailHelper.directionFromVelocity(Vec3(exit.x.toDouble(), exit.y.toDouble(), exit.z.toDouble())).toYRot()
        }
        linkingHandler.train = Train(this)
        railHelper = RailHelper(this)
        resetAttributes()
    }


    fun linkEntities(player: Player, target: Entity): Boolean {
        if (target is AbstractTrainCarEntity) {
            val train1: Train = target.getTrain()
            val train2: Train = this.getTrain()
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

    fun setDominated(entity: AbstractTrainCarEntity) {
        linkingHandler.follower = Optional.of(entity)
    }


    fun setDominant(entity: AbstractTrainCarEntity) {
        linkingHandler.train = entity.getTrain()
        linkingHandler.leader = Optional.of(entity)
    }


    fun removeDominated() {
        if (!this.isAlive) {
            return
        }
        linkingHandler.follower = Optional.empty()
        linkingHandler.train!!.setTail(this)
    }

    fun removeDominant() {
        if (!this.isAlive) {
            return
        }
        linkingHandler.leader = Optional.empty()
        this.setTrain(Train(this))
    }

    protected fun getTrain(): Train {
        return linkingHandler.train
    }

    protected fun setTrain(train: Train) {
        linkingHandler.train = train
    }

    fun handleLinkableKill() {
        this.getFollower().ifPresent({ obj -> obj.removeDominant() })
        this.getLeader().ifPresent({ obj -> obj.removeDominated() })
    }

    fun checkNoLoopsDominated(): Boolean {
        return checkNoLoopsHelper(this, (Function { obj -> obj.getFollower() }), HashSet())
    }

    fun checkNoLoopsDominant(): Boolean {
        return checkNoLoopsHelper(
            this,
            (Function { obj -> obj.getLeader() }),
            HashSet<AbstractTrainCarEntity>()
        )
    }

    fun checkNoLoopsHelper(
        entity: AbstractTrainCarEntity,
        next: Function<AbstractTrainCarEntity, Optional<AbstractTrainCarEntity>>,
        set: MutableSet<AbstractTrainCarEntity>
    ): Boolean {
        if (set.contains(entity)) {
            return true
        }
        set.add(entity)
        val nextEntity = next.apply(entity)
        return nextEntity.map(Function { e -> this.checkNoLoopsHelper(e, next, set) }).orElse(false)
    }

    fun <U> applyWithAll(function: Function<AbstractTrainCarEntity, U?>): Stream<U?> {
        return this.getTrain().getHead().applyWithDominated<U?>(function)
    }

    fun <U> applyWithDominant(function: Function<AbstractTrainCarEntity, U?>): Stream<U?> {
        val ofThis = Stream.of<U?>(function.apply(this))

        return if (checkNoLoopsDominant()) ofThis else this.getLeader().map<Stream<U?>?>(Function { dom ->
            Stream.concat<U?>(
                ofThis,
                dom!!.applyWithDominant<U?>(function)
            )
        }
        ).orElse(ofThis)
    }

    fun <U> applyWithDominated(function: Function<AbstractTrainCarEntity, U?>): Stream<U?> {
        val ofThis = Stream.of<U?>(function.apply(this))

        return if (checkNoLoopsDominated()) ofThis else this.getFollower().map<Stream<U?>?>(Function { dom ->
            Stream.concat<U?>(
                ofThis,
                dom.applyWithDominated<U?>(function)
            )
        }
        ).orElse(ofThis)
    }


    private fun resetAttributes() {
        isCustomNameVisible = true
    }

    protected val railShape: Optional<RailShape>
        get() {
            for (pos in listOf<BlockPos>(this.onPos.above(), this.onPos)) {
                val state: BlockState = level().getBlockState(pos)
                if (state.block is BaseRailBlock) {
                    return Optional.of(railHelper.getShape(pos))
                }
            }
            return Optional.empty()
        }

    protected override fun readAdditionalSaveData(valueInput: ValueInput) {
        super.readAdditionalSaveData(valueInput)
        linkingHandler.readAdditionalSaveData(valueInput)
    }

    protected override fun addAdditionalSaveData(valueOutput: ValueOutput) {
        super.addAdditionalSaveData(valueOutput)
        linkingHandler.addAdditionalSaveData(valueOutput)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DOMINANT_ID, -1)
        builder.define(DOMINATED_ID, -1)
    }

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        linkingHandler.onSyncedDataUpdated(key)
    }


    override fun tick() {
        linkingHandler.tickLoad()
        tickYRot()
        val yrot = this.yRot
        tickVanilla()
        this.yRot = yrot
        if (!level().isClientSide) {
            doChainMath()
        }
    }


    protected fun enforceMaxVelocity(maxSpeed: Double) {
        var vel: Vec3 = this.deltaMovement
        val normal: Vec3 = vel.normalize()
        if (abs(vel.x) > maxSpeed) {
            this.setDeltaMovement(normal.x * maxSpeed, vel.y, vel.z)
            vel = this.deltaMovement
        }
        if (abs(vel.z) > maxSpeed) {
            this.setDeltaMovement(vel.x, vel.y, normal.z * maxSpeed)
        }
    }

    override fun push(pEntity: Entity) {
        if (!this.level().isClientSide) {

            if (!pEntity.noPhysics && !this.noPhysics) {
                // fix carts with passengers falling behind
                if (!this.hasPassenger(pEntity) || this.getLeader().isPresent) {
                    var d0 = pEntity.x - this.x
                    var d1 = pEntity.z - this.z
                    var d2 = d0 * d0 + d1 * d1
                    if (d2 >= 1.0E-4) {
                        d2 = sqrt(d2)
                        d0 /= d2
                        d1 /= d2
                        var d3 = 1.0 / d2
                        if (d3 > 1.0) {
                            d3 = 1.0
                        }

                        d0 *= d3
                        d1 *= d3
                        d0 *= 0.1
                        d1 *= 0.1
                        d0 *= 0.5
                        d1 *= 0.5
                        if (pEntity is AbstractMinecart) {
                            val d4 = pEntity.x - this.x
                            val d5 = pEntity.z - this.z
                            val vec3: Vec3 = (Vec3(d4, 0.0, d5)).normalize()
                            val vec31: Vec3 = (Vec3(
                                Mth.cos(this.yRot * (Math.PI.toFloat() / 180f)).toDouble(),
                                0.0,
                                Mth.sin(this.yRot * (Math.PI.toFloat() / 180f)).toDouble()
                            )).normalize()
                            val d6: Double = abs(vec3.dot(vec31))
                            if (d6 < 0.8) {
                                return
                            }

                            val vec32: Vec3 = this.deltaMovement
                            val vec33: Vec3 = pEntity.deltaMovement
                            if (isPoweredCart(pEntity) && !isPoweredCart(this)) {
                                this.deltaMovement = vec32.multiply(0.2, 1.0, 0.2)
                                this.push(vec33.x - d0, 0.0, vec33.z - d1)
                                pEntity.deltaMovement = vec33.multiply(0.95, 1.0, 0.95)
                            } else if (!isPoweredCart(pEntity) && isPoweredCart(this)) {
                                pEntity.deltaMovement = vec33.multiply(0.2, 1.0, 0.2)
                                pEntity.push(vec32.x + d0, 0.0, vec32.z + d1)
                                this.deltaMovement = vec32.multiply(0.95, 1.0, 0.95)
                            } else {
                                val d7: Double = (vec33.x + vec32.x) / 2.0
                                val d8: Double = (vec33.z + vec32.z) / 2.0
                                this.deltaMovement = vec32.multiply(0.2, 1.0, 0.2)
                                this.push(d7 - d0, 0.0, d8 - d1)
                                pEntity.deltaMovement = vec33.multiply(0.2, 1.0, 0.2)
                                pEntity.push(d7 + d0, 0.0, d8 + d1)
                            }
                        } else {
                            this.push(-d0, 0.0, -d1)
                            pEntity.push(d0 / 4.0, 0.0, d1 / 4.0)
                        }
                    }
                }
            }
        }
    }

    private fun isPoweredCart(entity: AbstractMinecart): Boolean {
        return entity is LocomotiveEntity || entity is MinecartFurnace
    }

    // avoid inheriting mixins
    override fun getOnPos(): BlockPos {
        val position: Vec3 = position()
        val i: Int = Mth.floor(position.x)
        val j: Int = Mth.floor(position.y - 0.2)
        val k: Int = Mth.floor(position.z)
        val blockpos = BlockPos(i, j, k)
        if (this.level().isEmptyBlock(blockpos)) {
            val blockpos1: BlockPos = blockpos.below()
            val blockstate: BlockState = this.level().getBlockState(blockpos1)
            if (blockstate.collisionExtendsVertically(this.level(), blockpos1, this)) {
                return blockpos1
            }
        }

        return blockpos
    }

    protected fun tickYRot() {
        this.yRot = computeYaw()
    }

    fun computeYaw(): Float {
        val yrot = this.yRot
        // if the car is part of a train, enforce that direction instead
        val railShape: Optional<RailShape> = this.railShape
        if (linkingHandler!!.follower.isPresent && railShape.isPresent) {
            val r = railHelper.traverseBi(
                this.onPos.above(),
                RailHelper.samePositionPredicate(linkingHandler.follower.get()), 5, this
            )
            if (r.isPresent) {
                val yaw = yawHelper(r.get(), linkingHandler.follower.get())
                val directionOpt = RailHelper.getDirectionToOtherExit(yaw, railShape.get())
                if (directionOpt.isPresent) {
                    val direction: Vec3i = directionOpt.get()
                    return ((Mth.atan2(
                        direction.z.toDouble(),
                        direction.x.toDouble()
                    ) * 180.0 / Math.PI).toFloat() + 90)
                }
            }
        } else if (linkingHandler.leader.isPresent && railShape.isPresent) {
            val r = railHelper.traverseBi(
                this.onPos.above(),
                RailHelper.samePositionPredicate(linkingHandler.leader.get()), 5, this
            )
            if (r.isPresent) {
                val hordir = yawHelper(r.get(), linkingHandler.leader.get())
                val directionOpt = RailHelper.getDirectionToOtherExit(hordir, railShape.get())
                if (directionOpt.isPresent) {
                    val direction: Vec3i = directionOpt.get()
                    return ((Mth.atan2(
                        -direction.z.toDouble(),
                        -direction.x.toDouble()
                    ) * 180.0 / Math.PI).toFloat() + 90)
                }
            }
        } else {
            val d1 = this.xo - this.x
            val d3 = this.zo - this.z
            if (d1 * d1 + d3 * d3 > 0.001) {
                return ((Mth.atan2(d3, d1) * 180.0 / Math.PI).toFloat() + 90)
            }
        }

        return yrot
    }

    private fun yawHelper(r: Pair<Direction, Int>, e: Entity): Direction {
        var hordir: Direction? = null
        if (r.second == 0) {
            val dirvec = Vec3(e.xo - this.xo, 0.0, e.zo - this.zo)
            hordir =
                Direction.getNearest(dirvec.normalize().x.toInt(), 0, dirvec.normalize().z.toInt(), null) // may fail
        }
        // if still null
        if (hordir == null) {
            hordir = r.first
        }
        return hordir
    }


    /**
     * This method returns the specific position on the track at
     * pOffset blocks from the current position. This overridden
     * method takes into account of the minecart's yRot, which
     * the vanilla code does not (leading to lots of flipping)
     */
    fun getPosOffs(pX: Double, pY: Double, pZ: Double, pOffset: Double): Vec3? {
        var pX = pX
        var pY = pY
        var pZ = pZ
        val i: Int = Mth.floor(pX)
        var j: Int = Mth.floor(pY)
        val k: Int = Mth.floor(pZ)
        if (this.level().getBlockState(BlockPos(i, j - 1, k)).`is`(BlockTags.RAILS)) {
            --j
        }

        val blockstate: BlockState = this.level().getBlockState(BlockPos(i, j, k))
        if (BaseRailBlock.isRail(blockstate)) {
            val railshape: RailShape = (blockstate.block as BaseRailBlock).getRailDirection(
                blockstate,
                this.level(),
                BlockPos(i, j, k),
                this
            )
            pY = j.toDouble()
            if (railshape.isSlope) {
                pY = (j + 1).toDouble()
            }

            val pair = RailHelper.EXITS[railshape]!!
            var exit1: Vec3i = pair.first
            var exit2: Vec3i = pair.second

            // check if need to swap end points to make calculation correct
            val yawX = -sin(Math.toRadians(yRot.toDouble()))
            val yawZ = cos(Math.toRadians(yRot.toDouble()))
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
        var pX = pX
        var pY = pY
        var pZ = pZ
        val i = Mth.floor(pX)
        var j = Mth.floor(pY)
        val k = Mth.floor(pZ)
        if (this.level().getBlockState(BlockPos(i, j - 1, k)).`is`(BlockTags.RAILS)) {
            --j
        }

        val blockstate = this.level().getBlockState(BlockPos(i, j, k))
        if (BaseRailBlock.isRail(blockstate)) {
            val railshape = (blockstate.getBlock() as BaseRailBlock).getRailDirection(
                blockstate,
                this.level(),
                BlockPos(i, j, k),
                this
            )
            val pair = exits(railshape)
            val vec3i = pair.getFirst()
            val vec3i1 = pair.getSecond()
            val d0 = i.toDouble() + 0.5 + vec3i.getX().toDouble() * 0.5
            val d1 = j.toDouble() + 0.0625 + vec3i.getY().toDouble() * 0.5
            val d2 = k.toDouble() + 0.5 + vec3i.getZ().toDouble() * 0.5
            val d3 = i.toDouble() + 0.5 + vec3i1.getX().toDouble() * 0.5
            val d4 = j.toDouble() + 0.0625 + vec3i1.getY().toDouble() * 0.5
            val d5 = k.toDouble() + 0.5 + vec3i1.getZ().toDouble() * 0.5
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

    // force render since we delegate rendering to the head of the train
    override fun shouldRender(pX: Double, pY: Double, pZ: Double): Boolean {
        return true
    }

    override fun getMotionDirection(): Direction {
        return Direction.fromYRot((this.yRot).toDouble())
    }

    protected fun tickVanilla() {
        super.tick()
    }

    override fun remove(r: RemovalReason) {
        handleLinkableKill()
        super.remove(r)
    }

    override fun destroy(serverLevel: ServerLevel, item: Item) {

        super.destroy(serverLevel, item)

        this.remove(RemovalReason.KILLED)
        if (serverLevel.gameRules.getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            val stack: ItemStack = this.pickResult!!
            if (this.hasCustomName()) {
                stack.set(DataComponents.ITEM_NAME, this.customName)
            }

            val chains = Stream.of(linkingHandler.leader, linkingHandler.follower)
                .filter { obj -> obj.isPresent }
                .count()
                .toInt()
            this.spawnAtLocation(serverLevel, stack)
            for (j in 0..<chains) {
                spawnChain()
            }
        }
    }


    protected fun prevent180() {
        val dir: Vec3 = Vec3(
            this.direction.stepX.toDouble(),
            this.direction.stepY.toDouble(),
            this.direction.stepZ.toDouble()
        )
        val vel: Vec3 = this.deltaMovement
        val mag: Vec3 = vel.multiply(dir)
        val fixer: Vec3 = Vec3(fixUtil(mag.x), 1.0, fixUtil(mag.z))
        this.deltaMovement = this.deltaMovement.multiply(fixer)
    }

    private fun fixUtil(mag: Double): Double {
        return (if (mag < 0) 0 else 1).toDouble()
    }


    private fun doChainMath() {
        linkingHandler.leader.ifPresent({ parent ->
            val railDirDis =
                railHelper.traverseBi(this.onPos.above(), RailHelper.samePositionPredicate(parent), 5, this)
            // this is a fix to mitigate "bouncing" when trains start moving from a stopped position
            // todo: fix based on "docked" instead.
            val docked =
                this.getTrain().tug.isPresent && this.getTrain().tug.get().deltaMovement.equals(Vec3.ZERO)
            val maxDist = if (docked) 1.0 else 1.2
            val minDist = 1.0

            val distance: Float = railDirDis.map { obj -> obj.second }
                .filter { a -> a > 0 }
                .map { di ->
                    val euclid = this.distanceTo(parent)
                    if (euclid < maxDist) di.toFloat() else euclid
                }
                .orElse(this.distanceTo(parent))

            if (distance <= 6) {
                val euclideanDir: Vec3? = parent.position().subtract(position()).normalize()
                val parentDirection: Vec3 = railDirDis
                    .map({ obj -> obj.first })
                    .map { dir -> dir.unitVec3i }
                    .map({ vec3i -> Vec3.atLowerCornerOf(vec3i) })
                    .orElse(euclideanDir)
                    .normalize()
                val parentVelocity: Vec3 = parent.deltaMovement

                if (distance > maxDist) {
                    if (parentVelocity.length() == 0.0) {
                        deltaMovement = parentDirection.scale(0.05)
                    } else {
                        deltaMovement = parentDirection.scale(parentVelocity.length())
                        if (distance > maxDist + 0.2) {
                            deltaMovement = deltaMovement.scale(distance * 0.8)
                        }
                    }
                } else if (parent.distanceTo(this) < minDist && parent.deltaMovement.length() < 0.01) {
                    this.setPos(floor(x) + 0.5, y, floor(z) + 0.5)
                    deltaMovement = Vec3.ZERO
                } else {
                    deltaMovement = Vec3.ZERO
                }
            } else {
                linkingHandler.leader.ifPresent { it.removeDominated() }
                removeDominant()
            }
        })
    }

    private fun spawnChain() {
        val stack = ItemStack(ModItems.SPRING.get())
        this.spawnAtLocation(this.level() as ServerLevel, stack)
    }

    fun handleShearsCut(): Boolean {

        if (!this.level().isClientSide && linkingHandler.leader.isPresent) {
            spawnChain()
        }
        linkingHandler.leader.ifPresent { it.removeDominated() }
        removeDominant()
        return true
    }

    private fun invertDoms() {
        val temp = linkingHandler.leader
        linkingHandler.leader = linkingHandler.follower
        linkingHandler.follower = temp
    }

    private fun distHelper(car1: AbstractTrainCarEntity, car2: AbstractTrainCarEntity): Optional<Int?> {
        return railHelper.traverseBi(
            car1.onPos.above(),
            { l, p ->
                RailHelper.getRail(car2.onPos.above(), car2.level())
                    .map({ rp -> rp.equals(p) }).orElse(false)
            }, 5, car1
        ).map({ obj -> obj.second })
    }

    private fun findClosestPair(
        train1: Train,
        train2: Train
    ): Optional<Pair<AbstractTrainCarEntity, AbstractTrainCarEntity>> {

        var mindistance = Int.MAX_VALUE
        var curr: Optional<Pair<AbstractTrainCarEntity, AbstractTrainCarEntity>> = Optional.empty()
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

    private fun tryFindAndPrepareClosePair(
        train1: Train,
        train2: Train
    ): Optional<Pair<AbstractTrainCarEntity, AbstractTrainCarEntity>> {
        return findClosestPair(train1, train2)
            .flatMap(Function { targetPair ->
                if (targetPair.first == train1.getHead() && targetPair.second == train2.getHead()) {
                    // if trying to attach to head loco then loco is solo
                    if (train1.tug.isPresent) {
                        Optional.of<Pair<AbstractTrainCarEntity?, AbstractTrainCarEntity?>>(targetPair)
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
                        Optional.of<Pair<AbstractTrainCarEntity?, AbstractTrainCarEntity?>>(targetPair)
                    }
                }
                Optional.empty()
            })
    }

    fun getLeader(): Optional<AbstractTrainCarEntity> {
        return linkingHandler.leader
    }

    fun getFollower(): Optional<AbstractTrainCarEntity> {
        return linkingHandler.follower
    }


    companion object {

        private fun swap(pair: Pair<AbstractTrainCarEntity, AbstractTrainCarEntity>): Pair<AbstractTrainCarEntity, AbstractTrainCarEntity> {
            return Pair(pair.second, pair.first)
        }

        val DOMINANT_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId<Int>(AbstractTrainCarEntity::class.java, EntityDataSerializers.INT)
        val DOMINATED_ID: EntityDataAccessor<Int> =
            SynchedEntityData.defineId<Int>(AbstractTrainCarEntity::class.java, EntityDataSerializers.INT)

        private fun caseTailHead(
            trainTail: Train,
            trainHead: Train,
            targetPair: Pair<AbstractTrainCarEntity, AbstractTrainCarEntity>
        ): Pair<AbstractTrainCarEntity, AbstractTrainCarEntity> {
            if (trainHead.tug.isPresent) {
                invertTrain(trainHead)
                invertTrain(trainTail)
                return Pair(targetPair.second, targetPair.first)
            } else {
                return targetPair
            }
        }

        private fun invertTrain(train: Train) {
            val head = train.getHead()
            val tail = train.getTail()
            train.asList().forEach({ obj: AbstractTrainCarEntity? -> obj!!.invertDoms() })
            train.setHead(tail)
            train.setTail(head)
        }

        private fun createLinks(dominant: AbstractTrainCarEntity, dominated: AbstractTrainCarEntity) {
            dominated.setDominant(dominant)
            dominant.setDominated(dominated)
        }
    }
}

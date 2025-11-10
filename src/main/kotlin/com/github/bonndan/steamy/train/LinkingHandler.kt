package com.github.bonndan.steamy.train

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.Function
import java.util.function.Predicate
import kotlin.math.floor

const val DOMINANT = "dominant"

class LinkingHandler<T>(private val entity: T) where T : AbstractMinecart, T : LinkableCart<T> {

    private var waitForDominated = false
    private var linkData: LinkData? = null

    var level: Level = entity.level()
    var leader: Optional<LinkableCart<T>> = Optional.empty()
    var follower: Optional<LinkableCart<T>> = Optional.empty()
    var train: Train<T> = Train(entity)

    fun initWithEntityAndPosition(level: Level, x: Double, y: Double, z: Double) {

        val pos: BlockPos = BlockPos.containing(x, y, z)
        val state: BlockState = level.getBlockState(pos)
        if (state.block is BaseRailBlock) {
            val railshape: RailShape = (state.block as BaseRailBlock).getRailDirection(state, level, pos, entity)
            val exit = RailHelper.EXITS[railshape]!!.first
            entity.yRot =
                RailHelper.directionFromVelocity(Vec3(exit.x.toDouble(), exit.y.toDouble(), exit.z.toDouble())).toYRot()
        }
    }

    fun tickLoad() {
        if (entity.level().isClientSide) {
            fetchDominantClient()
            fetchDominatedClient()
            return
        }

        if (leader.isEmpty) {
            tryToLoadFromNBT(linkData).ifPresent { e -> e.setDominant(entity) }
            leader.ifPresent { trainCarEntity ->
                trainCarEntity.setDominated(entity)
                linkData = null // done loading
            }
        }
        if (follower.isPresent) {
            waitForDominated = false
        } else if (waitForDominated) {

        }
        entity.getEntityData()
            .set(entity.getDominantIdAccessor(), leader.map { obj -> (obj as AbstractMinecart).id }.orElse(-1))
        entity.getEntityData()
            .set(entity.getDominatedIdAccessor(), follower.map { obj -> (obj as AbstractMinecart).id }.orElse(-1))
    }

    fun readAdditionalSaveData(input: ValueInput) {
        linkData = deserializeLinkInfo(input)
        waitForDominated = input.getBooleanOr("hasChild", false)
    }

    fun addAdditionalSaveData(valueOutput: ValueOutput) {
        val hasChild = follower.isPresent

        if (leader.isPresent) {
            val entity = (leader.get() as AbstractMinecart)
            val entityLinkData = LinkData(entity.uuid.toString(), hasChild, x = entity.x, y = entity.y, z = entity.z)
            serializeLinkInfo(valueOutput, entityLinkData)
        } else if (linkData != null) {
            linkData?.hasChild = hasChild
            serializeLinkInfo(valueOutput, linkData)
        }

    }

    fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        if (entity.level().isClientSide) {
            if (entity.getDominatedIdAccessor() == key || entity.getDominantIdAccessor() == key) {
                fetchDominantClient()
                fetchDominatedClient()
            }
        }
    }

    private fun fetchDominantClient() {
        val potential = entity.level().getEntity(entity.getEntityData().get(entity.getDominantIdAccessor()))
        if (potential is LinkableCart<*>) {
            leader = Optional.of(potential as LinkableCart<T>)
        } else {
            leader = Optional.empty()
        }
    }

    private fun tryToLoadFromNBT(linkData: LinkData?): Optional<LinkableCart<T>> {

        if (linkData?.uuid == null) return Optional.empty()

        try {
            val searchBox = AABB(
                (linkData.x - 2),
                (linkData.y - 2),
                (linkData.z - 2),
                (linkData.x + 2),
                (linkData.y + 2),
                (linkData.z + 2)
            )
            val entities = entity.level().getEntities(
                entity,
                searchBox,
                Predicate { e -> e.getStringUUID() == linkData.uuid })
            return entities.stream().findFirst().map(Function { e -> e as LinkableCart<T> })
        } catch (e: Exception) {
            return Optional.empty()
        }
    }

    private fun fetchDominatedClient() {
        val potential = entity.level().getEntity(entity.getEntityData().get(entity.getDominatedIdAccessor()))
        if (potential is LinkableCart<*>) {
            follower = Optional.of(potential as LinkableCart<T>)
        } else {
            follower = Optional.empty()
        }
    }

    private fun serializeLinkInfo(output: ValueOutput, linkData: LinkData?) {

        val info = linkData
        if (info?.uuid == null) return

        val objectValue = output.child(DOMINANT)

        objectValue.putString("uuid", info.uuid)
        objectValue.putBoolean("hasChild", info.hasChild)
        objectValue.putDouble("x", info.x)
        objectValue.putDouble("y", info.y)
        objectValue.putDouble("z", info.z)
    }

    private fun deserializeLinkInfo(input: ValueInput) =
        LinkData(
            uuid = input.getString("uuid").orElse(null),
            hasChild = input.getBooleanOr("hasChild", false),
            x = input.getDoubleOr("x", 0.0),
            y = input.getDoubleOr("y", 0.0),
            z = input.getDoubleOr("z", 0.0),
        )

    fun handleLinkableKill() {
        this.follower.ifPresent { it.removeDominant() }
        this.leader.ifPresent { it.removeDominated() }
    }


    fun getRailShape(): Optional<RailShape> {
        for (pos in listOf<BlockPos>(entity.onPos.above(), entity.onPos)) {
            val state: BlockState = entity.level().getBlockState(pos)
            val block = state.block
            if (block is BaseRailBlock) {
                return Optional.of(block.getRailDirection(state, entity.level(), pos, entity))
            }
        }
        return Optional.empty()
    }


    // avoid inheriting mixins
    fun getOnPos(cart: AbstractMinecart): BlockPos {
        val position: Vec3 = cart.position()
        val i: Int = Mth.floor(position.x)
        val j: Int = Mth.floor(position.y - 0.2)
        val k: Int = Mth.floor(position.z)
        val blockpos = BlockPos(i, j, k)
        if (cart.level().isEmptyBlock(blockpos)) {
            val blockpos1: BlockPos = blockpos.below()
            val blockstate: BlockState = cart.level().getBlockState(blockpos1)
            if (blockstate.collisionExtendsVertically(cart.level(), blockpos1, cart)) {
                return blockpos1
            }
        }

        return blockpos
    }


    fun yawHelper(r: Pair<Direction, Int>, minecart: AbstractMinecart, entity: Entity): Direction {
        var hordir: Direction? = null
        if (r.second == 0) {
            val dirvec = Vec3(entity.xo - minecart.xo, 0.0, entity.zo - minecart.zo)
            hordir =
                Direction.getNearest(dirvec.normalize().x.toInt(), 0, dirvec.normalize().z.toInt(), null) // may fail
        }
        // if still null
        if (hordir == null) {
            hordir = r.first
        }
        return hordir
    }

    fun computeYaw(): Float {
        val yrot = entity.yRot
        // if the car is part of a train, enforce that direction instead
        val railShape = getRailShape()
        if (follower.isPresent && railShape.isPresent) {
            val pair = RailHelper.traverseBi(
                entity,
                entity.onPos.above(),
                RailHelper.samePositionPredicate(follower.get() as AbstractMinecart),
                5,
            )
            if (pair.isPresent) {
                val yaw =
                    yawHelper(pair.get(), entity as AbstractMinecart, follower.get() as Entity)
                val directionOpt = RailHelper.getDirectionToOtherExit(yaw, railShape.get())
                if (directionOpt.isPresent) {
                    val direction: Vec3i = directionOpt.get()
                    return ((Mth.atan2(
                        direction.z.toDouble(),
                        direction.x.toDouble()
                    ) * 180.0 / Math.PI).toFloat() + 90)
                }
            }
        } else if (leader.isPresent && railShape.isPresent) {
            val r = RailHelper.traverseBi(
                entity,
                entity.onPos.above(),
                RailHelper.samePositionPredicate(leader.get() as AbstractMinecart),
                5,
            )
            if (r.isPresent) {
                val hordir = yawHelper(r.get(), entity, leader.get() as AbstractMinecart)
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
            val d1 = entity.xo - entity.x
            val d3 = entity.zo - entity.z
            if (d1 * d1 + d3 * d3 > 0.001) {
                return ((Mth.atan2(d3, d1) * 180.0 / Math.PI).toFloat() + 90)
            }
        }

        return yrot
    }


    private fun fixUtil(mag: Double): Double {
        return (if (mag < 0) 0 else 1).toDouble()
    }


    fun doChainMath() {
        leader.ifPresent { parent ->

            if (parent !is AbstractMinecart) {
                return@ifPresent
            }

            val railDirDis = RailHelper.traverseBi(
                entity as AbstractMinecart,
                entity.onPos.above(),
                RailHelper.samePositionPredicate(parent),
                5,
            )
            // this is a fix to mitigate "bouncing" when trains start moving from a stopped position
            val docked = train.tug.isPresent && this.train.tug.get().deltaMovement.equals(Vec3.ZERO)
            val maxDist = if (docked) 1.0 else 1.2
            val minDist = 1.0

            val distance: Float = railDirDis.map { obj -> obj.second }
                .filter { a -> a > 0 }
                .map { di ->
                    val euclid = entity.distanceTo(parent)
                    if (euclid < maxDist) di.toFloat() else euclid
                }
                .orElse(entity.distanceTo(parent))

            if (distance <= 6) {
                val euclideanDir: Vec3? = parent.position().subtract(entity.position()).normalize()
                val parentDirection: Vec3 = railDirDis
                    .map({ obj -> obj.first })
                    .map { dir -> dir.unitVec3i }
                    .map({ vec3i -> Vec3.atLowerCornerOf(vec3i) })
                    .orElse(euclideanDir)
                    .normalize()
                val parentVelocity: Vec3 = parent.deltaMovement

                if (distance > maxDist) {
                    if (parentVelocity.length() == 0.0) {
                        entity.deltaMovement = parentDirection.scale(0.05)
                    } else {
                        entity.deltaMovement = parentDirection.scale(parentVelocity.length())
                        if (distance > maxDist + 0.2) {
                            entity.deltaMovement = entity.deltaMovement.scale(distance * 0.8)
                        }
                    }
                } else if (parent.distanceTo(entity) < minDist && parent.deltaMovement.length() < 0.01) {
                    entity.setPos(floor(entity.x) + 0.5, entity.y, floor(entity.z) + 0.5)
                    entity.deltaMovement = Vec3.ZERO
                } else {
                    entity.deltaMovement = Vec3.ZERO
                }
            } else {
                leader.ifPresent { it.removeDominated() }
                entity.removeDominant()
            }
        }
    }

    class LinkData(var uuid: String?, var hasChild: Boolean, var x: Double, var y: Double, var z: Double)
}

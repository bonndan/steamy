package com.github.bonndan.steamy.train

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

const val DOMINANT = "dominant"

class LinkingHandler(
    private val entity: AbstractTrainCarEntity,
    private val dominantID: EntityDataAccessor<Int>,
    private val dominatedID: EntityDataAccessor<Int>
) {

    private var waitForDominated = false

    var leader: Optional<AbstractTrainCarEntity> = Optional.empty()
    var follower: Optional<AbstractTrainCarEntity> = Optional.empty()

    lateinit var train: Train

    private var linkData: LinkData? = null


    fun tickLoad() {
        if (entity.level().isClientSide) {
            fetchDominantClient()
            fetchDominatedClient()
            return
        }

        if (leader.isEmpty ) {
            tryToLoadFromNBT(linkData).ifPresent { entity -> entity.setDominant(entity) }
            leader.ifPresent { trainCarEntity ->
                trainCarEntity.setDominated(entity)
                linkData = null // done loading
            }
        }
        if (follower.isPresent) {
            waitForDominated = false
        } else if (waitForDominated) {

        }
        entity.getEntityData().set(dominantID, leader.map { obj -> obj.id }.orElse(-1))
        entity.getEntityData().set(dominatedID, follower.map { obj -> obj.id }.orElse(-1))
    }


    fun readAdditionalSaveData(input: ValueInput) {
        linkData = deserializeLinkInfo(input)
        waitForDominated = input.getBooleanOr("hasChild", false)
    }

    fun addAdditionalSaveData(valueOutput: ValueOutput) {
        val hasChild = follower.isPresent

        if (leader.isPresent) {
            val entity = leader.get()
            val entityLinkData = LinkData(entity.uuid.toString(), hasChild, x = entity.x, y = entity.y, z = entity.z)
            serializeLinkInfo(valueOutput, entityLinkData)
        } else if (linkData != null) {
            linkData?.hasChild = hasChild
            serializeLinkInfo(valueOutput, linkData)
        }

    }

    fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        if (entity.level().isClientSide) {
            if (dominatedID == key || dominantID == key) {
                fetchDominantClient()
                fetchDominatedClient()
            }
        }
    }

    private fun fetchDominantClient() {
        val potential = entity.level().getEntity(entity.getEntityData().get(dominantID))
        if (potential is AbstractTrainCarEntity) {
            leader = Optional.of(potential)
        } else {
            leader = Optional.empty()
        }
    }

    private fun tryToLoadFromNBT(linkData: LinkData?): Optional<AbstractTrainCarEntity> {

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
            return entities.stream().findFirst().map(Function { e -> e as AbstractTrainCarEntity })
        } catch (e: Exception) {
            return Optional.empty()
        }
    }

    private fun fetchDominatedClient() {
        val potential = entity.level().getEntity(entity.getEntityData().get(dominatedID))
        if (potential is AbstractTrainCarEntity) {
            follower = Optional.of(potential)
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


    companion object {
        fun defineSynchedData(
            entity: Entity,
            dominantID: EntityDataAccessor<Int>,
            dominatedID: EntityDataAccessor<Int>
        ) {
            entity.getEntityData().set(dominantID, -1)
            entity.getEntityData().set(dominatedID, -1)
        }
    }

    class LinkData(var uuid: String?, var hasChild: Boolean, var x: Double, var y: Double, var z: Double)


}

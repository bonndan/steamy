/*
MIT License

Copyright (c) 2018 Xavier "jglrxavpok" Niochaut

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.github.bonndan.steamy.train

import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object SpringPhysicsUtil {
    fun computeTargetYaw(currentYaw: Float, anchorPos: Vec3, otherAnchorPos: Vec3): Float {
        val idealYaw =
            (atan2(otherAnchorPos.x - anchorPos.x, -(otherAnchorPos.z - anchorPos.z)) * (180f / Math.PI)).toFloat()
        var closestDistance = Float.POSITIVE_INFINITY
        var closest = idealYaw
        for (sign in Arrays.asList(-1, 0, 1)) {
            val potentialYaw = idealYaw + sign * 360f
            val distance = abs(potentialYaw - currentYaw)
            if (distance < closestDistance) {
                closestDistance = distance
                closest = potentialYaw
            }
        }
        return closest
    }


    fun adjustSpringedEntities(dominant: AbstractTrainCarEntity, dominated: AbstractTrainCarEntity)  {
        if (dominated.distanceTo(dominant) > 20) {
            dominated.removeDominant()
        }

        val distSq = dominant.distanceToSqr(dominated)
        val maxDstSq: Double = 1.2


        val frontAnchor: Vec3 = dominant.position()
        val backAnchor: Vec3 = dominated.position()
        val dist = sqrt(distSq)
        val dx: Double = (frontAnchor.x - backAnchor.x) / dist
        val dy: Double = (frontAnchor.y - backAnchor.y) / dist
        val dz: Double = (frontAnchor.z - backAnchor.z) / dist
        val alpha = 0.5


        val targetYaw = computeTargetYaw(dominated.yRot, frontAnchor, backAnchor)
        dominated.yRot = ((alpha * dominated.yRot + targetYaw * (1f - alpha)) % 360).toFloat()
        val k =  0.4
        val l0 = maxDstSq
        dominated.setDeltaMovement(k * (dist - l0) * dx, k * (dist - l0) * dy, k * (dist - l0) * dz)
    }
}

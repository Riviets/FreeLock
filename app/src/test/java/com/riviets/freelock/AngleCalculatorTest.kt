package com.riviets.freelock

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class AngleCalculatorTest {

    private fun calculateAngle(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray
    ): Double {
        val vector1 = floatArrayOf(a[0] - b[0], a[1] - b[1])
        val vector2 = floatArrayOf(c[0] - b[0], c[1] - b[1])

        val dotProduct = (vector1[0] * vector2[0] + vector1[1] * vector2[1]).toDouble()
        val magnitude1 = sqrt(vector1[0].pow(2) + vector1[1].pow(2).toDouble())
        val magnitude2 = sqrt(vector2[0].pow(2) + vector2[1].pow(2).toDouble())

        val angleInRadians = acos(dotProduct / (magnitude1 * magnitude2))
        return Math.toDegrees(angleInRadians)
    }

    @Test
    fun testPushupDownPhaseAngle() {
        val shoulder = floatArrayOf(0f, 10f)
        val elbow = floatArrayOf(0f, 0f)
        val wrist = floatArrayOf(10f, 0f)

        val angle = calculateAngle(shoulder, elbow, wrist)
        
        assertTrue(angle < 90.0)
    }

    @Test
    fun testPushupUpPhaseAngle() {
        val shoulder = floatArrayOf(0f, 10f)
        val elbow = floatArrayOf(0f, 5f)
        val wrist = floatArrayOf(0.5f, 0f)

        val angle = calculateAngle(shoulder, elbow, wrist)
        
        assertTrue(angle > 160.0)
    }
}

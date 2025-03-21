package de.kvxd.kmcprotocol.codec.codecs

import de.kvxd.kmcprotocol.codec.ElementCodec
import io.ktor.utils.io.*

/**
 * A simple 3-dimensional vector consisting of integers.
 */

data class Vec3i(val x: Int, val y: Int, val z: Int) {

    companion object {
        const val POSITION_X_SIZE = 38
        const val POSITION_Y_SIZE = 12
        const val POSITION_Z_SIZE = 38
        const val POSITION_WRITE_SHIFT = 0x3FFFFFFL
        const val POSITION_Y_SHIFT = 0xFFFL
    }

    constructor(value: Int) : this(value, value, value)

    operator fun plus(other: Vec3i) = Vec3i(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3i) = Vec3i(x - other.x, y - other.y, z - other.z)
    operator fun times(other: Vec3i) = Vec3i(x * other.x, y * other.y, z * other.z)
    operator fun div(other: Vec3i) = Vec3i(x / other.x, y / other.y, z / other.z)

    operator fun plus(scalar: Int) = Vec3i(x + scalar, y + scalar, z + scalar)
    operator fun minus(scalar: Int) = Vec3i(x - scalar, y - scalar, z - scalar)
    operator fun times(scalar: Int) = Vec3i(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Int) = Vec3i(x / scalar, y / scalar, z / scalar)

    operator fun unaryMinus() = Vec3i(-x, -y, -z)
}

object Vec3iCodec : ElementCodec<Vec3i> {

    override suspend fun encode(channel: ByteWriteChannel, value: Vec3i) {
        val x = (value.x.toLong() and Vec3i.POSITION_WRITE_SHIFT)
        val y = (value.y.toLong() and Vec3i.POSITION_Y_SHIFT)
        val z = (value.z.toLong() and Vec3i.POSITION_WRITE_SHIFT)

        val packed = (x shl Vec3i.POSITION_X_SIZE) or (z shl Vec3i.POSITION_Y_SIZE) or y

        LongCodec.encode(channel, packed)
    }

    override suspend fun decode(channel: ByteReadChannel): Vec3i {
        val value = LongCodec.decode(channel)

        val x = (value shr Vec3i.POSITION_X_SIZE).toInt()
        val y = (value shl 52 shr 52).toInt()
        val z = (value shl 26 shr Vec3i.POSITION_Z_SIZE).toInt()

        return Vec3i(x, y, z)
    }
}
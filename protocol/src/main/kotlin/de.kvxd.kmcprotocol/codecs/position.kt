package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

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

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.position(
    property: KProperty1<T, Vec3i>
) {
    addCodec<Vec3i>(encoder = { packet, channel ->
        val value = property.get(packet)

        val x = (value.x.toLong() and Vec3i.POSITION_WRITE_SHIFT)
        val y = (value.y.toLong() and Vec3i.POSITION_Y_SHIFT)
        val z = (value.z.toLong() and Vec3i.POSITION_WRITE_SHIFT)

        val packed = (x shl Vec3i.POSITION_X_SIZE) or (z shl Vec3i.POSITION_Y_SIZE) or y

        channel.writeLong(packed)
    }, decoder = { channel ->
        val value = channel.readLong()

        val x = (value shr Vec3i.POSITION_X_SIZE).toInt()
        val y = (value shl 52 shr 52).toInt()
        val z = (value shl 26 shr Vec3i.POSITION_Z_SIZE).toInt()

        Vec3i(x, y, z)
    })
}
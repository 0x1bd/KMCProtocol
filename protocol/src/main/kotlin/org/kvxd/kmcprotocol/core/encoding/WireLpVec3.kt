package org.kvxd.kmcprotocol.core.encoding

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private const val MaxQuantizedValue = 32766.0
private const val AbsMinValue = 3.051944088384301e-5
private const val AbsMaxValue = 1.7179869183e10

private fun sanitize(value: Double): Double =
    if (value.isNaN()) 0.0 else max(-AbsMaxValue, min(value, AbsMaxValue))

private fun pack(value: Double): Long = ((value * 0.5 + 0.5) * MaxQuantizedValue).roundToLong()

private fun unpack(packed: Long, shift: Int): Double {
    val quantized = min(floor(packed.toDouble() / Math.pow(2.0, shift.toDouble())) % 0x8000, MaxQuantizedValue)
    return (quantized * 2.0) / MaxQuantizedValue - 1.0
}

internal fun PacketWriter.writeLpVec3(x: Double, y: Double, z: Double) {
    val sx = sanitize(x)
    val sy = sanitize(y)
    val sz = sanitize(z)

    val largest = max(abs(sx), max(abs(sy), abs(sz)))
    if (largest < AbsMinValue) {
        writeByte(0)
        return
    }

    val scale = ceil(largest)
    val needsContinuation = scale > 3
    val markers = if (needsContinuation) ((scale.toLong() % 4) or 4) else scale.toLong()
    val packed = markers +
        pack(sx / scale) * 0x8L +
        pack(sy / scale) * 0x40000L +
        pack(sz / scale) * 0x200000000L

    writeByte((packed % 0x100).toByte())
    writeByte(((packed / 0x100) % 0x100).toByte())
    writeInt((((packed / 0x10000) % 0x100000000L)).toInt())

    if (needsContinuation) writeVarInt((scale / 4).toInt())
}

internal fun PacketReader.readLpVec3(): Triple<Double, Double, Double> {
    val a = readByte().toInt() and 0xff
    if (a == 0) return Triple(0.0, 0.0, 0.0)

    val b = readByte().toInt() and 0xff
    val c = readInt().toLong() and 0xffffffffL
    val packed = c * 65536L + (b.toLong() shl 8) + a.toLong()

    var scale = (a and 3).toLong()
    if (a and 4 == 4) scale = readVarInt().toLong() * 4 + scale

    return Triple(
        unpack(packed, 3) * scale,
        unpack(packed, 18) * scale,
        unpack(packed, 33) * scale,
    )
}

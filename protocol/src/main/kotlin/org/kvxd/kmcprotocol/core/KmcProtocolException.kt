package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.network.Direction
import kotlin.reflect.KClass

/**
 * Base exception for protocol, packet, and connection failures raised by KMCProtocol.
 */
open class KmcProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Indicates that a packet could not be encoded for the current protocol state.
 */
class KmcPacketEncodeException(
    packetClass: KClass<out MinecraftPacket>,
    state: ProtocolState,
    details: String,
    cause: Throwable? = null,
) : KmcProtocolException(
    """
    Failed to encode packet ${packetClass.qualifiedName}.
    State: $state
    Details: $details
    """.trimIndent(),
    cause,
)

/**
 * Indicates that a packet frame or payload could not be decoded.
 */
class KmcPacketDecodeException(
    state: ProtocolState,
    direction: Direction,
    packetId: Int?,
    frameLength: Int?,
    details: String,
    cause: Throwable? = null,
) : KmcProtocolException(
    """
    Failed to decode Minecraft packet.
    State: $state
    Direction: $direction
    Packet id: ${packetId?.let { "0x${it.toString(16)}" } ?: "unknown"}
    Frame length: ${frameLength?.toString() ?: "unknown"}
    Details: $details
    """.trimIndent(),
    cause,
)

/**
 * Indicates that a client or server connection failed while reading, writing, or managing transport state.
 */
class KmcConnectionException(
    operation: String,
    endpoint: String,
    state: ProtocolState,
    direction: Direction,
    details: String,
    cause: Throwable? = null,
) : KmcProtocolException(
    """
    Connection failure while $operation.
    Endpoint: $endpoint
    State: $state
    Direction: $direction
    Details: $details
    """.trimIndent(),
    cause,
)

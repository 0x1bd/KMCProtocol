package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import java.nio.file.Path

internal data class PacketGenerationOptions(
    val minecraftVersion: String,
    val basePackage: String,
    val serverboundPrefix: String,
    val clientboundPrefix: String,
    val outputDirectory: Path,
)

internal data class GenerationReport(
    val packetCount: Int,
    val supportTypeCount: Int,
)

internal data class ProtocolStateSource(
    val sourceName: String,
    val className: String,
    val packageName: String,
)

internal data class DirectionSource(
    val sourceName: String,
    val classPrefix: String,
    val packageName: String,
    val enumEntry: String,
)

internal data class PacketEntry(
    val id: Int,
    val name: String,
    val typeName: String,
)

internal data class PacketModel(
    val state: ProtocolStateSource,
    val direction: DirectionSource,
    val id: Int,
    val sourceName: String,
    val className: String,
    val packageName: String,
    val fields: List<PacketField>,
)

internal data class PacketField(
    val sourceName: String,
    val propertyName: String,
    val typeName: TypeName,
    val numberEncoding: String? = null,
    val protocolType: String? = null,
)

internal data class ResolvedType(
    val typeName: TypeName = ClassName("kotlin", "Unit"),
    val numberEncoding: String? = null,
    val protocolType: String? = null,
    val skip: Boolean = false,
)

internal data class RegistryEntry(
    val stateName: String,
    val directionEntry: String,
    val id: Int,
    val className: ClassName,
)

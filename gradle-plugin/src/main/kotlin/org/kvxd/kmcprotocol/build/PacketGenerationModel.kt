package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.nio.file.Path

internal data class ProtocolVersionSpec(
    val minecraftVersion: String,
    val protocolVersion: Int,
    val protocolResourcePath: String,
    val protocolJson: Path,
) {
    val packageSuffix: String get() = "v${minecraftVersion.sanitizeVersion()}"
    val catalogEntryName: String get() = "V${minecraftVersion.sanitizeVersion().uppercase()}"
}

internal fun String.sanitizeVersion(): String = replace(Regex("[^A-Za-z0-9]"), "_")

internal data class PacketGenerationOptions(
    val minecraftVersion: String,
    val protocolVersion: Int,
    val protocolResourcePath: String,
    val runtimePackage: String,
    val packetPackage: String,
    val registryPackage: String,
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

internal sealed interface FlowRole {
    data class HandshakeIntent(val property: String) : FlowRole
    data class Compression(val property: String) : FlowRole
    data class Transition(val targetState: String) : FlowRole
}

internal data class PacketModel(
    val state: ProtocolStateSource,
    val direction: DirectionSource,
    val id: Int,
    val sourceName: String,
    val className: String,
    val packageName: String,
    val fields: List<PacketField>,
    val nestedTypes: List<TypeSpec> = emptyList(),
    val flowRole: FlowRole? = null,
)

internal data class ArrayLevelInfo(
    val countType: String = "VarInt",
    val fixedCount: Int = -1,
    val countField: String = "",
)

internal data class BinaryInfo(
    val kind: String,
    val countType: String = "VarInt",
    val fixedCount: Int = -1,
)

internal data class ArrayInfo(val levels: List<ArrayLevelInfo>, val elementEncoding: String = "VarInt")

internal data class PacketField(
    val sourceName: String,
    val propertyName: String,
    val typeName: TypeName,
    val numberEncoding: String? = null,
    val protocolType: String? = null,
    val serializer: ClassName? = null,
    val arrayInfo: ArrayInfo? = null,
    val binary: BinaryInfo? = null,
    val option: Boolean = false,
    val flagsEncoding: String? = null,
    val switch: SwitchInfo? = null,
)

internal data class SwitchInfo(
    val compareTo: String,
    val presentKeys: List<String>,
    val voidKeys: List<String>,
    val defaultPresent: Boolean,
)

internal data class ResolvedType(
    val typeName: TypeName = ClassName("kotlin", "Unit"),
    val numberEncoding: String? = null,
    val protocolType: String? = null,
    val skip: Boolean = false,
    val serializer: ClassName? = null,
    val arrayInfo: ArrayInfo? = null,
    val native: Boolean = false,
    val binary: BinaryInfo? = null,
    val option: Boolean = false,
    val flagsEncoding: String? = null,
    val switch: SwitchInfo? = null,
)

internal data class RegistryEntry(
    val stateName: String,
    val directionEntry: String,
    val id: Int,
    val className: ClassName,
)

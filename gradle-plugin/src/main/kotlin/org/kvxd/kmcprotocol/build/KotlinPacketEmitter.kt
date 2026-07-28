package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName

internal class KotlinPacketEmitter(
    private val options: PacketGenerationOptions,
) {
    private val minecraftPacket = ClassName("${options.runtimePackage}.core", "MinecraftPacket")
    private val packetMetadata = ClassName("${options.runtimePackage}.core", "PacketMetadata")
    private val packetRegistrySource = ClassName("${options.runtimePackage}.core", "PacketRegistrySource")
    private val protocolState = ClassName("${options.runtimePackage}.core", "ProtocolState")
    private val protocolNumber = ClassName("${options.runtimePackage}.core.encoding", "ProtocolNumber")
    private val protocolType = ClassName("${options.runtimePackage}.core.encoding", "ProtocolType")
    private val numberEncoding = ClassName("${options.runtimePackage}.core.encoding", "NumberEncoding")
    private val handshakeIntentPacket = ClassName("${options.runtimePackage}.core", "HandshakeIntentPacket")
    private val compressionSetupPacket = ClassName("${options.runtimePackage}.core", "CompressionSetupPacket")
    private val stateTransitionPacket = ClassName("${options.runtimePackage}.core", "StateTransitionPacket")
    private val direction = ClassName("${options.runtimePackage}.network", "Direction")
    private val serialName = ClassName("kotlinx.serialization", "SerialName")
    private val serializable = ClassName("kotlinx.serialization", "Serializable")
    private val uuidSerializer = ClassName("${options.runtimePackage}.core.encoding", "UuidSerializer")
    private val protocolArray = ClassName("${options.runtimePackage}.core.encoding", "ProtocolArray")
    private val protocolBinary = ClassName("${options.runtimePackage}.core.encoding", "ProtocolBinary")
    private val binaryKind = ClassName("${options.runtimePackage}.core.encoding", "BinaryKind")
    private val protocolOption = ClassName("${options.runtimePackage}.core.encoding", "ProtocolOption")
    private val protocolFlags = ClassName("${options.runtimePackage}.core.encoding", "ProtocolFlags")
    private val protocolSwitch = ClassName("${options.runtimePackage}.core.encoding", "ProtocolSwitch")
    private val kClass = ClassName("kotlin.reflect", "KClass")

    fun emitPacket(packet: PacketModel): RegistryEntry {
        val packetType = if (packet.fields.isEmpty()) {
            TypeSpec.objectBuilder(packet.className)
                .addModifiers(KModifier.DATA)
                .addSuperinterface(minecraftPacket)
        } else {
            TypeSpec.classBuilder(packet.className)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    FunSpec.constructorBuilder().apply {
                        packet.fields.forEach { addParameter(it.propertyName, it.typeName) }
                    }.build()
                )
                .addSuperinterface(minecraftPacket)
                .apply { packet.fields.forEach { addProperty(packetProperty(it)) } }
        }

        packet.nestedTypes.forEach { packetType.addType(it) }
        applyFlowRole(packetType, packet.flowRole)

        packetType
            .addKdoc(packetKdoc(packet))
            .addAnnotation(
                AnnotationSpec.builder(packetMetadata)
                    .addMember("0x%L", packet.id.toString(16).padStart(2, '0'))
                    .addMember("%T.%L", direction, packet.direction.enumEntry)
                    .build()
            )
            .addAnnotation(serializable)

        fileSpec(packet.packageName, packet.className)
            .addType(packetType.build())
            .build()
            .writeTo(options.outputDirectory)

        return RegistryEntry(
            packet.state.className,
            packet.direction.enumEntry,
            packet.id,
            ClassName(packet.packageName, packet.className),
        )
    }

    fun emitRegistry(entries: List<RegistryEntry>) {
        val packetClassType = kClass.parameterizedBy(WildcardTypeName.producerOf(minecraftPacket))
        val mapType = ClassName("kotlin.collections", "Map")
        val packetIdMapType = mapType.parameterizedBy(direction, mapType.parameterizedBy(INT, packetClassType))

        entries
            .groupBy { Triple(it.stateName, it.directionEntry, it.id) }
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.let { duplicate ->
                val (state, directionEntry, id) = duplicate.key
                throw IllegalArgumentException(
                    "Duplicate generated packet id 0x${id.toString(16)} for $directionEntry in $state: " +
                        duplicate.value.joinToString { it.className.canonicalName }
                )
            }

        val registry = TypeSpec.objectBuilder("GeneratedPacketRegistry")
            .addSuperinterface(packetRegistrySource)
            .addProperty(
                PropertySpec.builder("minecraftVersion", String::class, KModifier.CONST)
                    .initializer("%S", options.minecraftVersion)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("protocolVersion", INT, KModifier.CONST)
                    .initializer("%L", options.protocolVersion)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("protocolResourcePath", String::class, KModifier.CONST)
                    .initializer("%S", options.protocolResourcePath)
                    .build()
            )

        protocolStates().forEach { state ->
            val stateEntries = entries.filter { it.stateName == state.className }
            val statePropertyName = state.className.replaceFirstChar { it.lowercase() }
            registry.addProperty(
                PropertySpec.builder("${statePropertyName}ById", packetIdMapType)
                    .initializer(registryIdInitializer(stateEntries))
                    .build()
            )
        }

        registry.addFunction(
            FunSpec.builder("packetsById")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("state", protocolState)
                .returns(packetIdMapType)
                .addCode(
                    CodeBlock.builder().apply {
                        add("return when (state) {\n⇥")
                        protocolStates().forEach { state ->
                            val statePropertyName = state.className.replaceFirstChar { it.lowercase() }
                            add("%T.%L -> %LById\n", protocolState, state.className, statePropertyName)
                        }
                        add("⇤}\n")
                    }.build()
                )
                .build()
        )

        fileSpec(options.registryPackage, "GeneratedPacketRegistry")
            .addType(registry.build())
            .build()
            .writeTo(options.outputDirectory)
    }

    private fun packetKdoc(packet: PacketModel): CodeBlock =
        CodeBlock.builder()
            .add("`%L` packet.\n\n", packet.sourceName)
            .add("Id: `0x%L`\n\n", packet.id.toString(16).padStart(2, '0'))
            .add("Direction: %L\n\n", packet.direction.enumEntry)
            .add("State: %L\n\n", packet.state.className)
            .add("Minecraft %L (protocol %L)\n", options.minecraftVersion, options.protocolVersion)
            .build()

    private fun applyFlowRole(builder: TypeSpec.Builder, role: FlowRole?) {
        when (role) {
            null -> Unit
            is FlowRole.HandshakeIntent -> {
                builder.addSuperinterface(handshakeIntentPacket)
                builder.addProperty(
                    PropertySpec.builder("nextProtocolStateId", INT, KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return %L.toInt()", role.property).build())
                        .build()
                )
            }
            is FlowRole.Compression -> {
                builder.addSuperinterface(compressionSetupPacket)
                builder.addProperty(
                    PropertySpec.builder("compressionThreshold", INT, KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return %L.toInt()", role.property).build())
                        .build()
                )
            }
            is FlowRole.Transition -> {
                builder.addSuperinterface(stateTransitionPacket)
                builder.addProperty(
                    PropertySpec.builder("targetProtocolState", protocolState, KModifier.OVERRIDE)
                        .getter(
                            FunSpec.getterBuilder()
                                .addStatement("return %T.%L", protocolState, role.targetState)
                                .build()
                        )
                        .build()
                )
            }
        }
    }

    private fun packetProperty(field: PacketField): PropertySpec =
        PropertySpec.builder(field.propertyName, field.typeName)
            .initializer(field.propertyName)
            .apply {
                if (field.sourceName != field.propertyName) {
                    addAnnotation(AnnotationSpec.builder(serialName).addMember("%S", field.sourceName).build())
                }
                if (field.numberEncoding != null) {
                    addAnnotation(
                        AnnotationSpec.builder(protocolNumber)
                            .addMember("%T.%L", numberEncoding, field.numberEncoding)
                            .build()
                    )
                }
                if (field.protocolType != null) {
                    addAnnotation(AnnotationSpec.builder(protocolType).addMember("%S", field.protocolType).build())
                }
                field.arrayInfo?.let {
                    addAnnotation(protocolArrayAnnotationOf(protocolArray, numberEncoding, it))
                }
                field.binary?.let {
                    addAnnotation(protocolBinaryAnnotationOf(protocolBinary, binaryKind, numberEncoding, it))
                }
                if (field.option) addAnnotation(AnnotationSpec.builder(protocolOption).build())
                field.switch?.let { addAnnotation(protocolSwitchAnnotationOf(protocolSwitch, it)) }
                field.flagsEncoding?.let {
                    addAnnotation(
                        AnnotationSpec.builder(protocolFlags)
                            .addMember("encoding·=·%T.%L", numberEncoding, it)
                            .build()
                    )
                }
                field.serializer?.let {
                    addAnnotation(AnnotationSpec.builder(serializable).addMember("with·=·%T::class", it).build())
                }
            }
            .build()

    private fun registryIdInitializer(entries: List<RegistryEntry>): CodeBlock =
        CodeBlock.builder().apply {
            if (entries.isEmpty()) {
                add("emptyMap()")
                return@apply
            }

            add("mapOf(\n⇥")
            entries.groupBy { it.directionEntry }.entries.forEachIndexed { directionIndex, (directionEntry, group) ->
                if (directionIndex > 0) add(",\n")
                add("%T.%L to mapOf(\n⇥", direction, directionEntry)
                group.sortedBy { it.id }.forEachIndexed { packetIndex, entry ->
                    if (packetIndex > 0) add(",\n")
                    add("0x%L to %T::class", entry.id.toString(16).padStart(2, '0'), entry.className)
                }
                add("\n⇤)")
            }
            add("\n⇤)")
        }.build()

    private fun fileSpec(packageName: String, fileName: String): FileSpec.Builder =
        FileSpec.builder(packageName, fileName)
            .addFileComment("Generated by the kmcprotocol Gradle plugin from PrismarineJS minecraft-data.\n")
            .addFileComment("Minecraft %L (protocol %L). Do not edit.\n", options.minecraftVersion, options.protocolVersion)
            .addAnnotation(
                AnnotationSpec.builder(ClassName("kotlinx.serialization", "UseSerializers"))
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
                    .addMember("%T::class", uuidSerializer)
                    .build()
            )
}

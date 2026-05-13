package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateMinecraftPackets : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protocolDataFile: RegularFileProperty

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val basePackage: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val protocol = Json.parseToJsonElement(protocolDataFile.get().asFile.readText()).jsonObject
        val output = outputDirectory.get().asFile
        project.delete(output)
        output.mkdirs()

        val generator = PacketGenerator(protocol, minecraftVersion.get(), basePackage.get(), output.toPath())
        val count = generator.generate()
        logger.lifecycle("Generated $count packet classes from ${protocolDataFile.get().asFile}")
    }
}

private class PacketGenerator(
    private val protocol: JsonObject,
    private val minecraftVersion: String,
    private val basePackage: String,
    private val outputDirectory: java.nio.file.Path,
) {

    private val minecraftPacket = ClassName("$basePackage.core", "MinecraftPacket")
    private val packetMetadata = ClassName("$basePackage.core", "PacketMetadata")
    private val protocolNumber = ClassName("$basePackage.core.encoding", "ProtocolNumber")
    private val protocolType = ClassName("$basePackage.core.encoding", "ProtocolType")
    private val protocolValue = ClassName("$basePackage.core.encoding", "ProtocolValue")
    private val numberEncoding = ClassName("$basePackage.core.encoding", "NumberEncoding")
    private val direction = ClassName("$basePackage.network", "Direction")
    private val serialName = ClassName("kotlinx.serialization", "SerialName")
    private val serializable = ClassName("kotlinx.serialization", "Serializable")
    private val kClass = ClassName("kotlin.reflect", "KClass")
    private val uShort = ClassName("kotlin", "UShort")

    private val states = listOf(
        ProtocolStateSource("handshaking", "Handshake", "handshake"),
        ProtocolStateSource("status", "Status", "status"),
        ProtocolStateSource("login", "Login", "login"),
        ProtocolStateSource("configuration", "Configuration", "configuration"),
        ProtocolStateSource("play", "Play", "play"),
    )

    private val directions = listOf(
        DirectionSource("toServer", "Serverbound", "serverbound", "Serverbound"),
        DirectionSource("toClient", "Clientbound", "clientbound", "Clientbound"),
    )

    private val reserved = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while",
    )

    private val scalarTypes = mapOf(
        "bool" to ResolvedType(ClassName("kotlin", "Boolean")),
        "i8" to ResolvedType(ClassName("kotlin", "Byte"), "Byte"),
        "u8" to ResolvedType(ClassName("kotlin", "Short"), "UnsignedByte"),
        "i16" to ResolvedType(ClassName("kotlin", "Short"), "Short"),
        "u16" to ResolvedType(INT, "UnsignedShort"),
        "i32" to ResolvedType(INT, "Int"),
        "u32" to ResolvedType(LONG, "UnsignedInt"),
        "varint" to ResolvedType(INT, "VarInt"),
        "optvarint" to ResolvedType(INT, "VarInt"),
        "i64" to ResolvedType(LONG, "Long"),
        "varlong" to ResolvedType(LONG, "VarLong"),
        "f32" to ResolvedType(ClassName("kotlin", "Float")),
        "f64" to ResolvedType(ClassName("kotlin", "Double")),
        "string" to ResolvedType(STRING),
    )

    fun generate(): Int {
        val registryEntries = mutableListOf<RegistryEntry>()

        for (state in states) {
            val stateObject = protocol[state.sourceName]?.jsonObject ?: continue

            for (directionSource in directions) {
                val directionTypes = stateObject[directionSource.sourceName]?.jsonObject
                    ?.get("types")
                    ?.jsonObject
                    ?: continue

                for (packet in extractPacketEntries(directionTypes)) {
                    val packetDefinition = resolvePacketDefinition(directionTypes, packet.typeName)
                    val fields = packetFields(packetDefinition, directionTypes)
                    val className = "${directionSource.classPrefix}${toPascalCase(packet.typeName)}Packet"
                    val packageName = "$basePackage.packets.generated.${state.packageName}.${directionSource.packageName}"

                    writePacketFile(packageName, className, packet.id, directionSource.enumEntry, fields)
                    registryEntries += RegistryEntry(state.className, ClassName(packageName, className))
                }
            }
        }

        writeRegistryFile(registryEntries)
        return registryEntries.size
    }

    private fun extractPacketEntries(directionTypes: JsonObject): List<PacketEntry> {
        val packetContainer = directionTypes["packet"] as? JsonArray ?: return emptyList()
        val fields = packetContainer.getOrNull(1)?.jsonArray ?: return emptyList()
        val nameField = fields.fieldNamed("name")
        val paramsField = fields.fieldNamed("params")
        val idMappings = nameField?.typeOptions()?.get("mappings")?.jsonObject ?: return emptyList()
        val packetTypes = paramsField?.typeOptions()?.get("fields")?.jsonObject ?: JsonObject(emptyMap())

        return idMappings.map { (rawId, packetName) ->
            val name = packetName.jsonPrimitive.content
            PacketEntry(
                id = Integer.decode(rawId),
                name = name,
                typeName = packetTypes[name]?.jsonPrimitive?.content ?: "packet_$name",
            )
        }
    }

    private fun resolvePacketDefinition(directionTypes: JsonObject, typeName: String): JsonElement? =
        directionTypes[typeName] ?: protocol["types"]?.jsonObject?.get(typeName)

    private fun packetFields(packetDefinition: JsonElement?, directionTypes: JsonObject): List<PacketField> {
        val definition = packetDefinition as? JsonArray ?: return emptyList()
        if (definition.getOrNull(0)?.jsonPrimitive?.content != "container") return emptyList()

        val usedNames = mutableSetOf<String>()
        return definition.getOrNull(1)
            ?.jsonArray
            .orEmpty()
            .mapNotNull { fieldElement ->
                val field = fieldElement.jsonObject
                val sourceName = field["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val resolved = resolveType(field["type"] ?: return@mapNotNull null, directionTypes)
                if (resolved.skip) return@mapNotNull null

                PacketField(
                    sourceName = sourceName,
                    propertyName = uniqueName(toPropertyName(sourceName), usedNames),
                    typeName = resolved.typeName,
                    numberEncoding = resolved.numberEncoding,
                    protocolType = resolved.protocolType,
                )
            }
    }

    private fun resolveType(type: JsonElement, aliases: JsonObject, seen: MutableSet<String> = mutableSetOf()): ResolvedType {
        if (type is JsonPrimitive) {
            val typeName = type.content
            if (typeName == "void") return ResolvedType(skip = true)
            scalarTypes[typeName]?.let { return it }

            val alias = aliases[typeName] ?: protocol["types"]?.jsonObject?.get(typeName)
            if (alias != null && seen.add(typeName)) {
                if (alias.jsonPrimitiveOrNull()?.contentOrNull == "native") {
                    return dynamicType(JsonPrimitive(typeName))
                }
                return resolveType(alias, aliases, seen)
            }

            return dynamicType(type)
        }

        val typeArray = type as? JsonArray ?: return dynamicType(type)
        val kind = typeArray.getOrNull(0)?.jsonPrimitive?.content ?: return dynamicType(type)
        val options = typeArray.getOrNull(1) as? JsonObject

        return when (kind) {
            "pstring" -> ResolvedType(STRING)
            "mapper" -> resolveType(options?.get("type") ?: JsonPrimitive("varint"), aliases, seen)
            "bitflags" -> resolveType(options?.get("type") ?: JsonPrimitive("varint"), aliases, seen)
            else -> dynamicType(type)
        }
    }

    private fun dynamicType(type: JsonElement): ResolvedType =
        ResolvedType(protocolValue, protocolType = type.toString())

    private fun writePacketFile(
        packageName: String,
        className: String,
        packetId: Int,
        directionEntry: String,
        fields: List<PacketField>,
    ) {
        val packetType = if (fields.isEmpty()) {
            TypeSpec.objectBuilder(className)
                .addModifiers(KModifier.DATA)
                .addSuperinterface(minecraftPacket)
        } else {
            TypeSpec.classBuilder(className)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    com.squareup.kotlinpoet.FunSpec.constructorBuilder().apply {
                        fields.forEach { field ->
                            addParameter(field.propertyName, field.typeName)
                        }
                    }.build()
                )
                .addSuperinterface(minecraftPacket)
                .apply {
                    fields.forEach { field ->
                        val property = PropertySpec.builder(field.propertyName, field.typeName)
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
                            }
                            .build()
                        addProperty(property)
                    }
                }
        }

        packetType
            .addAnnotation(
                AnnotationSpec.builder(packetMetadata)
                    .addMember("0x%L", packetId.toString(16).padStart(2, '0'))
                    .addMember("%T.%L", direction, directionEntry)
                    .build()
            )
            .addAnnotation(serializable)

        FileSpec.builder(packageName, className)
            .addFileComment("This file is generated by kmcp.\n")
            .addFileComment("Source: PrismarineJS minecraft-data protocol.json ($minecraftVersion).\n")
            .addType(packetType.build())
            .build()
            .writeTo(outputDirectory)
    }

    private fun writeRegistryFile(entries: List<RegistryEntry>) {
        val listType = ClassName("kotlin.collections", "List")
            .parameterizedBy(kClass.parameterizedBy(WildcardTypeName.producerOf(minecraftPacket)))

        val registry = TypeSpec.objectBuilder("GeneratedPacketRegistry")

        states.forEach { state ->
            val stateEntries = entries.filter { it.stateName == state.className }
            val initializer = CodeBlock.builder().apply {
                if (stateEntries.isEmpty()) {
                    add("emptyList()")
                } else {
                    add("listOf(\n⇥")
                    stateEntries.forEachIndexed { index, entry ->
                        if (index > 0) add(",\n")
                        add("%T::class", entry.className)
                    }
                    add("\n⇤)")
                }
            }.build()

            registry.addProperty(
                PropertySpec.builder(state.className.replaceFirstChar { it.lowercase() }, listType)
                    .initializer(initializer)
                    .build()
            )
        }

        FileSpec.builder("$basePackage.generated", "GeneratedPacketRegistry")
            .addFileComment("This file is generated by kmcp.\n")
            .addFileComment("Source: PrismarineJS minecraft-data protocol.json ($minecraftVersion).\n")
            .addType(registry.build())
            .build()
            .writeTo(outputDirectory)
    }

    private fun toPascalCase(value: String): String =
        value.removePrefix("packet_")
            .split(Regex("[^A-Za-z0-9]+|_"))
            .filter(String::isNotEmpty)
            .joinToString("") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun toPropertyName(value: String): String {
        val cleaned = value
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .replace(Regex("^_+$"), "field")
            .replace(Regex("^([0-9])"), "field_$1")
        val identifier = cleaned.ifEmpty { "field" }
        return if (identifier in reserved) "${identifier}Value" else identifier
    }

    private fun uniqueName(base: String, used: MutableSet<String>): String {
        if (used.add(base)) return base

        var index = 2
        while (!used.add("$base$index")) index += 1
        return "$base$index"
    }

}

private data class ProtocolStateSource(
    val sourceName: String,
    val className: String,
    val packageName: String,
)

private data class DirectionSource(
    val sourceName: String,
    val classPrefix: String,
    val packageName: String,
    val enumEntry: String,
)

private data class PacketEntry(
    val id: Int,
    val name: String,
    val typeName: String,
)

private data class PacketField(
    val sourceName: String,
    val propertyName: String,
    val typeName: TypeName,
    val numberEncoding: String? = null,
    val protocolType: String? = null,
)

private data class ResolvedType(
    val typeName: TypeName = ClassName("kotlin", "Unit"),
    val numberEncoding: String? = null,
    val protocolType: String? = null,
    val skip: Boolean = false,
)

private data class RegistryEntry(
    val stateName: String,
    val className: ClassName,
)

private fun List<JsonElement>.fieldNamed(name: String): JsonObject? =
    firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == name }?.jsonObject

private fun JsonObject.typeOptions(): JsonObject? =
    (this["type"] as? JsonArray)?.getOrNull(1)?.jsonObject

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private val JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()

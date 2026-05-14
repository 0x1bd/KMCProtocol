package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ProtocolTypeResolver(
    private val protocol: JsonObject,
    private val options: PacketGenerationOptions,
    private val naming: PacketNaming,
) {
    private val serialName = ClassName("kotlinx.serialization", "SerialName")
    private val serializable = ClassName("kotlinx.serialization", "Serializable")
    private val protocolEnum = ClassName("${options.runtimePackage}.core.encoding", "ProtocolEnum")
    private val protocolEnumValue = ClassName("${options.runtimePackage}.core.encoding", "ProtocolEnumValue")
    private val protocolValue = ClassName("${options.runtimePackage}.core.encoding", "ProtocolValue")
    private val byteArray = ClassName("kotlin", "ByteArray")
    private val nbtTag = ClassName("net.benwoodworth.knbt", "NbtTag")

    private val emittedTypes = mutableMapOf<String, ClassName>()
    private val usedTypeNames = mutableMapOf<String, String>()

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
        "UUID" to ResolvedType(STRING, protocolType = "\"UUID\""),
        "restBuffer" to ResolvedType(byteArray, protocolType = "\"restBuffer\""),
        "anonymousNbt" to ResolvedType(nbtTag, protocolType = "\"anonymousNbt\""),
        "anonOptionalNbt" to ResolvedType(nbtTag.copy(nullable = true), protocolType = "\"anonOptionalNbt\""),
        "entityMetadataLoop" to ResolvedType(byteArray, protocolType = "\"entityMetadataLoop\""),
    )

    val emittedTypeCount: Int
        get() = emittedTypes.size

    fun resolveType(
        type: JsonElement,
        aliases: JsonObject,
        seen: MutableSet<String> = mutableSetOf(),
        hint: String = "Value",
    ): ResolvedType {
        if (type is JsonPrimitive) {
            val typeName = type.content
            if (typeName == "void") return ResolvedType(skip = true)
            scalarTypes[typeName]?.let { return it }

            val alias = aliases[typeName] ?: protocol["types"]?.jsonObject?.get(typeName)
            if (alias != null && seen.add(typeName)) {
                if (alias.jsonPrimitiveOrNull()?.safeContentOrNull == "native") {
                    return scalarTypes[typeName]?.copy(protocolType = JsonPrimitive(typeName).toString())
                        ?: dynamicType(JsonPrimitive(typeName), aliases, hint)
                }
                return resolveType(alias, aliases, seen, hint)
            }

            return dynamicType(type, aliases, hint)
        }

        val typeArray = type as? JsonArray ?: return dynamicType(type, aliases, hint)
        val kind = typeArray.getOrNull(0)?.jsonPrimitive?.content ?: return dynamicType(type, aliases, hint)
        val options = typeArray.getOrNull(1) as? JsonObject

        return when (kind) {
            "pstring" -> ResolvedType(STRING, protocolType = type.toString())
            "mapper" -> mapperType(typeArray, options, hint)
            "bitflags" -> ResolvedType(LIST.parameterizedBy(STRING), protocolType = type.toString())
            "array" -> {
                val element = resolveType(options?.get("type") ?: JsonPrimitive("void"), aliases, mutableSetOf(), "${hint}Entry")
                ResolvedType(LIST.parameterizedBy(element.typeName), protocolType = type.toString())
            }
            "buffer" -> ResolvedType(byteArray, protocolType = type.toString())
            "option" -> {
                val nested = resolveType(typeArray.getOrNull(1) ?: JsonPrimitive("void"), aliases, mutableSetOf(), hint)
                ResolvedType(nested.typeName.copy(nullable = true), protocolType = type.toString())
            }
            "container", "bitfield", "registryEntryHolder", "registryEntryHolderSet" ->
                generatedContainerType(typeArray, aliases, hint)
            else -> dynamicType(type, aliases, hint)
        }
    }

    private fun dynamicType(type: JsonElement, aliases: JsonObject, hint: String): ResolvedType =
        when ((type as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.content) {
            "switch" -> ResolvedType(protocolValue, protocolType = type.toString())
            "topBitSetTerminatedArray" -> {
                val options = type.getOrNull(1) as? JsonObject
                val element = resolveType(options?.get("type") ?: JsonPrimitive("void"), aliases, mutableSetOf(), "${hint}Entry")
                ResolvedType(LIST.parameterizedBy(element.typeName), protocolType = type.toString())
            }
            else -> ResolvedType(protocolValue, protocolType = type.toString())
        }

    private fun mapperType(type: JsonArray, typeOptions: JsonObject?, hint: String): ResolvedType {
        val mappings = typeOptions?.get("mappings")?.jsonObject ?: return ResolvedType(INT, "VarInt", type.toString())
        val baseClassName = naming.generatedTypeName(hint)
        val key = "mapper:$baseClassName:${mappings.entries.joinToString(",") { "${it.key}:${it.value}" }}"
        emittedTypes[key]?.let { return ResolvedType(it, protocolType = type.toString()) }
        val className = allocatedTypeName(baseClassName, key)

        val packageName = "${options.packetPackage}.types"
        val enumClass = ClassName(packageName, className)
        val enumBuilder = TypeSpec.enumBuilder(className)
            .addAnnotation(serializable)
            .addAnnotation(protocolEnum)

        val used = mutableSetOf<String>()
        mappings.entries.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }.forEach { (raw, mappedName) ->
            val serial = mappedName.jsonPrimitive.content
            val constant = naming.uniqueName(naming.toPascalCase(serial).ifEmpty { "Value$raw" }, used)
            enumBuilder.addEnumConstant(
                constant,
                TypeSpec.anonymousClassBuilder()
                    .addAnnotation(AnnotationSpec.builder(serialName).addMember("%S", serial).build())
                    .addAnnotation(AnnotationSpec.builder(protocolEnumValue).addMember("%L", raw.toInt()).build())
                    .build()
            )
        }

        FileSpec.builder(packageName, className)
            .addFileComment("This file is generated by kmcp.\n")
            .addType(enumBuilder.build())
            .build()
            .writeTo(options.outputDirectory)

        emittedTypes[key] = enumClass
        return ResolvedType(enumClass, protocolType = type.toString())
    }

    private fun generatedContainerType(type: JsonArray, aliases: JsonObject, hint: String): ResolvedType {
        val baseClassName = naming.generatedTypeName(hint)
        val key = "container:$baseClassName:${type}"
        emittedTypes[key]?.let { return ResolvedType(it, protocolType = type.toString()) }
        val className = allocatedTypeName(baseClassName, key)

        val packageName = "${options.packetPackage}.types"
        val generatedClass = ClassName(packageName, className)
        emittedTypes[key] = generatedClass

        val fields = when (type.getOrNull(0)?.jsonPrimitive?.content) {
            "bitfield" -> type.getOrNull(1)?.jsonArray.orEmpty().mapNotNull { field ->
                val obj = field.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                PacketField(name, naming.toPropertyName(name), INT)
            }
            "registryEntryHolder" -> {
                val options = type.getOrNull(1)?.jsonObject.orEmpty()
                val baseName = options["baseName"]?.jsonPrimitive?.content ?: "id"
                val otherwise = options["otherwise"]?.jsonObject
                buildList {
                    add(PacketField(baseName, naming.toPropertyName(baseName), INT.copy(nullable = true), "VarInt"))
                    if (otherwise != null) {
                        val otherwiseName = otherwise["name"]?.jsonPrimitive?.content ?: "data"
                        val otherwiseType = resolveType(
                            type = otherwise["type"] ?: JsonPrimitive("void"),
                            aliases = aliases,
                            seen = mutableSetOf(),
                            hint = "${className}${naming.toPascalCase(otherwiseName)}",
                        )
                        if (!otherwiseType.skip) {
                            add(
                                PacketField(
                                    otherwiseName,
                                    naming.toPropertyName(otherwiseName),
                                    otherwiseType.typeName.copy(nullable = true),
                                    otherwiseType.numberEncoding,
                                    otherwiseType.protocolType,
                                )
                            )
                        }
                    }
                }
            }
            "registryEntryHolderSet" -> {
                val options = type.getOrNull(1)?.jsonObject.orEmpty()
                val base = options["base"]?.jsonObject
                val otherwise = options["otherwise"]?.jsonObject
                buildList {
                    if (base != null) {
                        val baseName = base["name"]?.jsonPrimitive?.content ?: "name"
                        val baseType = resolveType(
                            type = base["type"] ?: JsonPrimitive("string"),
                            aliases = aliases,
                            seen = mutableSetOf(),
                            hint = "${className}${naming.toPascalCase(baseName)}",
                        )
                        if (!baseType.skip) {
                            add(
                                PacketField(
                                    baseName,
                                    naming.toPropertyName(baseName),
                                    baseType.typeName.copy(nullable = true),
                                    baseType.numberEncoding,
                                    baseType.protocolType,
                                )
                            )
                        }
                    }
                    if (otherwise != null) {
                        val otherwiseName = otherwise["name"]?.jsonPrimitive?.content ?: "ids"
                        val otherwiseType = resolveType(
                            type = otherwise["type"] ?: JsonPrimitive("varint"),
                            aliases = aliases,
                            seen = mutableSetOf(),
                            hint = "${className}${naming.toPascalCase(otherwiseName)}Entry",
                        )
                        if (!otherwiseType.skip) {
                            add(
                                PacketField(
                                    otherwiseName,
                                    naming.toPropertyName(otherwiseName),
                                    LIST.parameterizedBy(otherwiseType.typeName).copy(nullable = true),
                                    null,
                                    type.toString(),
                                )
                            )
                        }
                    }
                }
            }
            else -> type.getOrNull(1)?.jsonArray.orEmpty().mapNotNull { fieldElement ->
                val field = fieldElement.jsonObject
                val sourceName = field["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val resolved = resolveType(
                    type = field["type"] ?: return@mapNotNull null,
                    aliases = aliases,
                    seen = mutableSetOf(),
                    hint = "${className}${naming.toPascalCase(sourceName)}",
                )
                if (resolved.skip) return@mapNotNull null
                PacketField(sourceName, naming.toPropertyName(sourceName), resolved.typeName, resolved.numberEncoding, resolved.protocolType)
            }
        }

        val used = mutableSetOf<String>()
        val uniqueFields = fields.map { field ->
            field.copy(propertyName = naming.uniqueName(field.propertyName, used))
        }

        val constructor = com.squareup.kotlinpoet.FunSpec.constructorBuilder()
        uniqueFields.forEach { constructor.addParameter(it.propertyName, it.typeName) }

        val typeBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .addAnnotation(serializable)
            .primaryConstructor(constructor.build())

        uniqueFields.forEach { field ->
            typeBuilder.addProperty(
                PropertySpec.builder(field.propertyName, field.typeName)
                    .initializer(field.propertyName)
                    .apply {
                        if (field.sourceName != field.propertyName) {
                            addAnnotation(AnnotationSpec.builder(serialName).addMember("%S", field.sourceName).build())
                        }
                    }
                    .build()
            )
        }

        FileSpec.builder(packageName, className)
            .addFileComment("This file is generated by kmcp.\n")
            .addType(typeBuilder.build())
            .build()
            .writeTo(options.outputDirectory)

        return ResolvedType(generatedClass, protocolType = type.toString())
    }

    private fun allocatedTypeName(baseName: String, key: String): String {
        usedTypeNames[baseName]?.let { existingKey ->
            if (existingKey == key) return baseName
        } ?: run {
            usedTypeNames[baseName] = key
            return baseName
        }

        var index = 2
        while (true) {
            val candidate = "$baseName$index"
            val existingKey = usedTypeNames[candidate]
            if (existingKey == null || existingKey == key) {
                usedTypeNames[candidate] = key
                return candidate
            }
            index += 1
        }
    }
}

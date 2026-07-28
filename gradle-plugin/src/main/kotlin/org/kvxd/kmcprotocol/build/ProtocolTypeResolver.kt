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
import com.squareup.kotlinpoet.SET
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
    private val protocolSwitchPayload = ClassName("${options.runtimePackage}.core.encoding", "ProtocolSwitchPayload")
    private val byteArray = ClassName("kotlin", "ByteArray")
    private val nbtTag = ClassName("net.benwoodworth.knbt", "NbtTag")
    private val lpVec3 = ClassName("${options.runtimePackage}.core", "LpVec3")
    private val uuid = ClassName("java.util", "UUID")
    private val protocolBitfield = ClassName("${options.runtimePackage}.core.encoding", "ProtocolBitfield")
    private val protocolArray = ClassName("${options.runtimePackage}.core.encoding", "ProtocolArray")
    private val protocolBinary = ClassName("${options.runtimePackage}.core.encoding", "ProtocolBinary")
    private val binaryKind = ClassName("${options.runtimePackage}.core.encoding", "BinaryKind")
    private val protocolOption = ClassName("${options.runtimePackage}.core.encoding", "ProtocolOption")
    private val protocolFlags = ClassName("${options.runtimePackage}.core.encoding", "ProtocolFlags")
    private val protocolSwitch = ClassName("${options.runtimePackage}.core.encoding", "ProtocolSwitch")
    private val protocolNumber = ClassName("${options.runtimePackage}.core.encoding", "ProtocolNumber")
    private val protocolType = ClassName("${options.runtimePackage}.core.encoding", "ProtocolType")
    private val numberEncoding = ClassName("${options.runtimePackage}.core.encoding", "NumberEncoding")
    private val uuidSerializer = ClassName("${options.runtimePackage}.core.encoding", "UuidSerializer")

    private fun useSerializersAnnotation(): AnnotationSpec =
        AnnotationSpec.builder(ClassName("kotlinx.serialization", "UseSerializers"))
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
            .addMember("%T::class", uuidSerializer)
            .build()

    private val emittedTypes = mutableMapOf<String, ClassName>()
    private val usedTypeNames = mutableMapOf<String, String>()
    private var packetOwner: TypeOwner? = null
    private val ownerStack = mutableListOf<TypeOwner>()

    private val scalarTypes = mapOf(
        "bool" to ResolvedType(ClassName("kotlin", "Boolean"), native = true),
        "i8" to ResolvedType(ClassName("kotlin", "Byte"), "Byte", native = true),
        "u8" to ResolvedType(ClassName("kotlin", "Short"), "UnsignedByte", native = true),
        "i16" to ResolvedType(ClassName("kotlin", "Short"), "Short", native = true),
        "u16" to ResolvedType(INT, "UnsignedShort", native = true),
        "i32" to ResolvedType(INT, "Int", native = true),
        "u32" to ResolvedType(LONG, "UnsignedInt", native = true),
        "varint" to ResolvedType(INT, "VarInt", native = true),
        "optvarint" to ResolvedType(INT, "VarInt", native = true),
        "i64" to ResolvedType(LONG, "Long", native = true),
        "varlong" to ResolvedType(LONG, "VarLong", native = true),
        "f32" to ResolvedType(ClassName("kotlin", "Float"), native = true),
        "f64" to ResolvedType(ClassName("kotlin", "Double"), native = true),
        "string" to ResolvedType(STRING, native = true),
        "UUID" to ResolvedType(uuid, native = true, serializer = uuidSerializer, binary = BinaryInfo("Uuid")),
        "restBuffer" to ResolvedType(byteArray, native = true, binary = BinaryInfo("RestBuffer")),
        "anonymousNbt" to ResolvedType(nbtTag, native = true, binary = BinaryInfo("AnonymousNbt")),
        "anonOptionalNbt" to ResolvedType(nbtTag.copy(nullable = true), native = true, binary = BinaryInfo("AnonOptionalNbt")),
        "nbt" to ResolvedType(nbtTag, native = true, binary = BinaryInfo("Nbt")),
        "optionalNbt" to ResolvedType(nbtTag.copy(nullable = true), native = true, binary = BinaryInfo("OptionalNbt")),
        "compressedNbt" to ResolvedType(nbtTag.copy(nullable = true), native = true, binary = BinaryInfo("CompressedNbt")),
        "u64" to ResolvedType(LONG, "Long", native = true),
        "byte_array" to ResolvedType(byteArray, protocolType = "\"byte_array\""),
        "lpVec3" to ResolvedType(lpVec3, protocolType = "\"lpVec3\""),
    )

    val emittedTypeCount: Int
        get() = emittedTypes.size

    fun beginPacket(packageName: String, className: String) {
        check(packetOwner == null && ownerStack.isEmpty()) { "Nested packet generation is already active." }
        val owner = TypeOwner(ClassName(packageName, className))
        packetOwner = owner
        ownerStack += owner
    }

    fun endPacket(): List<TypeSpec> {
        val owner = packetOwner ?: return emptyList()
        check(ownerStack.size == 1 && ownerStack.single() === owner) { "Nested packet generation stack was not unwound." }
        ownerStack.clear()
        packetOwner = null
        return owner.children.toList()
    }

    fun resolveType(
        type: JsonElement,
        aliases: JsonObject,
        seen: MutableSet<String> = mutableSetOf(),
        hint: String = "Value",
        namedAlias: Boolean = false,
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
                return resolveType(alias, aliases, seen, aliasHint(typeName, alias, hint), namedAlias = true)
            }

            return dynamicType(type, aliases, hint)
        }

        val typeArray = type as? JsonArray ?: return dynamicType(type, aliases, hint)
        val kind = typeArray.getOrNull(0)?.jsonPrimitive?.content ?: return dynamicType(type, aliases, hint)

        instantiateParameterizedType(typeArray, kind, aliases)?.let { instantiated ->
            return resolveType(instantiated, aliases, seen, hint, namedAlias)
        }

        val options = typeArray.getOrNull(1) as? JsonObject

        return when (kind) {
            "pstring" -> ResolvedType(STRING, native = true)
            "mapper" -> mapperType(typeArray, options, hint, namedAlias)
            "bitflags" -> bitFlagsType(typeArray, options, hint, namedAlias)
            "array" -> {
                val element = resolveType(options?.get("type") ?: JsonPrimitive("void"), aliases, mutableSetOf(), arrayElementHint(hint))
                val listType = LIST.parameterizedBy(element.typeName)
                val count = options?.get("count")
                val fixedCount = (count as? JsonPrimitive)?.safeContentOrNull?.toIntOrNull()
                val countField = (count as? JsonPrimitive)?.safeContentOrNull?.takeIf { fixedCount == null }
                if (element.native) {
                    val level = ArrayLevelInfo(
                        countType = countEncoding(options?.get("countType")),
                        fixedCount = fixedCount ?: -1,
                        countField = countField.orEmpty(),
                    )
                    ResolvedType(
                        listType,
                        native = true,
                        arrayInfo = ArrayInfo(
                            levels = listOf(level) + element.arrayInfo?.levels.orEmpty(),
                            elementEncoding = element.arrayInfo?.elementEncoding
                                ?: element.numberEncoding
                                ?: "VarInt",
                        ),
                        binary = element.binary,
                    )
                } else {
                    ResolvedType(listType, protocolType = type.toString())
                }
            }
            "buffer" -> {
                val fixed = (options?.get("count") as? JsonPrimitive)?.safeContentOrNull?.toIntOrNull()
                if (options?.get("count") == null || fixed != null) {
                    ResolvedType(
                        byteArray,
                        native = true,
                        binary = BinaryInfo("Buffer", countEncoding(options?.get("countType")), fixed ?: -1),
                    )
                } else {
                    ResolvedType(byteArray, protocolType = type.toString())
                }
            }
            "option" -> {
                val nested = resolveType(typeArray.getOrNull(1) ?: JsonPrimitive("void"), aliases, mutableSetOf(), hint)
                if (nested.native && !nested.option) {
                    ResolvedType(
                        nested.typeName.copy(nullable = true),
                        numberEncoding = nested.numberEncoding,
                        native = true,
                        serializer = nested.serializer,
                        arrayInfo = nested.arrayInfo,
                        binary = nested.binary,
                        option = true,
                    )
                } else {
                    ResolvedType(nested.typeName.copy(nullable = true), protocolType = type.toString())
                }
            }
            "bitfield" -> bitfieldType(typeArray, hint, namedAlias)
            "container", "registryEntryHolder", "registryEntryHolderSet" ->
                generatedContainerType(typeArray, aliases, hint, namedAlias)
            "entityMetadataLoop" -> entityMetadataLoopType(typeArray, options, aliases, hint)
            "count" -> resolveType(options?.get("type") ?: JsonPrimitive("varint"), aliases, mutableSetOf(), hint)
            else -> dynamicType(type, aliases, hint)
        }
    }

    private fun instantiateParameterizedType(type: JsonArray, kind: String, aliases: JsonObject): JsonElement? {
        if (kind in builtinCompoundKinds) return null
        val definition = aliases[kind] ?: protocol["types"]?.jsonObject?.get(kind) ?: return null
        if (definition.jsonPrimitiveOrNull()?.safeContentOrNull == "native") return null
        val arguments = type.getOrNull(1) as? JsonObject ?: JsonObject(emptyMap())
        return substituteArguments(definition, arguments)
    }

    private fun substituteArguments(element: JsonElement, arguments: JsonObject): JsonElement =
        when (element) {
            is JsonPrimitive -> {
                val reference = element.safeContentOrNull?.takeIf { element.isString && it.startsWith("$") }
                reference?.let { arguments[it.substring(1)] } ?: element
            }
            is JsonArray -> JsonArray(element.map { substituteArguments(it, arguments) })
            is JsonObject -> JsonObject(element.mapValues { substituteArguments(it.value, arguments) })
            else -> element
        }

    private val builtinCompoundKinds = setOf(
        "array",
        "buffer",
        "option",
        "container",
        "switch",
        "mapper",
        "bitflags",
        "bitfield",
        "pstring",
        "topBitSetTerminatedArray",
        "registryEntryHolder",
        "registryEntryHolderSet",
        "entityMetadataLoop",
        "count",
        "void",
    )

    private fun dynamicType(type: JsonElement, aliases: JsonObject, hint: String): ResolvedType =
        when ((type as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.content) {
            "switch" -> switchType(type, type.getOrNull(1) as? JsonObject, aliases, hint)
            "topBitSetTerminatedArray" -> {
                val options = type.getOrNull(1) as? JsonObject
                val element = resolveType(options?.get("type") ?: JsonPrimitive("void"), aliases, mutableSetOf(), arrayElementHint(hint))
                ResolvedType(LIST.parameterizedBy(element.typeName), protocolType = type.toString())
            }
            else -> error("Unsupported protocol type for generated Kotlin mapping: $type")
        }

    private fun entityMetadataLoopType(type: JsonArray, options: JsonObject?, aliases: JsonObject, hint: String): ResolvedType {
        val element = resolveType(
            type = options?.get("type") ?: JsonPrimitive("void"),
            aliases = aliases,
            seen = mutableSetOf(),
            hint = arrayElementHint(hint),
        )
        return ResolvedType(LIST.parameterizedBy(element.typeName), protocolType = type.toString())
    }

    private fun aliasHint(typeName: String, alias: JsonElement, fallback: String): String {
        val aliasKind = (alias as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.content
        return if (aliasKind in namedAliasKinds) naming.toPascalCase(typeName).ifEmpty { fallback } else fallback
    }

    private val namedAliasKinds = setOf(
        "mapper",
        "bitflags",
        "container",
        "bitfield",
        "registryEntryHolder",
        "registryEntryHolderSet",
    )

    private fun bitFlagsType(type: JsonArray, typeOptions: JsonObject?, hint: String, namedAlias: Boolean): ResolvedType {
        val flags = typeOptions?.get("flags")?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val baseClassName = naming.generatedTypeName(hint)
        val key = emittedTypeKey("bitflags:$baseClassName:${flags.joinToString(",")}", namedAlias)
        emittedTypes[key]?.let { return ResolvedType(SET.parameterizedBy(it), protocolType = type.toString()) }
        val target = emissionTarget(baseClassName, key, namedAlias)
        val enumClass = target.className
        val enumBuilder = TypeSpec.enumBuilder(target.simpleName)
            .addAnnotation(serializable)

        val used = mutableSetOf<String>()
        flags.forEachIndexed { index, flag ->
            enumBuilder.addEnumConstant(
                naming.uniqueName(enumConstantName(flag, index), used),
                TypeSpec.anonymousClassBuilder()
                    .addAnnotation(AnnotationSpec.builder(serialName).addMember("%S", flag).build())
                    .build()
            )
        }

        emitType(target, enumBuilder.build())

        emittedTypes[key] = enumClass
        return ResolvedType(
            SET.parameterizedBy(enumClass),
            native = true,
            flagsEncoding = countEncoding(typeOptions?.get("type")),
        )
    }

    private fun enumConstantName(raw: String, index: Int): String {
        val pascal = naming.toPascalCase(raw).ifEmpty { "Flag$index" }
        return if (pascal.first().isLetter() || pascal.first() == '_') pascal else "Flag$pascal"
    }

    private fun countEncoding(countType: JsonElement?): String =
        when ((countType as? JsonPrimitive)?.safeContentOrNull) {
            "u8" -> "UnsignedByte"
            "i8" -> "Byte"
            "u16" -> "UnsignedShort"
            "i16" -> "Short"
            "i32" -> "Int"
            else -> "VarInt"
        }

    private fun arrayElementHint(hint: String): String =
        when {
            hint.endsWith("Entries") -> hint.removeSuffix("Entries") + "Entry"
            hint.endsWith("ies") -> hint.dropLast(3) + "y"
            hint.endsWith("ches") || hint.endsWith("shes") || hint.endsWith("xes") || hint.endsWith("zes") ->
                hint.dropLast(2)
            hint.endsWith("s") && !hint.endsWith("ss") -> hint.dropLast(1)
            else -> "${hint}Entry"
        }

    private fun switchType(type: JsonArray, options: JsonObject?, aliases: JsonObject, hint: String): ResolvedType {
        val branches = switchBranches(options)
        if (branches.isEmpty()) return ResolvedType(skip = true)

        val resolvedBranches = branches.mapNotNull { branch ->
            val resolved = resolveType(
                type = branch.type,
                aliases = aliases,
                seen = mutableSetOf(),
                hint = "${hint}${naming.toPascalCase(branch.name).ifEmpty { "Default" }}",
            )
            if (resolved.skip) null else branch to resolved
        }
        if (resolvedBranches.isEmpty()) return ResolvedType(skip = true, protocolType = type.toString())

        val distinctTypes = resolvedBranches
            .map { it.second.copy(typeName = it.second.typeName.copy(nullable = false)) }
            .distinctBy { listOf(it.typeName, it.numberEncoding, it.protocolType) }
        if (distinctTypes.size == 1) {
            val resolved = distinctTypes.single()
            val nullable = branches.size != resolvedBranches.size || branches.size > 1
            val compareTo = options?.get("compareTo")?.jsonPrimitive?.safeContentOrNull
            if (nullable && compareTo != null && resolved.native && !resolved.option && resolved.switch == null) {
                val presentNames = resolvedBranches.map { it.first.name }.toSet()
                val allNames = branches.map { it.name }
                return ResolvedType(
                    typeName = resolved.typeName.copy(nullable = true),
                    numberEncoding = resolved.numberEncoding,
                    native = true,
                    serializer = resolved.serializer,
                    arrayInfo = resolved.arrayInfo,
                    binary = resolved.binary,
                    flagsEncoding = resolved.flagsEncoding,
                    switch = SwitchInfo(
                        compareTo = compareTo,
                        presentKeys = allNames.filter { it in presentNames && it != DefaultSwitchBranchName },
                        voidKeys = allNames.filter { it !in presentNames && it != DefaultSwitchBranchName },
                        defaultPresent = DefaultSwitchBranchName in presentNames,
                    ),
                )
            }
            return ResolvedType(
                typeName = if (nullable) resolved.typeName.copy(nullable = true) else resolved.typeName,
                numberEncoding = resolved.numberEncoding,
                protocolType = type.toString(),
            )
        }

        val baseClassName = naming.generatedTypeName(hint)
        val key = emittedTypeKey("switch:$baseClassName:${type}", namedAlias = false)
        emittedTypes[key]?.let { return ResolvedType(it, protocolType = type.toString()) }
        val target = emissionTarget(baseClassName, key, namedAlias = false)
        emittedTypes[key] = target.className

        val used = mutableSetOf<String>()
        val fields = resolvedBranches.map { (branch, resolved) ->
            PacketField(
                sourceName = switchBranchPropertyName(branch.name),
                propertyName = naming.uniqueName(switchBranchPropertyName(branch.name), used),
                typeName = resolved.typeName.copy(nullable = true),
                numberEncoding = resolved.numberEncoding,
                protocolType = resolved.protocolType,
                serializer = resolved.serializer,
            )
        }
        val constructor = com.squareup.kotlinpoet.FunSpec.constructorBuilder()
        fields.forEach { constructor.addParameter(it.propertyName, it.typeName) }
        val typeBuilder = TypeSpec.classBuilder(target.simpleName)
            .addModifiers(KModifier.DATA)
            .addAnnotation(serializable)
            .addAnnotation(protocolSwitchPayload)
            .primaryConstructor(constructor.build())

        fields.forEach { field ->
            typeBuilder.addProperty(
                PropertySpec.builder(field.propertyName, field.typeName)
                    .initializer(field.propertyName)
                    .apply {
                        field.serializer?.let {
                            addAnnotation(
                                AnnotationSpec.builder(serializable).addMember("with·=·%T::class", it).build()
                            )
                        }
                    }
                    .build()
            )
        }

        emitType(target, typeBuilder.build())
        return ResolvedType(target.className, protocolType = type.toString())
    }

    private fun switchBranches(options: JsonObject?): List<SwitchBranch> =
        buildList {
            options?.get("fields")?.jsonObject.orEmpty().forEach { (key, value) ->
                add(SwitchBranch(key, value))
            }
            options?.get("default")?.let { add(SwitchBranch(DefaultSwitchBranchName, it)) }
        }

    private fun switchBranchPropertyName(name: String): String =
        if (name == DefaultSwitchBranchName) "defaultValue" else naming.toPropertyName(name)

    private fun mapperType(type: JsonArray, typeOptions: JsonObject?, hint: String, namedAlias: Boolean): ResolvedType {
        val mappings = typeOptions?.get("mappings")?.jsonObject ?: return ResolvedType(INT, "VarInt", type.toString())
        val baseClassName = naming.generatedTypeName(hint)
        val key = emittedTypeKey("mapper:$baseClassName:${mappings.entries.joinToString(",") { "${it.key}:${it.value}" }}", namedAlias)
        emittedTypes[key]?.let { return ResolvedType(it, protocolType = type.toString()) }
        val target = emissionTarget(baseClassName, key, namedAlias)
        val enumClass = target.className
        val enumBuilder = TypeSpec.enumBuilder(target.simpleName)
            .addAnnotation(serializable)
            .addAnnotation(
                AnnotationSpec.builder(protocolEnum)
                    .addMember("%T.%L", numberEncoding, countEncoding(typeOptions?.get("type")))
                    .build()
            )

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

        emitType(target, enumBuilder.build())

        emittedTypes[key] = enumClass
        return ResolvedType(enumClass, native = true)
    }

    private fun bitfieldType(type: JsonArray, hint: String, namedAlias: Boolean): ResolvedType {
        val members = type.getOrNull(1)?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val size = obj["size"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val signed = obj["signed"]?.jsonPrimitive?.safeContentOrNull == "true"
            Triple(name, size, signed)
        }
        if (members.isEmpty()) return ResolvedType(skip = true)

        val baseClassName = naming.generatedTypeName(hint)
        val key = emittedTypeKey("bitfield:$baseClassName:$type", namedAlias)
        emittedTypes[key]?.let { return ResolvedType(it) }
        val target = emissionTarget(baseClassName, key, namedAlias)
        emittedTypes[key] = target.className

        val used = mutableSetOf<String>()
        val fields = members.map { (name, size, signed) ->
            Triple(naming.uniqueName(naming.toPropertyName(name), used), size, signed) to name
        }

        val constructor = com.squareup.kotlinpoet.FunSpec.constructorBuilder()
        val builder = TypeSpec.classBuilder(target.simpleName)
            .addModifiers(KModifier.DATA)
            .addAnnotation(serializable)
            .addAnnotation(
                AnnotationSpec.builder(protocolBitfield)
                    .addMember("sizes·=·[%L]", members.joinToString(",·") { it.second.toString() })
                    .addMember("signed·=·[%L]", members.joinToString(",·") { it.third.toString() })
                    .build()
            )

        val checks = com.squareup.kotlinpoet.CodeBlock.builder()
        fields.forEach { (spec, sourceName) ->
            val (propertyName, size, signed) = spec
            val wide = size > 31
            val memberType = if (wide) LONG else INT
            constructor.addParameter(propertyName, memberType)
            builder.addProperty(
                PropertySpec.builder(propertyName, memberType)
                    .initializer(propertyName)
                    .apply {
                        if (sourceName != propertyName) {
                            addAnnotation(AnnotationSpec.builder(serialName).addMember("%S", sourceName).build())
                        }
                    }
                    .build()
            )

            val suffix = if (wide) "L" else ""
            val min = if (signed) "-${java.math.BigInteger.TWO.pow(size - 1)}$suffix" else "0$suffix"
            val max = if (signed) {
                "${java.math.BigInteger.TWO.pow(size - 1).subtract(java.math.BigInteger.ONE)}$suffix"
            } else {
                "${java.math.BigInteger.TWO.pow(size).subtract(java.math.BigInteger.ONE)}$suffix"
            }
            checks.addStatement(
                "require(%L·in·%L..%L)·{·%S·+·%L·}",
                propertyName, min, max,
                "$propertyName does not fit in $size ${if (signed) "signed" else "unsigned"} bits: ",
                propertyName,
            )
        }

        builder.primaryConstructor(constructor.build())
        builder.addInitializerBlock(checks.build())

        emitType(target, builder.build())
        return ResolvedType(target.className, native = true)
    }

    private fun generatedContainerType(type: JsonArray, aliases: JsonObject, hint: String, namedAlias: Boolean): ResolvedType {
        val baseClassName = naming.generatedTypeName(hint)
        val key = emittedTypeKey("container:$baseClassName:${type}", namedAlias)
        emittedTypes[key]?.let { return ResolvedType(it, protocolType = type.toString()) }
        val target = emissionTarget(baseClassName, key, namedAlias)
        val className = target.simpleName
        val generatedClass = target.className
        emittedTypes[key] = generatedClass

        val owner = TypeOwner(generatedClass)
        ownerStack += owner
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
                                    otherwiseType.serializer,
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
                                    baseType.serializer,
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
                            hint = arrayElementHint("${className}${naming.toPascalCase(otherwiseName)}"),
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
            else -> containerFields(type, aliases, className, nullable = false)
        }

        val used = mutableSetOf<String>()
        val uniqueFields = fields.distinctBy { it.sourceName }.map { field ->
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
                generatedProperty(field)
            )
        }
        owner.children.forEach { typeBuilder.addType(it) }

        check(ownerStack.removeAt(ownerStack.lastIndex) === owner) { "Generated type owner stack became unbalanced." }
        emitType(target, typeBuilder.build())

        val nativeContainer = type.getOrNull(0)?.jsonPrimitive?.content == "container" &&
            uniqueFields.all { it.protocolType == null } &&
            !hasAnonymousSwitch(type, aliases)
        return if (nativeContainer) {
            ResolvedType(generatedClass, native = true)
        } else {
            ResolvedType(generatedClass, protocolType = type.toString())
        }
    }

    private fun generatedProperty(field: PacketField): PropertySpec =
        PropertySpec.builder(field.propertyName, field.typeName)
            .initializer(field.propertyName)
            .apply {
                if (field.sourceName != field.propertyName) {
                    addAnnotation(AnnotationSpec.builder(serialName).addMember("%S", field.sourceName).build())
                }
                field.numberEncoding?.let {
                    addAnnotation(
                        AnnotationSpec.builder(protocolNumber).addMember("%T.%L", numberEncoding, it).build()
                    )
                }
                field.protocolType?.let {
                    addAnnotation(AnnotationSpec.builder(protocolType).addMember("%S", it).build())
                }
                field.arrayInfo?.let { addAnnotation(protocolArrayAnnotationOf(protocolArray, numberEncoding, it)) }
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


    private fun hasAnonymousSwitch(type: JsonArray, aliases: JsonObject): Boolean =
        type.getOrNull(1)?.jsonArray.orEmpty().any { fieldElement ->
            val field = fieldElement.jsonObject
            if (field["anon"]?.jsonPrimitive?.safeContentOrNull != "true") return@any false
            val expanded = expandAlias(field["type"] ?: JsonPrimitive("void"), aliases)
            (expanded as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.safeContentOrNull == "switch"
        }

    private fun containerFields(
        type: JsonArray,
        aliases: JsonObject,
        ownerHint: String,
        nullable: Boolean,
    ): List<PacketField> =
        type.getOrNull(1)?.jsonArray.orEmpty().flatMap { fieldElement ->
            val field = fieldElement.jsonObject
            val fieldType = field["type"] ?: return@flatMap emptyList()
            val anonymous = field["anon"]?.jsonPrimitive?.content == "true"
            if (anonymous) {
                return@flatMap anonymousFields(fieldType, aliases, ownerHint, nullable)
            }

            val sourceName = field["name"]?.jsonPrimitive?.content ?: return@flatMap emptyList()
            val resolved = resolveType(
                type = fieldType,
                aliases = aliases,
                seen = mutableSetOf(),
                hint = "${ownerHint}${naming.toPascalCase(sourceName)}",
            )
            if (resolved.skip) return@flatMap emptyList()
            listOf(
                PacketField(
                    sourceName,
                    naming.toPropertyName(sourceName),
                    if (nullable) resolved.typeName.copy(nullable = true) else resolved.typeName,
                    resolved.numberEncoding,
                    resolved.protocolType,
                    resolved.serializer,
                    resolved.arrayInfo,
                    resolved.binary,
                    resolved.option,
                    resolved.flagsEncoding,
                    resolved.switch,
                )
            )
        }

    private fun anonymousFields(
        type: JsonElement,
        aliases: JsonObject,
        ownerHint: String,
        nullable: Boolean,
    ): List<PacketField> {
        val expanded = expandAlias(type, aliases)
        val typeArray = expanded as? JsonArray ?: return emptyList()
        return when (typeArray.getOrNull(0)?.jsonPrimitive?.content) {
            "container" -> containerFields(typeArray, aliases, ownerHint, nullable)
            "switch" -> {
                val options = typeArray.getOrNull(1) as? JsonObject ?: return emptyList()
                val branchTypes = options["fields"]?.jsonObject?.values.orEmpty() + listOfNotNull(options["default"])
                branchTypes.flatMap { anonymousFields(it, aliases, ownerHint, nullable = true) }
            }
            else -> emptyList()
        }
    }

    private fun expandAlias(type: JsonElement, aliases: JsonObject, seen: MutableSet<String> = mutableSetOf()): JsonElement {
        val typeName = (type as? JsonPrimitive)?.content ?: return type
        if (!seen.add(typeName)) return type
        val alias = aliases[typeName] ?: protocol["types"]?.jsonObject?.get(typeName) ?: return type
        if (alias.jsonPrimitiveOrNull()?.safeContentOrNull == "native") return type
        return expandAlias(alias, aliases, seen)
    }

    private fun emissionTarget(baseName: String, key: String, namedAlias: Boolean): EmissionTarget {
        val parent = if (namedAlias) null else ownerStack.lastOrNull()
        val scopedBaseName = parent?.let { localTypeName(baseName, it.className.simpleName) } ?: baseName
        val usedNames = parent?.usedTypeNames ?: usedTypeNames
        val simpleName = allocatedTypeName(scopedBaseName, key, usedNames)
        val className = parent?.className?.nestedClass(simpleName)
            ?: ClassName("${options.packetPackage}.types", simpleName)
        return EmissionTarget(className, simpleName, parent)
    }

    private fun emittedTypeKey(key: String, namedAlias: Boolean): String =
        if (namedAlias) key else "${ownerStack.lastOrNull()?.className?.canonicalName}:$key"

    private fun emitType(target: EmissionTarget, type: TypeSpec) {
        val parent = target.parent
        if (parent != null) {
            parent.children += type
            return
        }

        FileSpec.builder("${options.packetPackage}.types", target.simpleName)
            .addFileComment("Generated by the kmcprotocol Gradle plugin. Do not edit.\n")
            .addAnnotation(useSerializersAnnotation())
            .addType(type)
            .build()
            .writeTo(options.outputDirectory)
    }

    private fun localTypeName(baseName: String, ownerName: String): String {
        val stripped = baseName.removePrefix(ownerName)
        return when {
            stripped.isEmpty() || stripped == baseName -> baseName
            stripped == ownerName -> "Entry"
            else -> stripped
        }
    }

    private fun allocatedTypeName(baseName: String, key: String, usedNames: MutableMap<String, String>): String {
        usedNames[baseName]?.let { existingKey ->
            if (existingKey == key) return baseName
        } ?: run {
            usedNames[baseName] = key
            return baseName
        }

        var index = 2
        while (true) {
            val candidate = "$baseName$index"
            val existingKey = usedNames[candidate]
            if (existingKey == null || existingKey == key) {
                usedNames[candidate] = key
                return candidate
            }
            index += 1
        }
    }

    private data class TypeOwner(
        val className: ClassName,
        val children: MutableList<TypeSpec> = mutableListOf(),
        val usedTypeNames: MutableMap<String, String> = mutableMapOf(),
    )

    private data class EmissionTarget(
        val className: ClassName,
        val simpleName: String,
        val parent: TypeOwner?,
    )

    private data class SwitchBranch(
        val name: String,
        val type: JsonElement,
    )

    private companion object {
        const val DefaultSwitchBranchName = "\u0000default"
    }
}

package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.nio.file.Path

internal class MinecraftAccessorEmitter(
    private val runtimePackage: String,
    private val dataPackage: String,
    private val outputDirectory: Path,
) {
    private val blockData = ClassName("$runtimePackage.data", "BlockData")
    private val itemData = ClassName("$runtimePackage.data", "ItemData")
    private val entityData = ClassName("$runtimePackage.data", "EntityData")
    private val source = ClassName(dataPackage, "GeneratedMinecraftData")

    fun emit(model: MinecraftDataModel): AccessorReport {
        emitRegistry("Blocks", blockData, "blocks", model.blocks.map(BlockModel::name), model.minecraftVersion)
        emitRegistry("Items", itemData, "items", model.items.map(ItemModel::name), model.minecraftVersion)
        emitRegistry("Entities", entityData, "entities", model.entities.map(EntityModel::name), model.minecraftVersion)

        val enums = collectEnums(model.blocks)
        if (enums.isNotEmpty()) emitEnums(enums, model.minecraftVersion)
        val stateFunctions = emitBlockStates(model, enums)

        return AccessorReport(
            enumCount = enums.size,
            stateFunctionCount = stateFunctions,
        )
    }

    private fun emitRegistry(
        objectName: String,
        elementType: ClassName,
        listProperty: String,
        names: List<String>,
        minecraftVersion: String,
    ) {
        if (names.isEmpty()) return

        val allocator = NameAllocator()
        val registry = TypeSpec.objectBuilder(objectName)

        names.forEachIndexed { index, name ->
            registry.addProperty(
                PropertySpec.builder(allocator.allocate(name.pascalCase()), elementType)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return %T.data.%L[%L]", source, listProperty, index)
                            .build()
                    )
                    .build()
            )
        }

        fileSpec(objectName, minecraftVersion).addType(registry.build()).build().writeTo(outputDirectory)
    }

    private fun collectEnums(blocks: List<BlockModel>): Map<EnumKey, EnumSpec> {
        val keys = LinkedHashSet<EnumKey>()
        blocks.forEach { block ->
            block.states
                .filter { it.type == "enum" }
                .forEach { keys += EnumKey(it.name, it.values) }
        }

        val allocator = NameAllocator()
        return keys
            .sortedWith(compareBy({ it.property }, { it.values.size }, { it.values.joinToString() }))
            .associateWith { key ->
                EnumSpec(
                    className = ClassName(dataPackage, allocator.allocate(key.property.pascalCase())),
                    values = key.values,
                )
            }
    }

    private fun emitEnums(enums: Map<EnumKey, EnumSpec>, minecraftVersion: String) {
        val file = fileSpec("BlockStateEnums", minecraftVersion)
        enums.values.sortedBy { it.className.simpleName }.forEach { spec ->
            val allocator = NameAllocator()
            val enum = TypeSpec.enumBuilder(spec.className)
            spec.values.forEach { value -> enum.addEnumConstant(allocator.allocate(value.pascalCase())) }
            file.addType(enum.build())
        }
        file.build().writeTo(outputDirectory)
    }

    private fun emitBlockStates(model: MinecraftDataModel, enums: Map<EnumKey, EnumSpec>): Int {
        val blocks = model.blocks.filter { it.states.isNotEmpty() }
        if (blocks.isEmpty()) return 0

        val allocator = NameAllocator()
        val states = TypeSpec.objectBuilder("BlockStates")
        var functions = 0

        blocks.forEach { block ->
            val builderName = allocator.allocate(block.name.camelCase())
            states.addFunction(builderFunction(builderName, block, enums))
            functions++

            block.states.forEach { property ->
                val readerName = allocator.allocate("${block.name.camelCase()}${property.name.pascalCase()}")
                states.addFunction(readerFunction(readerName, block, property, enums))
                functions++
            }
        }

        fileSpec("BlockStates", model.minecraftVersion).addType(states.build()).build().writeTo(outputDirectory)
        return functions
    }

    private fun builderFunction(
        name: String,
        block: BlockModel,
        enums: Map<EnumKey, EnumSpec>,
    ): FunSpec {
        val builder = FunSpec.builder(name).returns(INT)
        val allocator = NameAllocator()
        val parameters = block.states.map { property ->
            val parameterName = allocator.allocate(property.name.camelCase())
            val defaultIndex = property.indexIn(block.defaultState, block.minStateId)
            builder.addParameter(
                ParameterSpec.builder(parameterName, property.typeName(enums))
                    .defaultValue(property.literalFor(defaultIndex, enums))
                    .build()
            )
            parameterName to property
        }

        val expression = CodeBlock.builder().add("%L", block.minStateId)
        parameters.forEach { (parameterName, property) ->
            expression.add(" + ")
            expression.add(property.indexExpression(parameterName, enums))
            if (property.stride != 1) expression.add(" * %L", property.stride)
        }

        return builder.addStatement("return %L", expression.build()).build()
    }

    private fun readerFunction(
        name: String,
        block: BlockModel,
        property: StatePropertyModel,
        enums: Map<EnumKey, EnumSpec>,
    ): FunSpec {
        val index = CodeBlock.builder()
            .add("(stateId - %L)", block.minStateId)
            .apply { if (property.stride != 1) add(" / %L", property.stride) }
            .add(" %% %L", property.numValues)
            .build()

        val body = when {
            property.type == "bool" -> CodeBlock.of("%L == 0", index)
            property.type == "int" -> {
                val base = property.values.firstOrNull()?.toIntOrNull() ?: 0
                if (base == 0) index else CodeBlock.of("%L + %L", index, base)
            }
            else -> CodeBlock.of("%T.entries[%L]", enums.getValue(property.key()).className, index)
        }

        return FunSpec.builder(name)
            .addParameter("stateId", INT)
            .returns(property.typeName(enums))
            .addStatement("return %L", body)
            .build()
    }

    private fun StatePropertyModel.key(): EnumKey = EnumKey(name, values)

    private fun StatePropertyModel.typeName(enums: Map<EnumKey, EnumSpec>): TypeName = when (type) {
        "bool" -> BOOLEAN
        "int" -> INT
        else -> enums.getValue(key()).className
    }

    private fun StatePropertyModel.indexIn(stateId: Int, minStateId: Int): Int =
        ((stateId - minStateId) / stride) % numValues

    private fun StatePropertyModel.literalFor(index: Int, enums: Map<EnumKey, EnumSpec>): CodeBlock = when (type) {
        "bool" -> CodeBlock.of("%L", index == 0)
        "int" -> CodeBlock.of("%L", values.getOrNull(index)?.toIntOrNull() ?: index)
        else -> {
            val spec = enums.getValue(key())
            CodeBlock.of("%T.%L", spec.className, spec.entryNames[index])
        }
    }

    private fun StatePropertyModel.indexExpression(
        parameterName: String,
        enums: Map<EnumKey, EnumSpec>,
    ): CodeBlock = when (type) {
        "bool" -> CodeBlock.of("(if (%L) 0 else 1)", parameterName)
        "int" -> {
            val base = values.firstOrNull()?.toIntOrNull() ?: 0
            if (base == 0) CodeBlock.of("%L", parameterName) else CodeBlock.of("(%L - %L)", parameterName, base)
        }
        else -> {
            enums.getValue(key())
            CodeBlock.of("%L.ordinal", parameterName)
        }
    }

    private fun fileSpec(fileName: String, minecraftVersion: String): FileSpec.Builder =
        FileSpec.builder(dataPackage, fileName)
            .addFileComment("Generated by the kmcprotocol Gradle plugin from PrismarineJS minecraft-data.\n")
            .addFileComment("Minecraft %L. Do not edit.\n", minecraftVersion)
}

internal data class EnumKey(val property: String, val values: List<String>)

internal class EnumSpec(val className: ClassName, val values: List<String>) {
    val entryNames: List<String> = NameAllocator().let { allocator ->
        values.map { allocator.allocate(it.pascalCase()) }
    }
}

internal data class AccessorReport(
    val enumCount: Int,
    val stateFunctionCount: Int,
)

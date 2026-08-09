package org.kvxd.kmcprotocol.build

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.nio.file.Path

internal class MinecraftDataEmitter(
    private val runtimePackage: String,
    private val dataPackage: String,
    private val outputDirectory: Path,
    private val chunkSize: Int,
) {
    private val blockData = ClassName("$runtimePackage.data", "BlockData")
    private val blockDrop = ClassName("$runtimePackage.data", "BlockDrop")
    private val blockStateProperty = ClassName("$runtimePackage.data", "BlockStateProperty")
    private val boundingBox = ClassName("$runtimePackage.data", "BoundingBox")
    private val statePropertyType = ClassName("$runtimePackage.data", "StatePropertyType")
    private val variation = ClassName("$runtimePackage.data", "Variation")
    private val itemData = ClassName("$runtimePackage.data", "ItemData")
    private val entityData = ClassName("$runtimePackage.data", "EntityData")
    private val minecraftData = ClassName("$runtimePackage.data", "MinecraftData")
    private val minecraftDataSource = ClassName("$runtimePackage.data", "MinecraftDataSource")

    fun emit(model: MinecraftDataModel): MinecraftDataReport {
        emitEntries("GeneratedBlocks", blockData, model.blocks, model.minecraftVersion, ::blockInitializer)
        emitEntries("GeneratedItems", itemData, model.items, model.minecraftVersion, ::itemInitializer)
        emitEntries("GeneratedEntities", entityData, model.entities, model.minecraftVersion, ::entityInitializer)
        emitDataSource(model)

        return MinecraftDataReport(
            blockCount = model.blocks.size,
            itemCount = model.items.size,
            entityCount = model.entities.size,
            blockStateCount = model.blocks.maxOfOrNull { it.maxStateId + 1 } ?: 0,
        )
    }

    private fun <T> emitEntries(
        objectName: String,
        elementType: ClassName,
        entries: List<T>,
        minecraftVersion: String,
        initializer: (T) -> CodeBlock,
    ) {
        val listType = LIST.parameterizedBy(elementType)
        val chunks = entries.chunked(chunkSize)
        val holder = TypeSpec.objectBuilder(objectName).addModifiers(KModifier.INTERNAL)

        chunks.forEachIndexed { index, chunk ->
            holder.addFunction(
                FunSpec.builder("part$index")
                    .addModifiers(KModifier.PRIVATE)
                    .returns(listType)
                    .addCode(
                        CodeBlock.builder().apply {
                            if (chunk.isEmpty()) {
                                add("return emptyList()\n")
                                return@apply
                            }
                            add("return listOf(\n⇥")
                            chunk.forEachIndexed { entryIndex, entry ->
                                if (entryIndex > 0) add(",\n")
                                add(initializer(entry))
                            }
                            add(",\n⇤)\n")
                        }.build()
                    )
                    .build()
            )
        }

        holder.addFunction(
            FunSpec.builder("entries")
                .returns(listType)
                .addCode(
                    CodeBlock.builder().apply {
                        if (chunks.isEmpty()) {
                            add("return emptyList()\n")
                            return@apply
                        }
                        add("val entries = %T<%T>(%L)\n", ArrayList::class, elementType, entries.size)
                        chunks.indices.forEach { add("entries.addAll(part%L())\n", it) }
                        add("return entries\n")
                    }.build()
                )
                .build()
        )

        fileSpec(objectName, minecraftVersion).addType(holder.build()).build().writeTo(outputDirectory)
    }

    private fun emitDataSource(model: MinecraftDataModel) {
        val source = TypeSpec.objectBuilder("GeneratedMinecraftData")
            .addSuperinterface(minecraftDataSource)
            .addProperty(
                PropertySpec.builder("minecraftVersion", String::class, KModifier.OVERRIDE)
                    .initializer("%S", model.minecraftVersion)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("data", minecraftData, KModifier.OVERRIDE)
                    .initializer(
                        CodeBlock.builder()
                            .add("%T(\n⇥", minecraftData)
                            .add("minecraftVersion = minecraftVersion,\n")
                            .add("blocks = %T::entries,\n", ClassName(dataPackage, "GeneratedBlocks"))
                            .add("items = %T::entries,\n", ClassName(dataPackage, "GeneratedItems"))
                            .add("entities = %T::entries,\n", ClassName(dataPackage, "GeneratedEntities"))
                            .add("⇤)")
                            .build()
                    )
                    .build()
            )

        fileSpec("GeneratedMinecraftData", model.minecraftVersion)
            .addType(source.build())
            .build()
            .writeTo(outputDirectory)
    }

    private fun blockInitializer(block: BlockModel): CodeBlock = CodeBlock.builder()
        .add("%T(\n⇥", blockData)
        .add("id = %L,\n", block.id)
        .add("name = %S,\n", block.name)
        .add("displayName = %S,\n", block.displayName)
        .add("hardness = %L,\n", block.hardness.literal())
        .add("resistance = %L,\n", block.resistance.literal())
        .add("stackSize = %L,\n", block.stackSize)
        .add("diggable = %L,\n", block.diggable)
        .add("material = %L,\n", block.material.literal())
        .add("transparent = %L,\n", block.transparent)
        .add("emitLight = %L,\n", block.emitLight)
        .add("filterLight = %L,\n", block.filterLight)
        .add("defaultState = %L,\n", block.defaultState)
        .add("minStateId = %L,\n", block.minStateId)
        .add("maxStateId = %L,\n", block.maxStateId)
        .add("boundingBox = %T.%L,\n", boundingBox, block.boundingBox.boundingBoxEntry())
        .apply {
            if (block.states.isNotEmpty()) {
                add("states = listOf(\n⇥")
                block.states.forEachIndexed { index, state ->
                    if (index > 0) add(",\n")
                    add(stateInitializer(state))
                }
                add(",\n⇤),\n")
            }
            if (block.harvestTools.isNotEmpty()) {
                add("harvestTools = setOf(%L),\n", block.harvestTools.joinToString())
            }
            if (block.drops.isNotEmpty()) {
                add("drops = listOf(")
                block.drops.forEachIndexed { index, drop ->
                    if (index > 0) add(", ")
                    add(dropInitializer(drop))
                }
                add("),\n")
            }
            addVariations(block.variations)
        }
        .add("⇤)")
        .build()

    private fun stateInitializer(state: StatePropertyModel): CodeBlock = CodeBlock.builder()
        .add(
            "%T(name = %S, type = %T.%L, numValues = %L, stride = %L, values = listOf(%L))",
            blockStateProperty,
            state.name,
            statePropertyType,
            state.type.statePropertyEntry(),
            state.numValues,
            state.stride,
            state.values.joinToString { CodeBlock.of("%S", it).toString() },
        )
        .build()

    private fun dropInitializer(drop: DropModel): CodeBlock = CodeBlock.builder()
        .add("%T(%L", blockDrop, drop.itemId)
        .apply {
            if (drop.metadata != null) add(", metadata = %L", drop.metadata)
            if (drop.minCount != null) add(", minCount = %L", drop.minCount.literal())
            if (drop.maxCount != null) add(", maxCount = %L", drop.maxCount.literal())
        }
        .add(")")
        .build()

    private fun CodeBlock.Builder.addVariations(variations: List<VariationModel>) {
        if (variations.isEmpty()) return
        add("variations = listOf(\n⇥")
        variations.forEachIndexed { index, entry ->
            if (index > 0) add(",\n")
            add("%T(metadata = %L, displayName = %S", variation, entry.metadata, entry.displayName)
            if (entry.description != null) add(", description = %S", entry.description)
            add(")")
        }
        add(",\n⇤),\n")
    }

    private fun itemInitializer(item: ItemModel): CodeBlock = CodeBlock.builder()
        .add("%T(\n⇥", itemData)
        .add("id = %L,\n", item.id)
        .add("name = %S,\n", item.name)
        .add("displayName = %S,\n", item.displayName)
        .add("stackSize = %L,\n", item.stackSize)
        .apply {
            if (item.maxDurability != null) add("maxDurability = %L,\n", item.maxDurability)
            if (item.enchantCategories.isNotEmpty()) {
                add("enchantCategories = listOf(%L),\n", item.enchantCategories.stringList())
            }
            if (item.repairWith.isNotEmpty()) {
                add("repairWith = listOf(%L),\n", item.repairWith.stringList())
            }
            addVariations(item.variations)
        }
        .add("⇤)")
        .build()

    private fun entityInitializer(entity: EntityModel): CodeBlock = CodeBlock.builder()
        .add("%T(\n⇥", entityData)
        .add("id = %L,\n", entity.id)
        .add("name = %S,\n", entity.name)
        .add("displayName = %S,\n", entity.displayName)
        .add("type = %S,\n", entity.type)
        .apply {
            if (entity.internalId != null) add("internalId = %L,\n", entity.internalId)
            if (entity.category != null) add("category = %S,\n", entity.category)
            if (entity.width != null) add("width = %L,\n", entity.width.literal())
            if (entity.height != null) add("height = %L,\n", entity.height.literal())
            if (entity.metadataKeys.isNotEmpty()) {
                add("metadataKeys = listOf(%L),\n", entity.metadataKeys.stringList())
            }
        }
        .add("⇤)")
        .build()

    private fun fileSpec(fileName: String, minecraftVersion: String): FileSpec.Builder =
        FileSpec.builder(dataPackage, fileName)
            .addFileComment("Generated by the kmcprotocol Gradle plugin from PrismarineJS minecraft-data.\n")
            .addFileComment("Minecraft %L. Do not edit.\n", minecraftVersion)

    private fun List<String>.stringList(): String = joinToString { CodeBlock.of("%S", it).toString() }

    private fun Double?.literal(): String = when {
        this == null -> "null"
        isNaN() || isInfinite() -> "0.0"
        else -> toString()
    }

    private fun String?.literal(): String = if (this == null) "null" else CodeBlock.of("%S", this).toString()

    private fun String.boundingBoxEntry(): String = when (lowercase()) {
        "block" -> "Block"
        "empty" -> "Empty"
        else -> "Unknown"
    }

    private fun String.statePropertyEntry(): String = when (lowercase()) {
        "bool" -> "Bool"
        "int" -> "Int"
        else -> "Enum"
    }
}

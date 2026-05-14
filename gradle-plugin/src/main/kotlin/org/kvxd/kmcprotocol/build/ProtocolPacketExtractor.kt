package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ProtocolPacketExtractor(
    private val protocol: JsonObject,
    private val options: PacketGenerationOptions,
    private val naming: PacketNaming,
    private val typeResolver: ProtocolTypeResolver,
) {
    fun extract(): List<PacketModel> {
        val packets = mutableListOf<PacketModel>()
        for (state in protocolStates()) {
            val stateObject = protocol[state.sourceName]?.jsonObject ?: continue
            for (direction in protocolDirections(options.serverboundPrefix, options.clientboundPrefix)) {
                val directionTypes = stateObject[direction.sourceName]
                    ?.jsonObject
                    ?.get("types")
                    ?.jsonObject
                    ?: continue

                extractPacketEntries(directionTypes).forEach { packet ->
                    val fields = packetFields(resolvePacketDefinition(directionTypes, packet.typeName), directionTypes)
                    val className = naming.packetClassName(direction, packet.typeName)
                    packets += PacketModel(
                        state = state,
                        direction = direction,
                        id = packet.id,
                        sourceName = packet.name,
                        className = className,
                        packageName = "${options.packetPackage}.${state.packageName}.${direction.packageName}",
                        fields = fields,
                    )
                }
            }
        }
        return packets.sortedWith(compareBy({ it.state.sourceName }, { it.direction.sourceName }, { it.id }))
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
        }.sortedBy { it.id }
    }

    private fun resolvePacketDefinition(directionTypes: JsonObject, typeName: String): JsonElement? =
        directionTypes[typeName] ?: protocol["types"]?.jsonObject?.get(typeName)

    private fun packetFields(packetDefinition: JsonElement?, directionTypes: JsonObject): List<PacketField> {
        val definition = packetDefinition as? JsonArray ?: return emptyList()
        if (definition.getOrNull(0)?.jsonPrimitive?.contentOrNull != "container") return emptyList()

        val usedNames = mutableSetOf<String>()
        return definition.getOrNull(1)
            ?.jsonArray
            .orEmpty()
            .mapNotNull { fieldElement ->
                val field = fieldElement.jsonObject
                val sourceName = field["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val resolved = typeResolver.resolveType(
                    type = field["type"] ?: return@mapNotNull null,
                    aliases = directionTypes,
                    hint = naming.toPascalCase(sourceName),
                )
                if (resolved.skip) return@mapNotNull null

                PacketField(
                    sourceName = sourceName,
                    propertyName = naming.uniqueName(naming.toPropertyName(sourceName), usedNames),
                    typeName = resolved.typeName,
                    numberEncoding = resolved.numberEncoding,
                    protocolType = resolved.protocolType,
                )
            }
    }
}

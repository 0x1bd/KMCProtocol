package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun List<JsonElement>.fieldNamed(name: String): JsonObject? =
    firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == name }?.jsonObject

internal fun JsonObject.typeOptions(): JsonObject? =
    (this["type"] as? JsonArray)?.getOrNull(1)?.jsonObject

internal fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

internal val JsonPrimitive.safeContentOrNull: String?
    get() = runCatching { content }.getOrNull()
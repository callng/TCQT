package com.owo233.tcqt.utils.proto2json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

val GlobalJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    encodeDefaults = false
    prettyPrint = false
    coerceInputValues = true
}

val EmptyJsonObject = JsonObject(emptyMap())
val EmptyJsonArray = JsonArray(emptyList())
val EmptyJsonString = "".json

val String.asJson: JsonElement
    get() = GlobalJson.parseToJsonElement(this)

val String.asJsonObject: JsonObject
    get() = GlobalJson.parseToJsonElement(this) as? JsonObject
        ?: throw IllegalArgumentException("JSON value is not an object")

val Collection<*>.json: JsonArray
    get() = JsonArray(map(Any?::toJsonElement))

val Map<*, *>.json: JsonObject
    get() = JsonObject(entries.associate { (key, value) ->
        val stringKey = key as? String
            ?: throw IllegalArgumentException("JSON object key must be a String: $key")
        stringKey to value.toJsonElement()
    })

val Map<String, JsonElement>.jsonObject: JsonObject
    get() = JsonObject(this)

val Collection<JsonElement>.jsonArray: JsonArray
    get() = JsonArray(toList())

val Boolean.json: JsonPrimitive
    get() = JsonPrimitive(this)

val String.json: JsonPrimitive
    get() = JsonPrimitive(this)

val Number.json: JsonPrimitive
    get() = JsonPrimitive(this)

val JsonElement?.asString: String
    get() = requireNotNull(this).jsonPrimitive.content

val JsonElement?.asStringOrNull: String?
    get() = when (this) {
        null, JsonNull -> null
        else -> (this as? JsonPrimitive)?.content
    }

val JsonElement?.asInt: Int
    get() = requireNotNull(this).jsonPrimitive.int

val JsonElement?.asLong: Long
    get() = requireNotNull(this).jsonPrimitive.long

val JsonElement?.asLongOrNull: Long?
    get() = (this as? JsonPrimitive)?.longOrNull

val JsonElement?.asIntOrNull: Int?
    get() = (this as? JsonPrimitive)?.intOrNull

val JsonElement?.asBoolean: Boolean
    get() = requireNotNull(this).jsonPrimitive.boolean

val JsonElement?.asBooleanOrNull: Boolean?
    get() = (this as? JsonPrimitive)?.booleanOrNull

val JsonElement?.asJsonObject: JsonObject
    get() = this as? JsonObject ?: throw IllegalArgumentException("JSON value is not an object")

val JsonElement?.asJsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

val JsonElement?.asJsonArray: JsonArray
    get() = this as? JsonArray ?: throw IllegalArgumentException("JSON value is not an array")

val JsonElement?.asJsonArrayOrNull: JsonArray?
    get() = this as? JsonArray

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> json
    is String -> json
    is Number -> json
    is UInt -> toLong().json
    is ULong -> toString().json
    is Map<*, *> -> json
    is Collection<*> -> json
    is Array<*> -> asList().json
    is BooleanArray -> asList().json
    is ByteArray -> asList().json
    is ShortArray -> asList().json
    is IntArray -> asList().json
    is LongArray -> asList().json
    is FloatArray -> asList().json
    is DoubleArray -> asList().json
    else -> throw IllegalArgumentException("Unsupported JSON value: ${this::class.qualifiedName}")
}

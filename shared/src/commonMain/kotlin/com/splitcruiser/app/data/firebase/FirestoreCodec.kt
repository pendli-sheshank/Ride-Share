package com.splitcruiser.app.data.firebase

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Firestore's REST API does not speak plain JSON. Every field is wrapped in a single-key object
 * naming its type, and — the part that silently corrupts data if you miss it — **integers travel
 * as JSON strings**:
 *
 * ```json
 * { "seatsLeft": { "integerValue": "4" }, "costPerRider": { "doubleValue": 12.5 } }
 * ```
 *
 * Rather than hand-write a converter per model (the approach the old `toMap()` helpers took, which
 * had already drifted — five models had one and seven did not), this walks the `JsonElement` that
 * kotlinx-serialization produces. Every `@Serializable` model is therefore supported for free, and
 * adding a field to a model needs no change here.
 */
object FirestoreCodec {

    /**
     * `ignoreUnknownKeys` matters in both directions: documents written by the old Android
     * Firestore SDK may carry fields this version of the model no longer declares.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /** Serialises [value] into the `fields` map of a Firestore document. */
    fun <T> encode(serializer: SerializationStrategy<T>, value: T): JsonObject {
        val element = json.encodeToJsonElement(serializer, value)
        check(element is JsonObject) { "Only object-shaped models can be Firestore documents" }
        return JsonObject(element.mapValues { (_, field) -> toFirestoreValue(field) })
    }

    /** Reads a Firestore document's `fields` map back into [T]. A null or absent map yields defaults. */
    fun <T> decode(serializer: DeserializationStrategy<T>, fields: JsonObject?): T {
        val plain = JsonObject(
            (fields ?: JsonObject(emptyMap())).mapValues { (_, field) -> fromFirestoreValue(field) }
        )
        return json.decodeFromJsonElement(serializer, plain)
    }

    fun toFirestoreValue(element: JsonElement): JsonElement = when (element) {
        is JsonNull -> wrap("nullValue", JsonNull)
        is JsonPrimitive -> primitiveToValue(element)
        is JsonArray -> wrap(
            "arrayValue",
            JsonObject(mapOf("values" to JsonArray(element.map { toFirestoreValue(it) })))
        )
        is JsonObject -> wrap(
            "mapValue",
            JsonObject(mapOf("fields" to JsonObject(element.mapValues { toFirestoreValue(it.value) })))
        )
    }

    fun fromFirestoreValue(element: JsonElement): JsonElement {
        val wrapper = element as? JsonObject ?: return element
        val (type, raw) = wrapper.entries.firstOrNull() ?: return JsonNull
        return when (type) {
            "nullValue" -> JsonNull
            "booleanValue" -> JsonPrimitive(raw.jsonPrimitive.boolean)
            // Unquoted on the way back, so a field declared Int, Long, Float or Double all decode.
            "integerValue" -> JsonPrimitive(raw.jsonPrimitive.content.toLong())
            "doubleValue" -> JsonPrimitive(raw.jsonPrimitive.content.toDouble())
            "stringValue", "timestampValue", "referenceValue", "bytesValue" ->
                JsonPrimitive(raw.jsonPrimitive.content)
            "arrayValue" -> JsonArray(
                raw.jsonObject["values"]?.jsonArray.orEmpty().map { fromFirestoreValue(it) }
            )
            "mapValue" -> JsonObject(
                raw.jsonObject["fields"]?.jsonObject.orEmpty().mapValues { fromFirestoreValue(it.value) }
            )
            else -> JsonNull
        }
    }

    private fun primitiveToValue(primitive: JsonPrimitive): JsonElement = when {
        primitive.isString -> wrap("stringValue", JsonPrimitive(primitive.content))
        primitive.content == "true" || primitive.content == "false" ->
            wrap("booleanValue", JsonPrimitive(primitive.content.toBoolean()))
        // A Float or Double always serialises with a '.' or an exponent, so anything that parses
        // as a whole number here really was declared Int or Long.
        isIntegral(primitive.content) -> wrap("integerValue", JsonPrimitive(primitive.content))
        else -> wrap("doubleValue", JsonPrimitive(primitive.content.toDouble()))
    }

    private fun isIntegral(literal: String): Boolean = literal.toLongOrNull() != null

    private fun wrap(type: String, value: JsonElement): JsonObject = JsonObject(mapOf(type to value))

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
}

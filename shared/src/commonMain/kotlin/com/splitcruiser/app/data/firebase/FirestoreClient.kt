package com.splitcruiser.app.data.firebase

import com.splitcruiser.app.data.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Cloud Firestore over its REST API. Paths may include subcollections, e.g. `users/u1/blockedUsers`. */
internal class FirestoreClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val tokens: TokenProvider,
) {

    suspend fun <T> getDocument(
        path: String,
        id: String,
        deserializer: DeserializationStrategy<T>,
    ): T? {
        val response = authorized { token ->
            http.get("${config.firestoreBase}/${path.encodePath()}/${id.encodeURLPathPart()}") {
                bearer(token)
            }
        }
        // A missing document is a normal outcome, not an error.
        if (response.status == HttpStatusCode.NotFound) return null
        response.requireSuccess("Reading $path/$id")
        val document: JsonObject = response.body()
        return FirestoreCodec.decode(deserializer, document.fieldsOrEmpty())
    }

    /**
     * Full-document write, matching the `set()` semantics the app has always used.
     *
     * A bare PATCH *merges* — fields absent from the body keep their old server values. Sending an
     * `updateMask` naming every field is what turns that back into an overwrite.
     */
    suspend fun <T> setDocument(
        path: String,
        id: String,
        value: T,
        serializer: SerializationStrategy<T>,
    ) {
        val fields = FirestoreCodec.encode(serializer, value)
        writeFields(path, id, fields, mask = fields.keys)
    }

    /**
     * Partial write. The mask is not an optimisation — the security rules check
     * `affectedKeys().hasOnly([...])`, so a write that touches more fields than it claims is denied.
     */
    suspend fun updateFields(path: String, id: String, fields: JsonObject) {
        writeFields(path, id, fields, mask = fields.keys)
    }

    private suspend fun writeFields(
        path: String,
        id: String,
        fields: JsonObject,
        mask: Set<String>,
    ) {
        val maskQuery = mask.joinToString("&") { "updateMask.fieldPaths=${it.encodeURLParameter()}" }
        val url = buildString {
            append(config.firestoreBase).append('/').append(path.encodePath())
            append('/').append(id.encodeURLPathPart())
            if (maskQuery.isNotEmpty()) append('?').append(maskQuery)
        }
        authorized { token ->
            http.patch(url) {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("fields", fields) })
            }
        }.requireSuccess("Writing $path/$id")
    }

    suspend fun deleteDocument(path: String, id: String) {
        val response = authorized { token ->
            http.delete("${config.firestoreBase}/${path.encodePath()}/${id.encodeURLPathPart()}") {
                bearer(token)
            }
        }
        // Deleting something already gone is success as far as the caller is concerned.
        if (response.status == HttpStatusCode.NotFound) return
        response.requireSuccess("Deleting $path/$id")
    }

    /**
     * Runs a structured query.
     *
     * The response is an array whose entries each hold a `document`, except that an empty result set
     * comes back as a single entry carrying only `readTime` — hence the mapNotNull rather than an
     * index into the array.
     */
    suspend fun <T> runQuery(
        query: StructuredQuery,
        deserializer: DeserializationStrategy<T>,
    ): List<T> {
        val response = authorized { token ->
            http.post("${config.firestoreBase}:runQuery") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("structuredQuery", query.toJson()) })
            }
        }.requireSuccess("Querying ${query.collection}")

        val results: JsonArray = response.body()
        return results.mapNotNull { entry ->
            val document = entry.jsonObject["document"]?.jsonObject ?: return@mapNotNull null
            runCatching { FirestoreCodec.decode(deserializer, document.fieldsOrEmpty()) }.getOrNull()
        }
    }

    /**
     * Lists a collection's documents.
     *
     * Subcollections cannot be reached with `runQuery` from the database root — a structured query
     * names one collection id relative to a parent — so listing is how `users/{uid}/blockedUsers`
     * gets read.
     */
    suspend fun <T> listDocuments(
        path: String,
        deserializer: DeserializationStrategy<T>,
        pageSize: Int,
    ): List<T> {
        val response = authorized { token ->
            http.get("${config.firestoreBase}/${path.encodePath()}?pageSize=$pageSize") { bearer(token) }
        }
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        response.requireSuccess("Listing $path")
        val body: JsonObject = response.body()
        val documents = body["documents"]?.jsonArray ?: return emptyList()
        return documents.mapNotNull { entry ->
            runCatching {
                FirestoreCodec.decode(deserializer, entry.jsonObject.fieldsOrEmpty())
            }.getOrNull()
        }
    }

    /**
     * Applies several writes atomically.
     *
     * The old repository issued three or four independent `set()` calls for a single logical action,
     * so a failure part-way left, for example, a match accepted with the seat count never
     * decremented. A commit removes that whole class of inconsistency.
     */
    suspend fun commit(writes: List<FirestoreWrite>) {
        if (writes.isEmpty()) return
        authorized { token ->
            http.post("${config.firestoreBase.substringBeforeLast("/documents")}/documents:commit") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        putJsonArray("writes") { writes.forEach { add(it.toJson(config)) } }
                    }
                )
            }
        }.requireSuccess("Committing ${writes.size} writes")
    }

    /**
     * Adds the bearer token and, on a 401, refreshes once and retries. That single retry is what
     * makes a cold start after a week of not opening the app work.
     */
    private suspend fun authorized(
        block: suspend (token: String) -> io.ktor.client.statement.HttpResponse,
    ): io.ktor.client.statement.HttpResponse {
        val token = tokens.idToken()
            ?: throw SplitCruiserException("You need to be logged in.", code = "UNAUTHENTICATED")
        val first = block(token)
        if (first.status != HttpStatusCode.Unauthorized) return first
        val refreshed = tokens.forceRefresh(staleToken = token)
            ?: throw SplitCruiserException("Your session expired. Please log in again.", code = "TOKEN_EXPIRED")
        return block(refreshed)
    }

    /** Collection paths carry `/` between segments, so encode segment-wise. */
    private fun String.encodePath(): String =
        split('/').joinToString("/") { it.encodeURLPathPart() }
}

// --- Structured queries --------------------------------------------------------------------

internal enum class FilterOp(val wire: String) {
    Equal("EQUAL"),
    NotEqual("NOT_EQUAL"),
    GreaterThan("GREATER_THAN"),
    GreaterThanOrEqual("GREATER_THAN_OR_EQUAL"),
    LessThan("LESS_THAN"),
    LessThanOrEqual("LESS_THAN_OR_EQUAL"),
    ArrayContains("ARRAY_CONTAINS"),
}

internal data class FieldFilter(val field: String, val op: FilterOp, val value: JsonElement)

internal data class OrderBy(val field: String, val descending: Boolean)

internal data class StructuredQuery(
    val collection: String,
    val filters: List<FieldFilter> = emptyList(),
    val orderBy: List<OrderBy> = emptyList(),
    val limit: Int? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        putJsonArray("from") { add(buildJsonObject { put("collectionId", collection) }) }
        if (filters.isNotEmpty()) {
            putJsonObject("where") {
                if (filters.size == 1) {
                    put("fieldFilter", filters.single().toJson())
                } else {
                    putJsonObject("compositeFilter") {
                        put("op", "AND")
                        putJsonArray("filters") {
                            filters.forEach { add(buildJsonObject { put("fieldFilter", it.toJson()) }) }
                        }
                    }
                }
            }
        }
        if (orderBy.isNotEmpty()) {
            putJsonArray("orderBy") {
                orderBy.forEach { order ->
                    add(
                        buildJsonObject {
                            putJsonObject("field") { put("fieldPath", order.field) }
                            put("direction", if (order.descending) "DESCENDING" else "ASCENDING")
                        }
                    )
                }
            }
        }
        limit?.let { put("limit", it) }
    }

    private fun FieldFilter.toJson(): JsonObject = buildJsonObject {
        putJsonObject("field") { put("fieldPath", field) }
        put("op", op.wire)
        put("value", value)
    }
}

internal fun stringValue(value: String): JsonElement =
    buildJsonObject { put("stringValue", value) }

internal fun integerValue(value: Long): JsonElement =
    buildJsonObject { put("integerValue", value.toString()) }

internal fun booleanValue(value: Boolean): JsonElement =
    buildJsonObject { put("booleanValue", value) }

internal fun doubleValue(value: Double): JsonElement =
    buildJsonObject { put("doubleValue", value) }

internal fun arrayOfStrings(values: List<String>): JsonElement = buildJsonObject {
    putJsonObject("arrayValue") {
        putJsonArray("values") { values.forEach { add(buildJsonObject { put("stringValue", it) }) } }
    }
}

// --- Batched writes ------------------------------------------------------------------------

internal sealed interface FirestoreWrite {
    fun toJson(config: FirebaseConfig): JsonObject

    data class Update(
        val path: String,
        val id: String,
        val fields: JsonObject,
    ) : FirestoreWrite {
        override fun toJson(config: FirebaseConfig): JsonObject = buildJsonObject {
            putJsonObject("update") {
                put("name", config.documentName("$path/$id"))
                put("fields", fields)
            }
            putJsonObject("updateMask") {
                putJsonArray("fieldPaths") { fields.keys.forEach { add(JsonPrimitive(it)) } }
            }
        }
    }

    data class Delete(val path: String, val id: String) : FirestoreWrite {
        override fun toJson(config: FirebaseConfig): JsonObject = buildJsonObject {
            put("delete", config.documentName("$path/$id"))
        }
    }
}

/** Builds the `fields` map for a partial write, e.g. `buildFields { "status" to stringValue("full") }`. */
internal fun buildFields(vararg pairs: Pair<String, JsonElement>): JsonObject =
    JsonObject(pairs.toMap())

package com.ezragubbay.quire.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSource

/**
 * Reads NDJSON lines from [source] one at a time (blocking; call off the main thread), invoking [onEvent] for each
 * recognised event as soon as its line is complete. Never buffers the whole body. Unknown event types and malformed
 * lines are skipped (forward compatible). Returns true if a terminal event (`done`/`error`) was seen.
 * UTF-8 split across network chunks is safe: okio assembles whole lines before decoding.
 */
internal suspend fun parseChatStream(
    source: BufferedSource,
    onEvent: suspend (ChatEvent) -> Unit,
): Boolean {
    while (true) {
        val line = source.readUtf8Line() ?: return false
        val event = parseChatLine(line) ?: continue
        onEvent(event)
        if (event is ChatEvent.Done || event is ChatEvent.Error) return true
    }
}

internal fun parseChatLine(line: String): ChatEvent? =
    try {
        toEvent(QuireJson.parseToJsonElement(line).jsonObject)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: NoSuchElementException) {
        null
    }

private fun toEvent(obj: JsonObject): ChatEvent? =
    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
        "user" -> ChatEvent.User(message(obj))
        "delta" -> ChatEvent.Delta(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        "done" -> ChatEvent.Done(message(obj))
        "error" ->
            ChatEvent.Error(
                code = ErrorCode.fromWire(obj["code"]?.jsonPrimitive?.contentOrNull),
                kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull,
                message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        else -> null
    }

private fun message(obj: JsonObject): ChatMessageDto =
    QuireJson.decodeFromJsonElement(ChatMessageDto.serializer(), obj.getValue("message"))

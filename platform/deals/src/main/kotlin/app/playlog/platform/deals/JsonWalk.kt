package app.playlog.platform.deals

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shape-agnostic helpers for undocumented store JSON that changes without notice. */
internal object JsonWalk {
    /** Every object in [root], depth first. */
    fun objects(root: JsonElement): Sequence<JsonObject> = sequence {
        val stack = ArrayDeque<JsonElement>().apply { add(root) }
        while (stack.isNotEmpty()) {
            when (val e = stack.removeLast()) {
                is JsonObject -> {
                    yield(e)
                    e.values.reversed().forEach(stack::addLast)
                }
                is JsonArray -> e.reversed().forEach(stack::addLast)
                else -> Unit
            }
        }
    }

    fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf(String::isNotBlank)

    fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private val DIGITS = Regex("\\d+")
    private const val FULL = 100

    /** "-60%" → 60, "Save 25%" → 25. */
    fun percent(text: String?): Int? = text?.let { DIGITS.find(it)?.value?.toIntOrNull() }?.takeIf { it in 1 until FULL }
}

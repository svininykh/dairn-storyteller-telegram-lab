package org.dairn.storyteller.narration

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.dairn.steppe.GreatSteppeOmen
import org.dairn.storyteller.application.CharacterState

typealias OmenResult = GreatSteppeOmen

data class SceneContext(val bookId: String, val storyId: String, val sceneId: String, val text: String) {
    init { require(listOf(bookId, storyId, sceneId, text).all(String::isNotBlank)) }
}

data class HeroStateNarrative(val text: String)

sealed interface NarrationResult {
    data class Narrated(val narrative: HeroStateNarrative) : NarrationResult
    data class Failure(val message: String) : NarrationResult
}

fun interface HeroStateNarrator {
    fun generate(characterState: CharacterState, omenResult: OmenResult, sceneContext: SceneContext): NarrationResult
}

/** Application service deliberately has no session-store dependency. */
class HeroStateNarrationService(private val narrator: HeroStateNarrator) {
    fun generate(characterState: CharacterState, omenResult: OmenResult, sceneContext: SceneContext): NarrationResult =
        narrator.generate(characterState, omenResult, sceneContext)
}

data class OpenAiNarratorConfig(val apiKey: String, val model: String)

/** OpenAI boundary: immutable inputs in, non-authoritative proposal or explicit failure out. */
class OpenAiHeroStateNarrator(
    private val config: OpenAiNarratorConfig,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : HeroStateNarrator {
    override fun generate(characterState: CharacterState, omenResult: OmenResult, sceneContext: SceneContext): NarrationResult {
        val request = HttpRequest.newBuilder(URI.create(RESPONSES_URL))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(characterState, omenResult, sceneContext)))
            .build()
        val response = runCatching { httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrElse { return NarrationResult.Failure("OpenAI недоступен: ${it.message ?: "ошибка транспорта"}") }
        if (response.statusCode() !in 200..299) return NarrationResult.Failure("OpenAI вернул HTTP ${response.statusCode()}.")
        return parse(response.body())
    }

    internal fun requestBody(characterState: CharacterState, omen: OmenResult, scene: SceneContext): String = buildJsonObject {
        put("model", config.model); put("store", false)
        put("input", buildJsonArray {
            add(buildJsonObject {
                put("role", "developer")
                put("content", "DAIRN constraints: describe only supplied facts. Do not change state or omen, invent dice, mechanics, rules, canon, facts outside scene context, or player decisions. Output exactly 2 to 4 concise Russian sentences.")
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray { add(buildJsonObject { put("type", "input_text"); put("text", structuredInput(characterState, omen, scene)) }) })
            })
        })
        put("text", buildJsonObject { put("format", buildJsonObject {
            put("type", "json_schema"); put("name", "hero_state_narrative"); put("strict", true)
            put("schema", buildJsonObject {
                put("type", "object"); put("additionalProperties", false)
                put("properties", buildJsonObject { put("text", buildJsonObject { put("type", "string") }) })
                put("required", JsonArray(listOf(JsonPrimitive("text"))))
            })
        }) })
    }.toString()

    internal fun parse(responseBody: String): NarrationResult {
        val output = runCatching {
            val body = json.parseToJsonElement(responseBody).jsonObject
            body["output_text"]?.jsonPrimitive?.contentOrNull ?: body.outputText()
        }.getOrNull() ?: return NarrationResult.Failure("OpenAI не вернул текст повествования.")
        val text = runCatching { json.decodeFromString(Payload.serializer(), output).text.trim() }.getOrNull()
            ?: return NarrationResult.Failure("OpenAI вернул некорректный формат повествования.")
        val sentences = text.split(Regex("[.!?]+\\s*")).count(String::isNotBlank)
        return if (sentences in 2..4) NarrationResult.Narrated(HeroStateNarrative(text))
        else NarrationResult.Failure("Повествование должно содержать от 2 до 4 предложений.")
    }

    private fun structuredInput(state: CharacterState, omen: OmenResult, scene: SceneContext): String = buildJsonObject {
        put("character_state", buildJsonObject {
            put("name", state.name ?: "")
            put("fields", buildJsonArray { state.fields.forEach { field -> add(buildJsonObject { put("label", field.label); put("values", JsonArray(field.values.map(::JsonPrimitive))) }) } })
        })
        put("omen_result", buildJsonObject { put("roll", omen.roll); put("name", omen.name); put("description", omen.description) })
        put("scene_context", buildJsonObject { put("book_id", scene.bookId); put("story_id", scene.storyId); put("scene_id", scene.sceneId); put("text", scene.text) })
        put("requested_output", "A 2-4 sentence Russian narrative proposal only.")
    }.toString()

    private fun kotlinx.serialization.json.JsonObject.outputText(): String? = this["output"]?.jsonArray?.asSequence()
        ?.flatMap { it.jsonObject["content"]?.jsonArray?.asSequence() ?: emptySequence() }
        ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
        ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

    @Serializable private data class Payload(val text: String)

    companion object {
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        private val json = Json { ignoreUnknownKeys = true }
        fun fromEnvironment(environment: (String) -> String? = System::getenv): Result<OpenAiHeroStateNarrator> {
            val key = environment("OPENAI_API_KEY") ?: return Result.failure(IllegalStateException("OPENAI_API_KEY must be configured"))
            return Result.success(OpenAiHeroStateNarrator(OpenAiNarratorConfig(key, environment("OPENAI_NARRATOR_MODEL") ?: "gpt-4.1-mini")))
        }
    }
}

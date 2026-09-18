package org.dairn.storyteller.vision

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.dairn.storyteller.application.DicePhoto
import org.dairn.storyteller.application.DiceRecognition
import org.dairn.storyteller.application.DiceVisionRecognizer
import org.dairn.storyteller.application.RecognizedDice

/** OpenAI Responses API adapter. It proposes a die value; application code requires user confirmation. */
class OpenAiDiceVisionRecognizer(
    private val apiKey: String,
    private val model: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : DiceVisionRecognizer {
    override fun recognize(photo: DicePhoto): DiceRecognition {
        println(
            "OpenAI Vision request started: model=$model, contentType=${photo.contentType}, imageBytes=${photo.bytes.size}",
        )
        val request = HttpRequest.newBuilder(URI.create(RESPONSES_URL))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(photo)))
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: Exception) {
            System.err.println(
                "OpenAI Vision transport error: ${exception::class.simpleName}: ${exception.message ?: "no message"}",
            )
            throw exception
        }
        if (response.statusCode() !in 200..299) {
            val requestId = response.headers().firstValue("x-request-id").orElse("unavailable")
            System.err.println(
                "OpenAI Vision API error: status=${response.statusCode()}, code=${errorCode(response.body())}, requestId=$requestId",
            )
            throw IllegalStateException("OpenAI Vision request failed with HTTP ${response.statusCode()}.")
        }
        val parsed = parseResponse(response.body())
        logRecognition(parsed)
        return parsed.recognition
    }

    private fun errorCode(responseBody: String): String = runCatching {
        json.parseToJsonElement(responseBody).jsonObject["error"]
            ?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull ?: "unavailable"
    }.getOrDefault("unavailable")

    private fun logRecognition(parsed: ParsedRecognition) {
        when (val recognition = parsed.recognition) {
            is DiceRecognition.Recognized -> println(
                "OpenAI Vision response: recognized die=${recognition.dice.die}, value=${recognition.dice.value}, confidence=${recognition.dice.confidence}",
            )
            DiceRecognition.Uncertain -> println(
                "OpenAI Vision response: uncertain, reason=${parsed.reason}, die=${parsed.result?.die ?: "unavailable"}, value=${parsed.result?.value ?: "unavailable"}, confidence=${parsed.result?.confidence ?: "unavailable"}",
            )
        }
    }

    internal fun parse(responseBody: String): DiceRecognition = parseResponse(responseBody).recognition

    private fun parseResponse(responseBody: String): ParsedRecognition {
        val response = json.parseToJsonElement(responseBody).jsonObject
        val outputText = response["output_text"]?.jsonPrimitive?.contentOrNull
            ?: response.outputText()
            ?: return ParsedRecognition(DiceRecognition.Uncertain, "missing_output_text")
        val result = runCatching { json.decodeFromString(VisionResult.serializer(), outputText) }.getOrNull()
            ?: return ParsedRecognition(DiceRecognition.Uncertain, "invalid_structured_output")
        if (result.status != "recognized") {
            return ParsedRecognition(DiceRecognition.Uncertain, "status_${result.status}", result)
        }
        if (result.die != "D20") {
            return ParsedRecognition(DiceRecognition.Uncertain, "die_${result.die}", result)
        }
        if (result.value == null || result.confidence == null) {
            return ParsedRecognition(DiceRecognition.Uncertain, "missing_value_or_confidence", result)
        }
        return ParsedRecognition(
            DiceRecognition.Recognized(RecognizedDice(result.die, result.value, result.confidence)),
            "recognized",
            result,
        )
    }

    private fun requestBody(photo: DicePhoto): String {
        val dataUrl = "data:${photo.contentType};base64,${Base64.getEncoder().encodeToString(photo.bytes)}"
        return buildJsonObject {
            put("model", model)
            put("store", false)
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put(
                                "text",
                                "Inspect the single physical die in this image. A d20 normally shows several numbered faces at once; that alone is not uncertainty. If it is clearly a twenty-sided die, read the numeral on its upward-facing, most prominent face (not a side face and not the largest numeral in the image). Return recognized when that numeral is legible. Do not infer a value if the die type or upward-facing numeral is genuinely obscured.",
                            )
                        })
                        add(buildJsonObject {
                            put("type", "input_image")
                            put("image_url", dataUrl)
                            put("detail", "high")
                        })
                    })
                })
            })
            put("text", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("name", "dice_recognition")
                    put("strict", true)
                    put("schema", buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", false)
                        put("properties", buildJsonObject {
                            put("status", schemaField("string", "recognized", "uncertain"))
                            put("die", schemaField("string", "D20", "OTHER", "UNKNOWN"))
                            put("value", buildJsonObject { put("type", strings("integer", "null")) })
                            put("confidence", buildJsonObject {
                                put("type", strings("number", "null"))
                                put("minimum", 0)
                                put("maximum", 1)
                            })
                        })
                        put("required", strings("status", "die", "value", "confidence"))
                    })
                })
            })
        }.toString()
    }

    private fun schemaField(type: String, vararg values: String) = buildJsonObject {
        put("type", type)
        put("enum", strings(*values))
    }

    private fun strings(vararg values: String) = JsonArray(values.map(::JsonPrimitive))

    /**
     * The REST response represents generated text in output message content.
     * `output_text` is an SDK convenience property and is absent from raw JSON.
     */
    private fun kotlinx.serialization.json.JsonObject.outputText(): String? = this["output"]
        ?.jsonArray
        ?.asSequence()
        ?.flatMap { outputItem ->
            outputItem.jsonObject["content"]?.jsonArray?.asSequence() ?: emptySequence<JsonElement>()
        }
        ?.firstOrNull { content ->
            content.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text"
        }
        ?.jsonObject
        ?.get("text")
        ?.jsonPrimitive
        ?.contentOrNull

    @Serializable
    private data class VisionResult(
        val status: String,
        val die: String,
        val value: Int? = null,
        val confidence: Double? = null,
    )

    private data class ParsedRecognition(
        val recognition: DiceRecognition,
        val reason: String,
        val result: VisionResult? = null,
    )

    companion object {
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
        private val json = Json { ignoreUnknownKeys = true }

        fun fromEnvironment(): OpenAiDiceVisionRecognizer = OpenAiDiceVisionRecognizer(
            apiKey = requireNotNull(System.getenv("OPENAI_API_KEY")) { "OPENAI_API_KEY must be configured" },
            model = System.getenv("OPENAI_VISION_MODEL") ?: "gpt-4.1-mini",
        )
    }
}

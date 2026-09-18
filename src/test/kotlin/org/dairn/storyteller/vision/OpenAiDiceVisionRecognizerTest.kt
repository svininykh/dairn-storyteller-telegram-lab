package org.dairn.storyteller.vision

import org.dairn.storyteller.application.DiceRecognition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenAiDiceVisionRecognizerTest {
    private val recognizer = OpenAiDiceVisionRecognizer("not-a-real-key", "test-model")

    @Test
    fun `parses a structured d20 proposal`() {
        val response = "{\"output_text\":\"{\\\"status\\\":\\\"recognized\\\",\\\"die\\\":\\\"D20\\\",\\\"value\\\":17,\\\"confidence\\\":0.97}\"}"
        val recognition = assertIs<DiceRecognition.Recognized>(recognizer.parse(response))

        assertEquals(17, recognition.dice.value)
        assertEquals(0.97, recognition.dice.confidence)
    }

    @Test
    fun `parses structured output from the raw Responses API output array`() {
        val response = """
            {"output":[{"type":"message","content":[
              {"type":"output_text","text":"{\"status\":\"recognized\",\"die\":\"D20\",\"value\":17,\"confidence\":0.97}"}
            ]}]}
        """.trimIndent()

        val recognition = assertIs<DiceRecognition.Recognized>(recognizer.parse(response))

        assertEquals(17, recognition.dice.value)
        assertEquals(0.97, recognition.dice.confidence)
    }

    @Test
    fun `treats malformed or uncertain results as uncertain`() {
        assertIs<DiceRecognition.Uncertain>(recognizer.parse("{\"output_text\":\"not json\"}"))
        val uncertain = "{\"output_text\":\"{\\\"status\\\":\\\"uncertain\\\",\\\"die\\\":\\\"UNKNOWN\\\",\\\"value\\\":null,\\\"confidence\\\":null}\"}"
        assertIs<DiceRecognition.Uncertain>(recognizer.parse(uncertain))
    }
}

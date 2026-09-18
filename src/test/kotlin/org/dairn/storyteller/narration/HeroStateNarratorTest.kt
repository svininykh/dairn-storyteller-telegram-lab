package org.dairn.storyteller.narration

import org.dairn.core.Dice
import org.dairn.core.DiceRoll
import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.steppe.GreatSteppeModule
import org.dairn.storyteller.application.CharacterState
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HeroStateNarratorTest {
    private val state = GreatSteppeCharacterGenerator().generate(
        GreatSteppeGenerationInput(name = "Айбике"), Dice { count, sides -> DiceRoll(List(count) { 1 }, sides) },
    )
    private val omen = GreatSteppeModule.resolveOmen(7)
    private val scene = SceneContext("battles-of-the-great-steppe", "first-trial", "arrival", "Айбике подходит к зимнему аулу.")

    @Test fun `application passes immutable state omen and scene context to narrator`() {
        var received: Triple<CharacterState, OmenResult, SceneContext>? = null
        val service = HeroStateNarrationService(HeroStateNarrator { character, omenResult, context ->
            received = Triple(character, omenResult, context)
            NarrationResult.Narrated(HeroStateNarrative("Ветер крепчает над аулом. Знамение остаётся неясным."))
        })

        val result = assertIs<NarrationResult.Narrated>(service.generate(state, omen, scene))

        assertEquals(Triple(state, omen, scene), received)
        assertEquals("Ветер крепчает над аулом. Знамение остаётся неясным.", result.narrative.text)
    }

    @Test fun `structured response becomes a short hero state narrative`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))

        val result = assertIs<NarrationResult.Narrated>(narrator.parse("{\"output_text\":\"{\\\"text\\\":\\\"Ветер крепчает. Над степью сгущается тревога. Связь знамений с прошлым Айбике остаётся неясной.\\\"}\"}"))

        assertTrue(result.narrative.text.contains("Айбике"))
    }

    @Test fun `rejects response outside two to four sentences`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))

        assertIs<NarrationResult.Failure>(narrator.parse("{\"output_text\":\"{\\\"text\\\":\\\"Только одно предложение.\\\"}\"}"))
    }

    @Test fun `request separates state omen scene and provider independent constraints`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))
        val body = narrator.requestBody(state, omen, scene)

        assertTrue(body.contains("immutable authoritative facts"))
        assertTrue(body.contains("subjects, participants, and events"))
        assertTrue(body.contains("current action or physical state"))
        assertTrue(body.contains("selected field's subject and scope"))
        assertTrue(body.contains("internal hero state from the situation"))
        assertTrue(body.contains("hero action, decision, intention, or next step"))
        assertTrue(body.contains("causal relationships"))
        assertTrue(body.contains("merge independent CharacterState fields"))
        assertTrue(body.contains("uncertain"))
        assertTrue(body.contains("human reader"))
        assertTrue(body.contains("character_state"))
        assertTrue(body.contains("omen_result"))
        assertTrue(body.contains("scene_context"))
    }

    @Test fun `Aibike and Pale Rider fixture preserves the semantic regression categories`() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/narration/aibike-pale-rider.json"))
            .readBytes().toString(StandardCharsets.UTF_8)
        val root = Json.parseToJsonElement(fixture).jsonObject
        val forbidden = root.getValue("must_not_assert_as_fact").jsonArray.map { requireNotNull(it.jsonPrimitive.contentOrNull) }

        assertEquals(20, requireNotNull(root.getValue("resolved_omen").jsonObject.getValue("roll").jsonPrimitive.contentOrNull).toInt())
        assertTrue(requireNotNull(root.getValue("resolved_omen").jsonObject.getValue("facts").jsonPrimitive.contentOrNull).contains("Children in different settlements"))
        assertTrue(forbidden.any { it.contains("holding") }) // fabricated state
        assertTrue(forbidden.any { it.contains("dreams") }) // altered Omen participant
        assertTrue(forbidden.any { it.contains("decides") }) // player agency
        assertTrue(forbidden.any { it.contains("represents") }) // fabricated relationship
        assertTrue(forbidden.any { it.contains("connected") }) // fabricated causality
        assertTrue(forbidden.any { it.contains("bloodstained") }) // attribute scope loss
        assertTrue(forbidden.any { it.contains("feels fear") }) // invented feeling
        assertTrue(forbidden.any { it.contains("remembers") }) // invented memory
        assertTrue(forbidden.any { it.contains("suspects") }) // invented suspicion
    }

    @Test fun `serializing narration inputs does not mutate authoritative snapshots`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))
        val before = Triple(state, omen, scene)

        narrator.requestBody(state, omen, scene)

        assertEquals(before, Triple(state, omen, scene))
    }

    @Test fun `missing OpenAI configuration is explicit`() {
        val result = OpenAiHeroStateNarrator.fromEnvironment { null }

        assertTrue(result.isFailure)
        assertEquals("OPENAI_API_KEY must be configured", result.exceptionOrNull()?.message)
    }

    @Test fun `narrator failure is returned explicitly`() {
        val service = HeroStateNarrationService(HeroStateNarrator { _, _, _ -> NarrationResult.Failure("OpenAI недоступен") })

        assertEquals(NarrationResult.Failure("OpenAI недоступен"), service.generate(state, omen, scene))
    }
}

package org.dairn.storyteller.narration

import org.dairn.core.Dice
import org.dairn.core.DiceRoll
import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.steppe.GreatSteppeModule
import org.dairn.storyteller.application.CharacterState
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
            NarrationResult.Narrated(HeroStateNarrative("Ветер крепчает. Айбике осматривает аул."))
        })

        val result = assertIs<NarrationResult.Narrated>(service.generate(state, omen, scene))

        assertEquals(Triple(state, omen, scene), received)
        assertEquals("Ветер крепчает. Айбике осматривает аул.", result.narrative.text)
    }

    @Test fun `structured response becomes a short hero state narrative`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))

        val result = assertIs<NarrationResult.Narrated>(narrator.parse("{\"output_text\":\"{\\\"text\\\":\\\"Ветер крепчает. Айбике осматривает аул. Знамение не даёт ей покоя.\\\"}\"}"))

        assertTrue(result.narrative.text.contains("Айбике"))
    }

    @Test fun `rejects response outside two to four sentences`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))

        assertIs<NarrationResult.Failure>(narrator.parse("{\"output_text\":\"{\\\"text\\\":\\\"Только одно предложение.\\\"}\"}"))
    }

    @Test fun `request separates state omen scene and DAIRN constraints`() {
        val narrator = OpenAiHeroStateNarrator(OpenAiNarratorConfig("test-key", "test-model"))
        val body = narrator.requestBody(state, omen, scene)

        assertTrue(body.contains("DAIRN constraints"))
        assertTrue(body.contains("character_state"))
        assertTrue(body.contains("omen_result"))
        assertTrue(body.contains("scene_context"))
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

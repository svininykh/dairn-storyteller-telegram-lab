package org.dairn.storyteller.application

import org.dairn.core.Dice
import org.dairn.core.DiceRoll
import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.storyteller.narration.HeroStateNarrative
import org.dairn.storyteller.narration.HeroStateNarrator
import org.dairn.storyteller.narration.NarrationResult
import org.dairn.storyteller.narration.SceneContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryTellerApplicationTest {
    private val sessions = InMemorySessionStore()
    private val vision = FakeVision()
    private val application = StoryTellerApplication(sessions, D20Roller { 20 }, diceVisionRecognizer = vision)

    @Test
    fun `start initializes an in-memory session and presents the demo character`() {
        val responses = application.start(CHAT_ID)

        assertIs<StoryTellerResponse.Start>(responses.first())
        assertIs<StoryTellerResponse.ChooseRoll>(responses.last())
        assertEquals(SessionStep.CHOOSING_ROLL, sessions.get(CHAT_ID)?.step)
    }

    @Test
    fun `generated engine character is stored and shown before Omen choices`() {
        val state = GreatSteppeCharacterGenerator().generate(
            GreatSteppeGenerationInput("Айбике"),
            Dice { count, sides -> DiceRoll(List(count) { 1 }, sides) },
        )

        val profile = assertIs<StoryTellerResponse.CharacterProfile>(application.startGeneratedHero(CHAT_ID, state))

        assertEquals(state, profile.characterState)
        assertEquals(state, sessions.get(CHAT_ID)?.characterState)
        assertEquals(SessionStep.VIEWING_CHARACTER_PROFILE, sessions.get(CHAT_ID)?.step)
        assertIs<StoryTellerResponse.Error>(application.chooseRoll(CHAT_ID, RollChoice.DIGITAL))
        assertIs<StoryTellerResponse.ChooseRoll>(application.continueAfterCharacterProfile(CHAT_ID))
        assertEquals(state, sessions.get(CHAT_ID)?.characterState)
    }

    @Test
    fun `digital d20 is in range and resolves through the Omen flow`() {
        application.start(CHAT_ID)

        val result = assertIs<StoryTellerResponse.OmenResolved>(application.chooseRoll(CHAT_ID, RollChoice.DIGITAL))

        assertEquals(20, result.roll)
        assertEquals(20, result.omen.roll)
        assertEquals(SessionStep.OMEN_RESOLVED, sessions.get(CHAT_ID)?.step)
    }

    @Test
    fun `resolved Omen narrates with the stored generated character and independent scene context`() {
        val state = generatedCharacter()
        var received: Triple<CharacterState, org.dairn.steppe.GreatSteppeOmen, SceneContext>? = null
        val narrator = HeroStateNarrator { character, omen, scene ->
            received = Triple(character, omen, scene)
            NarrationResult.Narrated(HeroStateNarrative("Ветер приносит знамение. Что оно значит для Айбике, остаётся неясным."))
        }
        val application = StoryTellerApplication(
            InMemorySessionStore(), D20Roller { 7 }, heroStateNarrator = narrator,
            sceneContext = SceneContext("book-1", "story-1", "scene-1", "Степь молчит."),
        )

        application.startGeneratedHero(CHAT_ID, state)
        application.continueAfterCharacterProfile(CHAT_ID)
        val result = assertIs<StoryTellerResponse.OmenResolved>(application.chooseRoll(CHAT_ID, RollChoice.DIGITAL))

        assertEquals(state, received?.first)
        assertEquals(result.omen, received?.second)
        assertEquals("scene-1", received?.third?.sceneId)
        assertIs<NarrationResult.Narrated>(result.narration)
        assertEquals(state, application.session(CHAT_ID)?.characterState)
        assertEquals(result.omen, application.session(CHAT_ID)?.omen)
    }

    @Test
    fun `narrator failure preserves the resolved Omen and generated character`() {
        val state = generatedCharacter()
        val application = StoryTellerApplication(
            InMemorySessionStore(), D20Roller { 7 },
            heroStateNarrator = HeroStateNarrator { _, _, _ -> NarrationResult.Failure("OpenAI недоступен") },
        )

        application.startGeneratedHero(CHAT_ID, state)
        application.continueAfterCharacterProfile(CHAT_ID)
        val result = assertIs<StoryTellerResponse.OmenResolved>(application.chooseRoll(CHAT_ID, RollChoice.DIGITAL))

        assertIs<NarrationResult.Failure>(result.narration)
        assertEquals(state, application.session(CHAT_ID)?.characterState)
        assertEquals(result.omen, application.session(CHAT_ID)?.omen)
        assertEquals(SessionStep.OMEN_RESOLVED, application.session(CHAT_ID)?.step)
    }

    @Test
    fun `random digital d20 stays within the valid range`() {
        repeat(200) {
            assertTrue(RandomD20Roller.roll() in 1..20)
        }
    }

    @Test
    fun `manual d20 resolves through the Omen flow`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.MANUAL)

        val result = assertIs<StoryTellerResponse.OmenResolved>(application.submitManualRoll(CHAT_ID, "7"))

        assertEquals(7, result.roll)
        assertEquals(7, result.omen.roll)
    }

    @Test
    fun `invalid manual d20 does not resolve an omen`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.MANUAL)

        assertIs<StoryTellerResponse.Error>(application.submitManualRoll(CHAT_ID, "21"))
        assertNull(sessions.get(CHAT_ID)?.omen)
        assertEquals(SessionStep.WAITING_FOR_MANUAL_ROLL, sessions.get(CHAT_ID)?.step)
    }

    @Test
    fun `recognized photo does not resolve an omen before confirmation`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.PHOTO)
        vision.result = DiceRecognition.Recognized(RecognizedDice("D20", 17, 0.98))

        val result = assertIs<StoryTellerResponse.PhotoRecognized>(application.submitPhoto(CHAT_ID, PHOTO))

        assertEquals(17, result.dice.value)
        assertNull(sessions.get(CHAT_ID)?.omen)
        assertEquals(SessionStep.WAITING_FOR_PHOTO_CONFIRMATION, sessions.get(CHAT_ID)?.step)
    }

    @Test
    fun `confirmed recognized photo resolves the proposed d20 through Omen flow`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.PHOTO)
        vision.result = DiceRecognition.Recognized(RecognizedDice("D20", 17, 0.98))
        application.submitPhoto(CHAT_ID, PHOTO)

        val result = assertIs<StoryTellerResponse.OmenResolved>(application.confirmPhotoRoll(CHAT_ID))

        assertEquals(17, result.roll)
        assertEquals(17, result.omen.roll)
    }

    @Test
    fun `repeating photo does not resolve an omen`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.PHOTO)
        vision.result = DiceRecognition.Recognized(RecognizedDice("D20", 9, 0.9))
        application.submitPhoto(CHAT_ID, PHOTO)

        assertIs<StoryTellerResponse.RequestPhoto>(application.repeatPhoto(CHAT_ID))
        assertNull(sessions.get(CHAT_ID)?.omen)
        assertEquals(SessionStep.WAITING_FOR_PHOTO, sessions.get(CHAT_ID)?.step)
    }

    @Test
    fun `photo manual fallback preserves existing manual path`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.PHOTO)

        assertIs<StoryTellerResponse.RequestManualRoll>(application.enterManualRoll(CHAT_ID))
        val result = assertIs<StoryTellerResponse.OmenResolved>(application.submitManualRoll(CHAT_ID, "7"))

        assertEquals(7, result.roll)
    }

    @Test
    fun `uncertain photo does not resolve an omen`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.PHOTO)
        vision.result = DiceRecognition.Uncertain

        assertIs<StoryTellerResponse.PhotoUncertain>(application.submitPhoto(CHAT_ID, PHOTO))
        assertNull(sessions.get(CHAT_ID)?.omen)
    }

    @Test
    fun `vision failure is treated as uncertain and does not resolve an omen`() {
        val failingApplication = StoryTellerApplication(
            InMemorySessionStore(),
            D20Roller { 1 },
            diceVisionRecognizer = DiceVisionRecognizer { error("Vision unavailable") },
        )
        failingApplication.start(CHAT_ID)
        failingApplication.chooseRoll(CHAT_ID, RollChoice.PHOTO)

        assertIs<StoryTellerResponse.PhotoUncertain>(failingApplication.submitPhoto(CHAT_ID, PHOTO))
        assertNull(failingApplication.session(CHAT_ID)?.omen)
    }

    @Test
    fun `out of range recognized photo cannot be confirmed`() {
        application.start(CHAT_ID)
        application.chooseRoll(CHAT_ID, RollChoice.PHOTO)
        vision.result = DiceRecognition.Recognized(RecognizedDice("D20", 21, 0.9))

        assertIs<StoryTellerResponse.PhotoUncertain>(application.submitPhoto(CHAT_ID, PHOTO))
        assertIs<StoryTellerResponse.Error>(application.confirmPhotoRoll(CHAT_ID))
        assertNull(sessions.get(CHAT_ID)?.omen)
    }

    private class FakeVision(var result: DiceRecognition = DiceRecognition.Uncertain) : DiceVisionRecognizer {
        override fun recognize(photo: DicePhoto): DiceRecognition = result
    }

    private fun generatedCharacter() = GreatSteppeCharacterGenerator().generate(
        GreatSteppeGenerationInput("Айбике"),
        Dice { count, sides -> DiceRoll(List(count) { 1 }, sides) },
    )

    private companion object {
        const val CHAT_ID = 42L
        val PHOTO = DicePhoto(byteArrayOf(1), "image/jpeg")
    }
}

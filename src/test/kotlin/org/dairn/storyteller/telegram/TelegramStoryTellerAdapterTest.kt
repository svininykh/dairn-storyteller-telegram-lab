package org.dairn.storyteller.telegram

import org.dairn.core.Dice
import org.dairn.core.DiceRoll
import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.storyteller.application.D20Roller
import org.dairn.storyteller.application.DicePhoto
import org.dairn.storyteller.application.DiceRecognition
import org.dairn.storyteller.application.DiceVisionRecognizer
import org.dairn.storyteller.application.InMemorySessionStore
import org.dairn.storyteller.application.RecognizedDice
import org.dairn.storyteller.application.StoryTellerApplication
import org.dairn.storyteller.narration.HeroStateNarrative
import org.dairn.storyteller.narration.HeroStateNarrator
import org.dairn.storyteller.narration.NarrationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramStoryTellerAdapterTest {
    private val adapter = TelegramStoryTellerAdapter(
        StoryTellerApplication(InMemorySessionStore(), D20Roller { 1 }, diceVisionRecognizer = FakeVision()),
    )

    @Test
    fun `start renders all three d20 options`() {
        val views = adapter.onStart(99L)
        val callbacks = views.last().keyboard.flatten().map(TelegramButton::callbackData)

        assertEquals(
            listOf(
                TelegramStoryTellerAdapter.DIGITAL_CALLBACK,
                TelegramStoryTellerAdapter.PHOTO_CALLBACK,
                TelegramStoryTellerAdapter.MANUAL_CALLBACK,
            ),
            callbacks,
        )
    }

    @Test
    fun `recognized photo renders confirmation controls`() {
        adapter.onStart(99L)
        adapter.onCallback(99L, TelegramStoryTellerAdapter.PHOTO_CALLBACK)

        val view = adapter.onPhoto(99L, DicePhoto(byteArrayOf(1), "image/jpeg")).single()
        val callbacks = view.keyboard.flatten().map(TelegramButton::callbackData)

        assertTrue(view.text.contains("Я распознал: 17"))
        assertEquals(
            listOf(
                TelegramStoryTellerAdapter.CONFIRM_PHOTO_CALLBACK,
                TelegramStoryTellerAdapter.REPEAT_PHOTO_CALLBACK,
                TelegramStoryTellerAdapter.MANUAL_FROM_PHOTO_CALLBACK,
            ),
            callbacks,
        )
    }

    @Test
    fun `generated profile is rendered before Omen controls`() {
        val state = GreatSteppeCharacterGenerator().generate(
            GreatSteppeGenerationInput("Айбике"),
            Dice { count, sides -> DiceRoll(List(count) { 1 }, sides) },
        )

        val profile = adapter.onHeroStarted(99L, state).single()
        val omen = adapter.onCallback(99L, TelegramStoryTellerAdapter.CONTINUE_PROFILE_CALLBACK).single()

        assertTrue(profile.text.contains("Жизненный путь"))
        assertEquals(TelegramStoryTellerAdapter.CONTINUE_PROFILE_CALLBACK, profile.keyboard.flatten().single().callbackData)
        assertEquals(TelegramStoryTellerAdapter.DIGITAL_CALLBACK, omen.keyboard.flatten().first().callbackData)
    }

    @Test
    fun `Omen result is rendered before the narrator output`() {
        val narrator = HeroStateNarrator { _, _, _ ->
            NarrationResult.Narrated(HeroStateNarrative("Ветер меняется. Айбике слышит зов степи."))
        }
        val narratorAdapter = TelegramStoryTellerAdapter(
            StoryTellerApplication(InMemorySessionStore(), D20Roller { 7 }, heroStateNarrator = narrator),
        )
        val state = GreatSteppeCharacterGenerator().generate(
            GreatSteppeGenerationInput("Айбике"),
            Dice { count, sides -> DiceRoll(List(count) { 1 }, sides) },
        )

        narratorAdapter.onHeroStarted(99L, state)
        narratorAdapter.onCallback(99L, TelegramStoryTellerAdapter.CONTINUE_PROFILE_CALLBACK)
        val views = narratorAdapter.onCallback(99L, TelegramStoryTellerAdapter.DIGITAL_CALLBACK)

        assertEquals(2, views.size)
        assertTrue(views[0].text.startsWith("d20: 7"))
        assertEquals("Ветер меняется. Айбике слышит зов степи.", views[1].text)
    }

    private class FakeVision : DiceVisionRecognizer {
        override fun recognize(photo: DicePhoto): DiceRecognition =
            DiceRecognition.Recognized(RecognizedDice("D20", 17, 0.95))
    }
}

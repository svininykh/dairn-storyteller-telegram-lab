package org.dairn.storyteller.telegram

import org.dairn.storyteller.application.D20Roller
import org.dairn.storyteller.application.DicePhoto
import org.dairn.storyteller.application.DiceRecognition
import org.dairn.storyteller.application.DiceVisionRecognizer
import org.dairn.storyteller.application.InMemorySessionStore
import org.dairn.storyteller.application.RecognizedDice
import org.dairn.storyteller.application.StoryTellerApplication
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

        val view = adapter.onPhoto(99L, DicePhoto(byteArrayOf(1), "image/jpeg"))
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

    private class FakeVision : DiceVisionRecognizer {
        override fun recognize(photo: DicePhoto): DiceRecognition =
            DiceRecognition.Recognized(RecognizedDice("D20", 17, 0.95))
    }
}

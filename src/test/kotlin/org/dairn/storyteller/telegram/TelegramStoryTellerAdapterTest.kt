package org.dairn.storyteller.telegram

import org.dairn.storyteller.application.D20Roller
import org.dairn.storyteller.application.InMemorySessionStore
import org.dairn.storyteller.application.StoryTellerApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramStoryTellerAdapterTest {
    private val adapter = TelegramStoryTellerAdapter(
        StoryTellerApplication(InMemorySessionStore(), D20Roller { 1 }),
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
    fun `photo callback says the path is deferred`() {
        adapter.onStart(99L)

        val view = adapter.onCallback(99L, TelegramStoryTellerAdapter.PHOTO_CALLBACK)

        assertTrue(view.text.contains("следующим этапом"))
    }
}

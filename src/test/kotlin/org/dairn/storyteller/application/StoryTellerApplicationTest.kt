package org.dairn.storyteller.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryTellerApplicationTest {
    private val sessions = InMemorySessionStore()
    private val application = StoryTellerApplication(sessions, D20Roller { 20 })

    @Test
    fun `start initializes an in-memory session and presents the demo character`() {
        val responses = application.start(CHAT_ID)

        assertIs<StoryTellerResponse.Start>(responses.first())
        assertIs<StoryTellerResponse.ChooseRoll>(responses.last())
        assertEquals(SessionStep.CHOOSING_ROLL, sessions.get(CHAT_ID)?.step)
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
    fun `photo path is explicitly deferred`() {
        application.start(CHAT_ID)

        assertIs<StoryTellerResponse.PhotoComingNext>(application.chooseRoll(CHAT_ID, RollChoice.PHOTO))
        assertNotNull(sessions.get(CHAT_ID))
    }

    private companion object {
        const val CHAT_ID = 42L
    }
}

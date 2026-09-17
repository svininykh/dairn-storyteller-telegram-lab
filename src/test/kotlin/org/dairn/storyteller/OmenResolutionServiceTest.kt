package org.dairn.storyteller

import org.dairn.steppe.GreatSteppeModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OmenResolutionServiceTest {
    private val service = OmenResolutionService()

    @Test
    fun `every confirmed d20 result is resolved by the external DAIRN Engine`() {
        (1..20).forEach { roll ->
            assertEquals(GreatSteppeModule.resolveOmen(roll), service.resolveConfirmedD20(roll))
        }
    }

    @Test
    fun `rolls outside d20 range are rejected explicitly`() {
        listOf(-1, 0, 21).forEach { roll ->
            assertFailsWith<IllegalArgumentException> { service.resolveConfirmedD20(roll) }
        }
    }
}

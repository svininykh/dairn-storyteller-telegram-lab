package org.dairn.storyteller

import org.dairn.steppe.GreatSteppeModule
import org.dairn.steppe.GreatSteppeOmen
import org.dairn.steppe.GreatSteppeOmenResolver

/** Thin StoryTeller boundary around the external DAIRN Great Steppe rules module. */
class OmenResolutionService(
    private val resolver: GreatSteppeOmenResolver = GreatSteppeModule,
) {
    fun resolveConfirmedD20(roll: Int): GreatSteppeOmen {
        require(roll in 1..20) { "Confirmed d20 roll must be between 1 and 20" }
        return resolver.resolveOmen(roll)
    }
}

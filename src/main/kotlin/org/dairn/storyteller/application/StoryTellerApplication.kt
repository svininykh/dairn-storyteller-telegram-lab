package org.dairn.storyteller.application

import org.dairn.steppe.GreatSteppeOmen
import org.dairn.storyteller.OmenResolutionService
import kotlin.random.Random

data class DemoCharacter(val name: String, val description: String)

enum class SessionStep { CHOOSING_ROLL, WAITING_FOR_MANUAL_ROLL, OMEN_RESOLVED }

data class StoryTellerSession(
    val chatId: Long,
    val character: DemoCharacter,
    val step: SessionStep,
    val omen: GreatSteppeOmen? = null,
)

class InMemorySessionStore {
    private val sessions = java.util.concurrent.ConcurrentHashMap<Long, StoryTellerSession>()

    fun get(chatId: Long): StoryTellerSession? = sessions[chatId]

    fun save(session: StoryTellerSession) {
        sessions[session.chatId] = session
    }
}

fun interface D20Roller {
    fun roll(): Int
}

object RandomD20Roller : D20Roller {
    override fun roll(): Int = Random.nextInt(1, 21)
}

enum class RollChoice { DIGITAL, PHOTO, MANUAL }

sealed interface StoryTellerResponse {
    data class Start(val character: DemoCharacter) : StoryTellerResponse
    data object ChooseRoll : StoryTellerResponse
    data object RequestManualRoll : StoryTellerResponse
    data object PhotoComingNext : StoryTellerResponse
    data class OmenResolved(val roll: Int, val omen: GreatSteppeOmen) : StoryTellerResponse
    data class Error(val message: String) : StoryTellerResponse
}

class StoryTellerApplication(
    private val sessionStore: InMemorySessionStore = InMemorySessionStore(),
    private val d20Roller: D20Roller = RandomD20Roller,
    private val omenResolutionService: OmenResolutionService = OmenResolutionService(),
) {
    fun start(chatId: Long): List<StoryTellerResponse> {
        sessionStore.save(StoryTellerSession(chatId, DEMO_CHARACTER, SessionStep.CHOOSING_ROLL))
        return listOf(StoryTellerResponse.Start(DEMO_CHARACTER), StoryTellerResponse.ChooseRoll)
    }

    fun chooseRoll(chatId: Long, choice: RollChoice): StoryTellerResponse = when (choice) {
        RollChoice.DIGITAL -> resolve(chatId, d20Roller.roll())
        RollChoice.MANUAL -> transitionToManualInput(chatId)
        RollChoice.PHOTO -> StoryTellerResponse.PhotoComingNext
    }

    fun submitManualRoll(chatId: Long, text: String): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (session.step != SessionStep.WAITING_FOR_MANUAL_ROLL) {
            return StoryTellerResponse.Error("Сначала выберите ручной ввод d20.")
        }
        val roll = text.trim().toIntOrNull()
            ?: return StoryTellerResponse.Error("Введите целое значение d20 от 1 до 20.")
        return resolve(chatId, roll)
    }

    fun session(chatId: Long): StoryTellerSession? = sessionStore.get(chatId)

    private fun transitionToManualInput(chatId: Long): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        sessionStore.save(session.copy(step = SessionStep.WAITING_FOR_MANUAL_ROLL))
        return StoryTellerResponse.RequestManualRoll
    }

    private fun resolve(chatId: Long, roll: Int): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (roll !in 1..20) {
            return StoryTellerResponse.Error("Подтверждённый d20 должен быть от 1 до 20.")
        }
        val omen = omenResolutionService.resolveConfirmedD20(roll)
        sessionStore.save(session.copy(step = SessionStep.OMEN_RESOLVED, omen = omen))
        return StoryTellerResponse.OmenResolved(roll, omen)
    }

    private companion object {
        val DEMO_CHARACTER = DemoCharacter(
            name = "Айбек",
            description = "Готовый персонаж DAIRN: путник Великой степи.",
        )
    }
}

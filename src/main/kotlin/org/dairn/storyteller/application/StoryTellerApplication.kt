package org.dairn.storyteller.application

import org.dairn.steppe.GreatSteppeOmen
import org.dairn.storyteller.OmenResolutionService
import org.dairn.storyteller.narration.HeroStateNarrator
import org.dairn.storyteller.narration.NarrationResult
import org.dairn.storyteller.narration.OpenAiHeroStateNarrator
import org.dairn.storyteller.narration.SceneContext
import kotlin.random.Random

data class DemoCharacter(val name: String, val description: String)

enum class SessionStep {
    VIEWING_CHARACTER_PROFILE,
    CHOOSING_ROLL,
    WAITING_FOR_MANUAL_ROLL,
    WAITING_FOR_PHOTO,
    WAITING_FOR_PHOTO_CONFIRMATION,
    OMEN_RESOLVED,
}

data class DicePhoto(val bytes: ByteArray, val contentType: String)

data class RecognizedDice(val die: String, val value: Int, val confidence: Double)

sealed interface DiceRecognition {
    data class Recognized(val dice: RecognizedDice) : DiceRecognition
    data object Uncertain : DiceRecognition
}

fun interface DiceVisionRecognizer {
    fun recognize(photo: DicePhoto): DiceRecognition
}

data class StoryTellerSession(
    val chatId: Long,
    val character: DemoCharacter,
    val step: SessionStep,
    val characterState: CharacterState? = null,
    val pendingDice: RecognizedDice? = null,
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
    data class CharacterProfile(val characterState: CharacterState) : StoryTellerResponse
    data object ChooseRoll : StoryTellerResponse
    data object RequestManualRoll : StoryTellerResponse
    data object RequestPhoto : StoryTellerResponse
    data class PhotoRecognized(val dice: RecognizedDice) : StoryTellerResponse
    data object PhotoUncertain : StoryTellerResponse
    data class OmenResolved(
        val roll: Int,
        val omen: GreatSteppeOmen,
        val narration: NarrationResult? = null,
    ) : StoryTellerResponse
    data class Error(val message: String) : StoryTellerResponse
}

class StoryTellerApplication(
    private val sessionStore: InMemorySessionStore = InMemorySessionStore(),
    private val d20Roller: D20Roller = RandomD20Roller,
    private val omenResolutionService: OmenResolutionService = OmenResolutionService(),
    private val diceVisionRecognizer: DiceVisionRecognizer = DiceVisionRecognizer { DiceRecognition.Uncertain },
    private val heroStateNarrator: HeroStateNarrator? = OpenAiHeroStateNarrator.fromEnvironment().getOrNull(),
    private val sceneContext: SceneContext = DEMO_SCENE_CONTEXT,
) {
    fun start(chatId: Long, character: DemoCharacter = DEMO_CHARACTER): List<StoryTellerResponse> {
        sessionStore.save(StoryTellerSession(chatId, character, SessionStep.CHOOSING_ROLL))
        return listOf(StoryTellerResponse.Start(character), StoryTellerResponse.ChooseRoll)
    }

    fun startGeneratedHero(chatId: Long, characterState: CharacterState): StoryTellerResponse {
        val name = requireNotNull(characterState.name) { "Engine generated a character without a name" }
        sessionStore.save(
            StoryTellerSession(
                chatId = chatId,
                character = DemoCharacter(name, "Герой DAIRN создан движком."),
                step = SessionStep.VIEWING_CHARACTER_PROFILE,
                characterState = characterState,
            ),
        )
        return StoryTellerResponse.CharacterProfile(characterState)
    }

    fun continueAfterCharacterProfile(chatId: Long): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (session.step != SessionStep.VIEWING_CHARACTER_PROFILE || session.characterState == null) {
            return StoryTellerResponse.Error("Сначала создайте и просмотрите профиль героя.")
        }
        sessionStore.save(session.copy(step = SessionStep.CHOOSING_ROLL))
        return StoryTellerResponse.ChooseRoll
    }

    fun chooseRoll(chatId: Long, choice: RollChoice): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (session.step != SessionStep.CHOOSING_ROLL) {
            return StoryTellerResponse.Error("Сначала завершите предыдущий этап.")
        }
        return when (choice) {
            RollChoice.DIGITAL -> resolve(chatId, d20Roller.roll())
            RollChoice.MANUAL -> transitionToManualInput(chatId)
            RollChoice.PHOTO -> transitionToPhotoInput(chatId)
        }
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

    fun submitPhoto(chatId: Long, photo: DicePhoto): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (session.step != SessionStep.WAITING_FOR_PHOTO) {
            return StoryTellerResponse.Error("Сначала выберите распознавание фотографии d20.")
        }
        val recognition = runCatching { diceVisionRecognizer.recognize(photo) }
            .getOrElse { DiceRecognition.Uncertain }
        val dice = (recognition as? DiceRecognition.Recognized)?.dice
        if (dice == null || dice.die != "D20" || dice.value !in 1..20) {
            sessionStore.save(session.copy(pendingDice = null))
            return StoryTellerResponse.PhotoUncertain
        }
        sessionStore.save(session.copy(step = SessionStep.WAITING_FOR_PHOTO_CONFIRMATION, pendingDice = dice))
        return StoryTellerResponse.PhotoRecognized(dice)
    }

    fun confirmPhotoRoll(chatId: Long): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (session.step != SessionStep.WAITING_FOR_PHOTO_CONFIRMATION || session.pendingDice == null) {
            return StoryTellerResponse.Error("Нет значения d20 для подтверждения.")
        }
        return resolve(chatId, session.pendingDice.value)
    }

    fun repeatPhoto(chatId: Long): StoryTellerResponse = transitionToPhotoInput(chatId)

    fun enterManualRoll(chatId: Long): StoryTellerResponse = transitionToManualInput(chatId)

    fun session(chatId: Long): StoryTellerSession? = sessionStore.get(chatId)

    private fun transitionToManualInput(chatId: Long): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        sessionStore.save(session.copy(step = SessionStep.WAITING_FOR_MANUAL_ROLL, pendingDice = null))
        return StoryTellerResponse.RequestManualRoll
    }

    private fun transitionToPhotoInput(chatId: Long): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        sessionStore.save(session.copy(step = SessionStep.WAITING_FOR_PHOTO, pendingDice = null))
        return StoryTellerResponse.RequestPhoto
    }

    private fun resolve(chatId: Long, roll: Int): StoryTellerResponse {
        val session = sessionStore.get(chatId)
            ?: return StoryTellerResponse.Error("Сначала отправьте /start.")
        if (roll !in 1..20) {
            return StoryTellerResponse.Error("Подтверждённый d20 должен быть от 1 до 20.")
        }
        val omen = omenResolutionService.resolveConfirmedD20(roll)
        val resolvedSession = session.copy(step = SessionStep.OMEN_RESOLVED, pendingDice = null, omen = omen)
        sessionStore.save(resolvedSession)
        val narration = resolvedSession.characterState?.let { characterState ->
            val narrator = heroStateNarrator
                ?: return@let NarrationResult.Failure("Повествование сейчас недоступно.")
            runCatching { narrator.generate(characterState, omen, sceneContext) }
                .getOrElse { NarrationResult.Failure("Не удалось подготовить повествование.") }
        }
        return StoryTellerResponse.OmenResolved(roll, omen, narration)
    }

    private companion object {
        val DEMO_CHARACTER = DemoCharacter(
            name = "Айбек",
            description = "Готовый персонаж DAIRN: путник Великой степи.",
        )
        val DEMO_SCENE_CONTEXT = SceneContext(
            bookId = "demo-book",
            storyId = "demo-story",
            sceneId = "opening",
            text = "Путник стоит в Великой степи перед началом пути.",
        )
    }
}

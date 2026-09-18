package org.dairn.storyteller.telegram

import org.dairn.storyteller.application.RollChoice
import org.dairn.storyteller.application.DicePhoto
import org.dairn.storyteller.application.CharacterState
import org.dairn.storyteller.application.StoryTellerApplication
import org.dairn.storyteller.application.StoryTellerResponse
import org.dairn.storyteller.narration.NarrationResult

data class TelegramView(val text: String, val keyboard: List<List<TelegramButton>> = emptyList())
data class TelegramButton(val label: String, val callbackData: String)

class TelegramStoryTellerAdapter(private val application: StoryTellerApplication) {
    fun onStart(chatId: Long): List<TelegramView> = application.start(chatId).map(::render)

    fun onHeroStarted(chatId: Long, characterState: CharacterState): List<TelegramView> =
        listOf(render(application.startGeneratedHero(chatId, characterState)))

    fun onCallback(chatId: Long, data: String): List<TelegramView> = renderAll(
        when (data) {
            DIGITAL_CALLBACK -> application.chooseRoll(chatId, RollChoice.DIGITAL)
            PHOTO_CALLBACK -> application.chooseRoll(chatId, RollChoice.PHOTO)
            MANUAL_CALLBACK -> application.chooseRoll(chatId, RollChoice.MANUAL)
            CONFIRM_PHOTO_CALLBACK -> application.confirmPhotoRoll(chatId)
            REPEAT_PHOTO_CALLBACK -> application.repeatPhoto(chatId)
            MANUAL_FROM_PHOTO_CALLBACK -> application.enterManualRoll(chatId)
            CONTINUE_PROFILE_CALLBACK -> application.continueAfterCharacterProfile(chatId)
            else -> StoryTellerResponse.Error("Неизвестное действие.")
        },
    )

    fun onText(chatId: Long, text: String): List<TelegramView> = renderAll(application.submitManualRoll(chatId, text))

    fun onPhoto(chatId: Long, photo: DicePhoto): List<TelegramView> = renderAll(application.submitPhoto(chatId, photo))

    private fun renderAll(response: StoryTellerResponse): List<TelegramView> = when (response) {
        is StoryTellerResponse.OmenResolved -> buildList {
            add(render(response))
            when (val narration = response.narration) {
                is NarrationResult.Narrated -> add(TelegramView(narration.narrative.text))
                is NarrationResult.Failure -> add(TelegramView("Не удалось подготовить повествование: ${narration.message}"))
                null -> Unit
            }
        }
        else -> listOf(render(response))
    }

    private fun render(response: StoryTellerResponse): TelegramView = when (response) {
        is StoryTellerResponse.Start -> TelegramView("${response.character.name}\n${response.character.description}")
        is StoryTellerResponse.CharacterProfile -> TelegramView(
            text = response.characterState.fields.joinToString(
                prefix = "Профиль героя\n\n",
                separator = "\n",
            ) { field -> "${field.label}: ${field.values.joinToString(", ")}" },
            keyboard = listOf(listOf(TelegramButton("Продолжить к Знамению", CONTINUE_PROFILE_CALLBACK))),
        )
        StoryTellerResponse.ChooseRoll -> TelegramView(
            text = "Определи своё Знамение — брось d20",
            keyboard = listOf(
                listOf(TelegramButton("🎲 цифровой бросок", DIGITAL_CALLBACK)),
                listOf(TelegramButton("📷 фотография d20", PHOTO_CALLBACK)),
                listOf(TelegramButton("⌨ ручной ввод", MANUAL_CALLBACK)),
            ),
        )
        StoryTellerResponse.RequestManualRoll -> TelegramView("Введите подтверждённое значение d20 от 1 до 20.")
        StoryTellerResponse.RequestPhoto -> TelegramView("Пришлите фотографию d20. Я предложу значение, а вы подтвердите его перед определением Знамения.")
        is StoryTellerResponse.PhotoRecognized -> TelegramView(
            text = "Я распознал: ${response.dice.value} (d20, уверенность: ${"%.0f".format(response.dice.confidence * 100)}%).",
            keyboard = listOf(
                listOf(TelegramButton("Подтвердить", CONFIRM_PHOTO_CALLBACK)),
                listOf(TelegramButton("Повторить", REPEAT_PHOTO_CALLBACK)),
                listOf(TelegramButton("Ввести вручную", MANUAL_FROM_PHOTO_CALLBACK)),
            ),
        )
        StoryTellerResponse.PhotoUncertain -> TelegramView(
            text = "Не удалось уверенно распознать d20. Знамение не определено.",
            keyboard = listOf(
                listOf(TelegramButton("Повторить", REPEAT_PHOTO_CALLBACK)),
                listOf(TelegramButton("Ввести вручную", MANUAL_FROM_PHOTO_CALLBACK)),
            ),
        )
        is StoryTellerResponse.OmenResolved -> TelegramView(
            "d20: ${response.roll}\nЗнамение: ${response.omen.name}\n${response.omen.description}",
        )
        is StoryTellerResponse.Error -> TelegramView(response.message)
    }

    companion object {
        const val DIGITAL_CALLBACK = "omen:digital"
        const val PHOTO_CALLBACK = "omen:photo"
        const val MANUAL_CALLBACK = "omen:manual"
        const val CONFIRM_PHOTO_CALLBACK = "omen:photo:confirm"
        const val REPEAT_PHOTO_CALLBACK = "omen:photo:repeat"
        const val MANUAL_FROM_PHOTO_CALLBACK = "omen:photo:manual"
        const val CONTINUE_PROFILE_CALLBACK = "hero:profile:continue"
    }
}

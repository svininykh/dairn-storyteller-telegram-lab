package org.dairn.storyteller.telegram

import org.dairn.storyteller.application.RollChoice
import org.dairn.storyteller.application.StoryTellerApplication
import org.dairn.storyteller.application.StoryTellerResponse

data class TelegramView(val text: String, val keyboard: List<List<TelegramButton>> = emptyList())
data class TelegramButton(val label: String, val callbackData: String)

class TelegramStoryTellerAdapter(private val application: StoryTellerApplication) {
    fun onStart(chatId: Long): List<TelegramView> = application.start(chatId).map(::render)

    fun onCallback(chatId: Long, data: String): TelegramView = render(
        when (data) {
            DIGITAL_CALLBACK -> application.chooseRoll(chatId, RollChoice.DIGITAL)
            PHOTO_CALLBACK -> application.chooseRoll(chatId, RollChoice.PHOTO)
            MANUAL_CALLBACK -> application.chooseRoll(chatId, RollChoice.MANUAL)
            else -> StoryTellerResponse.Error("Неизвестное действие.")
        },
    )

    fun onText(chatId: Long, text: String): TelegramView = render(application.submitManualRoll(chatId, text))

    private fun render(response: StoryTellerResponse): TelegramView = when (response) {
        is StoryTellerResponse.Start -> TelegramView("${response.character.name}\n${response.character.description}")
        StoryTellerResponse.ChooseRoll -> TelegramView(
            text = "Определи своё Знамение — брось d20",
            keyboard = listOf(
                listOf(TelegramButton("🎲 цифровой бросок", DIGITAL_CALLBACK)),
                listOf(TelegramButton("📷 фотография d20", PHOTO_CALLBACK)),
                listOf(TelegramButton("⌨ ручной ввод", MANUAL_CALLBACK)),
            ),
        )
        StoryTellerResponse.RequestManualRoll -> TelegramView("Введите подтверждённое значение d20 от 1 до 20.")
        StoryTellerResponse.PhotoComingNext -> TelegramView("Распознавание фотографии d20 будет реализовано следующим этапом.")
        is StoryTellerResponse.OmenResolved -> TelegramView(
            "d20: ${response.roll}\nЗнамение: ${response.omen.name}\n${response.omen.description}",
        )
        is StoryTellerResponse.Error -> TelegramView(response.message)
    }

    companion object {
        const val DIGITAL_CALLBACK = "omen:digital"
        const val PHOTO_CALLBACK = "omen:photo"
        const val MANUAL_CALLBACK = "omen:manual"
    }
}

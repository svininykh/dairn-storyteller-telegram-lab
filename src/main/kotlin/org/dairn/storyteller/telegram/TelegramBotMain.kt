package org.dairn.storyteller.telegram

import org.dairn.storyteller.application.StoryTellerApplication
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient

fun main() {
    val token = requireNotNull(System.getenv("TELEGRAM_BOT_TOKEN")) {
        "TELEGRAM_BOT_TOKEN must be configured"
    }
    val telegramClient = OkHttpTelegramClient(token)
    val adapter = TelegramStoryTellerAdapter(StoryTellerApplication())
    TelegramBotsLongPollingApplication().use { application ->
        application.registerBot(token, StoryTellerUpdateConsumer(telegramClient, adapter))
        Thread.currentThread().join()
    }
}

class StoryTellerUpdateConsumer(
    private val telegramClient: TelegramClient,
    private val adapter: TelegramStoryTellerAdapter,
) : LongPollingSingleThreadUpdateConsumer {
    override fun consume(update: Update) {
        when {
            update.hasMessage() && update.message.hasText() -> {
                val chatId = update.message.chatId
                val views = if (update.message.text == "/start") adapter.onStart(chatId)
                else listOf(adapter.onText(chatId, update.message.text))
                views.forEach { send(chatId, it) }
            }
            update.hasCallbackQuery() -> {
                val callback = update.callbackQuery
                val chatId = callback.message.chatId
                send(chatId, adapter.onCallback(chatId, callback.data))
            }
        }
    }

    private fun send(chatId: Long, view: TelegramView) {
        val message = SendMessage(chatId.toString(), view.text)
        if (view.keyboard.isNotEmpty()) {
            message.replyMarkup = InlineKeyboardMarkup(
                view.keyboard.map { row ->
                    InlineKeyboardRow(
                        row.map { button ->
                            InlineKeyboardButton(button.label).apply { callbackData = button.callbackData }
                        },
                    )
                },
            )
        }
        telegramClient.execute(message)
    }
}

package org.dairn.storyteller.telegram

import org.dairn.storyteller.application.StoryTellerApplication
import org.dairn.storyteller.application.DicePhoto
import org.dairn.storyteller.application.BookStartupApplication
import org.dairn.storyteller.application.EngineHeroStateInitializer
import org.dairn.storyteller.application.HeroInitializationService
import org.dairn.storyteller.application.InMemoryBookGameSessionStore
import org.dairn.storyteller.book.DairnBookPackage
import org.dairn.storyteller.vision.OpenAiDiceVisionRecognizer
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

fun main() {
    println("DAIRN StoryTeller Telegram Lab v${ApplicationVersion.value}")
    val token = requireNotNull(System.getenv("TELEGRAM_BOT_TOKEN")) {
        "TELEGRAM_BOT_TOKEN must be configured"
    }
    val telegramClient = OkHttpTelegramClient(token)
    val adapter = TelegramStoryTellerAdapter(StoryTellerApplication(diceVisionRecognizer = OpenAiDiceVisionRecognizer.fromEnvironment()))
    val heroStartup = TelegramHeroStartupAdapter(
        BookStartupApplication(InMemoryBookGameSessionStore(), HeroInitializationService(EngineHeroStateInitializer())),
        { DairnBookPackage.read(Path.of(requireNotNull(System.getenv("DAIRN_BOOK_PATH")) { "DAIRN_BOOK_PATH must be configured" })) },
        adapter,
    )
    TelegramBotsLongPollingApplication().use { application ->
        application.registerBot(token, StoryTellerUpdateConsumer(telegramClient, adapter, heroStartup))
        Thread.currentThread().join()
    }
}

object ApplicationVersion {
    val value: String by lazy {
        checkNotNull(ApplicationVersion::class.java.classLoader.getResourceAsStream("application.properties")) {
            "Application version metadata is missing"
        }.use { input ->
            java.util.Properties().apply { load(input) }.getProperty("application.version")
        }
    }
}

class StoryTellerUpdateConsumer(
    private val telegramClient: TelegramClient,
    private val adapter: TelegramStoryTellerAdapter,
    private val heroStartup: TelegramHeroStartupAdapter,
    private val token: String = requireNotNull(System.getenv("TELEGRAM_BOT_TOKEN")),
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : LongPollingSingleThreadUpdateConsumer {
    override fun consume(update: Update) {
        when {
            update.hasMessage() && update.message.hasText() -> {
                val chatId = update.message.chatId
                val views = if (update.message.text == "/start") heroStartup.onStart(chatId)
                else heroStartup.onText(chatId, update.message.text) ?: adapter.onText(chatId, update.message.text)
                views.forEach { send(chatId, it) }
            }
            update.hasMessage() && update.message.hasPhoto() -> {
                val chatId = update.message.chatId
                println("Telegram photo received: chatId=$chatId")
                adapter.onPhoto(chatId, downloadLargestPhoto(update.message.photo.last().fileId)).forEach { send(chatId, it) }
            }
            update.hasCallbackQuery() -> {
                val callback = update.callbackQuery
                val chatId = callback.message.chatId
                val views = if (heroStartup.handlesCallback(callback.data)) heroStartup.onCallback(chatId, callback.data)
                else adapter.onCallback(chatId, callback.data)
                views.forEach { send(chatId, it) }
            }
        }
    }

    private fun downloadLargestPhoto(fileId: String): DicePhoto {
        val file = telegramClient.execute(GetFile(fileId))
        val body = httpClient.send(
            HttpRequest.newBuilder(URI.create("https://api.telegram.org/file/bot$token/${file.filePath}")).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        ).body()
        return DicePhoto(body, "image/jpeg")
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

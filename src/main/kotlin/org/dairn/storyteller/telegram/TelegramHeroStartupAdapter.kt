package org.dairn.storyteller.telegram

import java.util.concurrent.ConcurrentHashMap
import org.dairn.storyteller.application.BookStartupApplication
import org.dairn.storyteller.application.HeroStartupResult
import org.dairn.storyteller.book.DairnBookPackage

/** Telegram-only presentation and correlation for the transport-independent hero startup flow. */
class TelegramHeroStartupAdapter(
    private val application: BookStartupApplication,
    private val bookSource: () -> DairnBookPackage.PackageInfo,
    private val storyTeller: TelegramStoryTellerAdapter,
) {
    private val states = ConcurrentHashMap<Long, State>()

    fun onStart(chatId: Long): List<TelegramView> = try {
        val book = bookSource()
        states.remove(chatId)
        render(chatId, book, application.begin(chatId, book))
    } catch (exception: Exception) {
        listOf(TelegramView("Не удалось загрузить книгу: ${exception.message ?: "неизвестная ошибка"}"))
    }

    fun onCallback(chatId: Long, data: String): List<TelegramView> {
        val state = states[chatId] as? State.AwaitingHeroChoice
            ?: return listOf(TelegramView("Выбор героя больше не активен. Отправьте /start."))
        val heroId = data.removePrefix(HERO_CALLBACK_PREFIX)
        if (!data.startsWith(HERO_CALLBACK_PREFIX) || heroId.isBlank()) {
            return listOf(TelegramView("Неизвестный выбор героя."))
        }
        return render(chatId, state.book, application.begin(chatId, state.book, heroId))
    }

    fun onText(chatId: Long, text: String): List<TelegramView>? {
        val state = states[chatId] as? State.AwaitingHeroName ?: return null
        return render(chatId, state.book, application.provideHeroName(chatId, state.book, text)
        )
    }

    fun handlesCallback(data: String): Boolean = data.startsWith(HERO_CALLBACK_PREFIX)

    private fun render(chatId: Long, book: DairnBookPackage.PackageInfo, result: HeroStartupResult): List<TelegramView> = when (result) {
        is HeroStartupResult.SelectHero -> {
            states[chatId] = State.AwaitingHeroChoice(book)
            listOf(TelegramView(
                "Выберите героя:",
                result.heroes.map { hero -> listOf(TelegramButton(hero.name ?: hero.id, "$HERO_CALLBACK_PREFIX${hero.id}")) },
            ))
        }
        is HeroStartupResult.RequestHeroName -> {
            states[chatId] = State.AwaitingHeroName(book)
            listOf(TelegramView("Введите имя героя:"))
        }
        is HeroStartupResult.Started -> {
            states.remove(chatId)
            storyTeller.onHeroStarted(chatId, result.characterState)
        }
        HeroStartupResult.CreationRequired -> {
            states.remove(chatId)
            listOf(TelegramView("Для этой книги нужен поток создания героя Engine; он пока недоступен."))
        }
        is HeroStartupResult.Error -> listOf(TelegramView(result.message))
    }

    private sealed interface State {
        data class AwaitingHeroChoice(val book: DairnBookPackage.PackageInfo) : State
        data class AwaitingHeroName(val book: DairnBookPackage.PackageInfo) : State
    }

    companion object {
        const val HERO_CALLBACK_PREFIX = "hero:select:"
    }
}

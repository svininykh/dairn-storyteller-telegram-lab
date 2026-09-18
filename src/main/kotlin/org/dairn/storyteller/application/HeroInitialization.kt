package org.dairn.storyteller.application

import java.util.concurrent.ConcurrentHashMap
import org.dairn.steppe.GreatSteppeCharacter
import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.storyteller.book.DairnBookPackage

/** Engine-owned rule-defined state; StoryTeller only retains the returned value. */
typealias CharacterState = GreatSteppeCharacter

data class HeroIdentity(val id: String? = null, val name: String)

fun interface HeroStateInitializer {
    fun initialize(hero: HeroIdentity): CharacterState
}

/** Adapter to the current DAIRN Great Steppe engine character-generation API. */
class EngineHeroStateInitializer(
    private val generator: GreatSteppeCharacterGenerator = GreatSteppeCharacterGenerator(),
) : HeroStateInitializer {
    override fun initialize(hero: HeroIdentity): CharacterState =
        generator.generate(GreatSteppeGenerationInput(name = hero.name))
}

sealed interface HeroStartupResult {
    data class SelectHero(val heroes: List<DairnBookPackage.BookHero>) : HeroStartupResult
    data class RequestHeroName(val heroId: String) : HeroStartupResult
    /** The external Engine creation flow owns this branch and is intentionally not implemented here. */
    data object CreationRequired : HeroStartupResult
    data class Started(val hero: HeroIdentity, val characterState: CharacterState) : HeroStartupResult
    data class Error(val message: String) : HeroStartupResult
}

class HeroInitializationService(private val initializer: HeroStateInitializer) {
    fun begin(book: DairnBookPackage.PackageInfo, selectedHeroId: String? = null): HeroStartupResult {
        if (book.heroes.isEmpty()) return HeroStartupResult.CreationRequired
        if (selectedHeroId == null) {
            return if (book.heroes.size == 1) startOrRequestName(book.heroes.single()) else HeroStartupResult.SelectHero(book.heroes)
        }
        val hero = book.heroes.singleOrNull { it.id == selectedHeroId }
            ?: return HeroStartupResult.Error("Выбранный герой отсутствует в книге.")
        return startOrRequestName(hero)
    }

    fun provideHeroName(book: DairnBookPackage.PackageInfo, selectedHeroId: String, name: String): HeroStartupResult {
        val bookHero = book.heroes.singleOrNull { it.id == selectedHeroId }
            ?: return HeroStartupResult.Error("Выбранный герой отсутствует в книге.")
        if (bookHero.name != null) return HeroStartupResult.Error("У выбранного героя уже есть имя.")
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return HeroStartupResult.Error("Укажите имя героя.")
        return start(HeroIdentity(id = bookHero.id, name = normalizedName))
    }

    private fun startOrRequestName(bookHero: DairnBookPackage.BookHero): HeroStartupResult =
        bookHero.name?.let { start(HeroIdentity(id = bookHero.id, name = it)) }
            ?: HeroStartupResult.RequestHeroName(bookHero.id)

    private fun start(hero: HeroIdentity): HeroStartupResult =
        try {
            HeroStartupResult.Started(hero, initializer.initialize(hero))
        } catch (exception: Exception) {
            HeroStartupResult.Error("Не удалось создать начальное состояние героя: ${exception.message ?: "ошибка движка"}")
        }
}

data class BookGameSession(
    val chatId: Long,
    val book: DairnBookPackage.PackageInfo,
    val hero: HeroIdentity,
    val characterState: CharacterState,
)

class InMemoryBookGameSessionStore {
    private val sessions = ConcurrentHashMap<Long, BookGameSession>()

    fun get(chatId: Long): BookGameSession? = sessions[chatId]
    fun save(session: BookGameSession) { sessions[session.chatId] = session }
}

/** Starts a book session only after an engine-provided initial character state is available. */
class BookStartupApplication(
    private val sessions: InMemoryBookGameSessionStore,
    private val heroInitialization: HeroInitializationService,
) {
    private val pendingHeroNames = ConcurrentHashMap<Long, PendingHeroName>()

    fun begin(chatId: Long, book: DairnBookPackage.PackageInfo, selectedHeroId: String? = null): HeroStartupResult {
        val result = heroInitialization.begin(book, selectedHeroId)
        if (result is HeroStartupResult.RequestHeroName) {
            pendingHeroNames[chatId] = PendingHeroName(book, result.heroId)
        } else {
            pendingHeroNames.remove(chatId)
        }
        return persistStarted(chatId, book, result)
    }

    fun provideHeroName(chatId: Long, book: DairnBookPackage.PackageInfo, name: String): HeroStartupResult {
        val pending = pendingHeroNames[chatId]
            ?: return HeroStartupResult.Error("Сначала выберите героя без имени.")
        if (pending.book != book) return HeroStartupResult.Error("Книга для ввода имени изменилась.")
        val result = heroInitialization.provideHeroName(book, pending.heroId, name)
        if (result is HeroStartupResult.Started) pendingHeroNames.remove(chatId)
        return persistStarted(chatId, book, result)
    }

    private fun persistStarted(chatId: Long, book: DairnBookPackage.PackageInfo, result: HeroStartupResult): HeroStartupResult {
        if (result is HeroStartupResult.Started) {
            sessions.save(BookGameSession(chatId, book, result.hero, result.characterState))
        }
        return result
    }

    private data class PendingHeroName(val book: DairnBookPackage.PackageInfo, val heroId: String)
}

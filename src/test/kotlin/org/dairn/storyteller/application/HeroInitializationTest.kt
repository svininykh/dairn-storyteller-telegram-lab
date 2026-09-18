package org.dairn.storyteller.application

import org.dairn.core.Dice
import org.dairn.core.DiceRoll
import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.storyteller.book.DairnBookPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HeroInitializationTest {
    private val capturedHeroes = mutableListOf<HeroIdentity>()
    private val service = HeroInitializationService(HeroStateInitializer { hero ->
        capturedHeroes += hero
        generatedState(hero.name)
    })

    @Test
    fun `book with selectable heroes requires a selection before a session starts`() {
        val sessions = InMemoryBookGameSessionStore()
        val application = BookStartupApplication(sessions, service)

        val result = assertIs<HeroStartupResult.SelectHero>(application.begin(CHAT_ID, pilotBook()))

        assertEquals(listOf("aibike", "karashash"), result.heroes.map { it.id })
        assertNull(sessions.get(CHAT_ID))
    }

    @Test
    fun `selected structured book hero reaches engine and starts session with returned state`() {
        val sessions = InMemoryBookGameSessionStore()
        val application = BookStartupApplication(sessions, service)

        val result = assertIs<HeroStartupResult.Started>(application.begin(CHAT_ID, pilotBook(), "aibike"))

        assertEquals(HeroIdentity("aibike", "Айбике"), capturedHeroes.single())
        assertEquals("Айбике", result.characterState.name)
        assertEquals(result.characterState, sessions.get(CHAT_ID)?.characterState)
    }

    @Test
    fun `selected hero without name requires an explicit name and passes it to engine`() {
        val sessions = InMemoryBookGameSessionStore()
        val application = BookStartupApplication(sessions, service)
        val book = DairnBookPackage.PackageInfo(
            "unnamed-hero",
            "1",
            "opening",
            listOf(DairnBookPackage.BookHero("wanderer")),
        )

        assertEquals("wanderer", assertIs<HeroStartupResult.RequestHeroName>(application.begin(CHAT_ID, book)).heroId)
        val result = assertIs<HeroStartupResult.Started>(application.provideHeroName(CHAT_ID, book, "  Батыр  "))

        assertEquals(HeroIdentity("wanderer", "Батыр"), capturedHeroes.last())
        assertEquals("Батыр", result.characterState.name)
    }

    @Test
    fun `blank name leaves unnamed hero pending`() {
        val sessions = InMemoryBookGameSessionStore()
        val application = BookStartupApplication(sessions, service)
        val book = DairnBookPackage.PackageInfo(
            "unnamed-hero",
            "1",
            "opening",
            listOf(DairnBookPackage.BookHero("wanderer")),
        )

        application.begin(CHAT_ID, book)

        assertIs<HeroStartupResult.Error>(application.provideHeroName(CHAT_ID, book, "  \t "))
        assertNull(sessions.get(CHAT_ID))
    }

    @Test
    fun `book without heroes delegates to separate engine creation flow`() {
        val result = service.begin(DairnBookPackage.PackageInfo("no-hero", "1", "opening"))

        assertIs<HeroStartupResult.CreationRequired>(result)
        assertEquals(emptyList(), capturedHeroes)
    }

    @Test
    fun `engine character generation is deterministic with deterministic dice`() {
        val first = generatedState("Айбике")
        val second = generatedState("Айбике")

        assertEquals(first, second)
    }

    private fun pilotBook() = DairnBookPackage.PackageInfo(
        "battles-of-the-great-steppe",
        "0.8.0",
        "first-trial",
        listOf(
            DairnBookPackage.BookHero("aibike", "Айбике"),
            DairnBookPackage.BookHero("karashash", "Қарашаш"),
        ),
    )

    private fun generatedState(name: String) = GreatSteppeCharacterGenerator().generate(
        GreatSteppeGenerationInput(name = name),
        Dice { count, sides -> DiceRoll(List(count) { 1 }, sides) },
    )

    private companion object {
        const val CHAT_ID = 77L
    }
}

package org.dairn.storyteller.telegram

import org.dairn.steppe.GreatSteppeCharacterGenerator
import org.dairn.steppe.GreatSteppeGenerationInput
import org.dairn.storyteller.application.BookStartupApplication
import org.dairn.storyteller.application.D20Roller
import org.dairn.storyteller.application.HeroInitializationService
import org.dairn.storyteller.application.HeroStateInitializer
import org.dairn.storyteller.application.InMemoryBookGameSessionStore
import org.dairn.storyteller.application.InMemorySessionStore
import org.dairn.storyteller.application.StoryTellerApplication
import org.dairn.storyteller.book.DairnBookPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramHeroStartupAdapterTest {
    @Test
    fun `one named hero starts story without a hero prompt`() {
        val views = adapter(namedBook()).onStart(1)

        assertTrue(views.first().text.contains("Айбике"))
        assertFalse(views.any { it.text == "Выберите героя:" })
        assertEquals(TelegramStoryTellerAdapter.DIGITAL_CALLBACK, views.last().keyboard.flatten().first().callbackData)
    }

    @Test
    fun `multiple heroes render stable IDs and selected callback starts story`() {
        val adapter = adapter(multipleBook())

        val choice = adapter.onStart(1).single()
        assertEquals(listOf("hero:aibike", "hero:karashash"), choice.keyboard.flatten().map { it.callbackData })

        assertTrue(adapter.onCallback(1, "hero:karashash").first().text.contains("Қарашаш"))
    }

    @Test
    fun `unnamed hero retains name state through validation failure`() {
        val adapter = adapter(unnamedBook())
        assertEquals("Введите имя героя:", adapter.onStart(1).single().text)

        assertEquals("Укажите имя героя.", requireNotNull(adapter.onText(1, "  ")).single().text)
        assertTrue(requireNotNull(adapter.onText(1, "Батыр")).first().text.contains("Батыр"))
    }

    @Test
    fun `stale callback and independent chats cannot alter another startup`() {
        val adapter = adapter(multipleBook())
        adapter.onStart(1)
        adapter.onStart(2)

        assertTrue(adapter.onCallback(1, "hero:aibike").first().text.contains("Айбике"))
        assertEquals("Выбор героя больше не активен. Отправьте /start.", adapter.onCallback(1, "hero:karashash").single().text)
        assertTrue(adapter.onCallback(2, "hero:karashash").first().text.contains("Қарашаш"))
    }

    @Test
    fun `book without heroes is reported without inventing a hero`() {
        val view = adapter(DairnBookPackage.PackageInfo("empty", "1", "start")).onStart(1).single()

        assertTrue(view.text.contains("поток создания героя Engine"))
    }

    private fun adapter(book: DairnBookPackage.PackageInfo): TelegramHeroStartupAdapter {
        val heroApplication = BookStartupApplication(
            InMemoryBookGameSessionStore(),
            HeroInitializationService(HeroStateInitializer { hero ->
                GreatSteppeCharacterGenerator().generate(GreatSteppeGenerationInput(hero.name))
            }),
        )
        val storyTeller = TelegramStoryTellerAdapter(
            StoryTellerApplication(InMemorySessionStore(), D20Roller { 1 }),
        )
        return TelegramHeroStartupAdapter(heroApplication, { book }, storyTeller)
    }

    private fun namedBook() = DairnBookPackage.PackageInfo("named", "1", "start", listOf(DairnBookPackage.BookHero("aibike", "Айбике")))
    private fun unnamedBook() = DairnBookPackage.PackageInfo("unnamed", "1", "start", listOf(DairnBookPackage.BookHero("wanderer")))
    private fun multipleBook() = DairnBookPackage.PackageInfo(
        "multiple", "1", "start",
        listOf(DairnBookPackage.BookHero("aibike", "Айбике"), DairnBookPackage.BookHero("karashash", "Қарашаш")),
    )
}

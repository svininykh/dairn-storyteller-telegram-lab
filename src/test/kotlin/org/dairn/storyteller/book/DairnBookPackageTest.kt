package org.dairn.storyteller.book

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DairnBookPackageTest {
    @Test fun `validates supplied pilot package without changing its narrative`() {
        val packagePath = Path.of("test-data/battles-of-the-great-steppe-pilot.dairn")

        val validation = DairnBookPackage.validate(packagePath)
        assertTrue(validation.valid, validation.errors.joinToString())
        assertEquals(
            DairnBookPackage.PackageInfo("battles-of-the-great-steppe", "0.8.0", "first-trial"),
            DairnBookPackage.read(packagePath),
        )
    }

    @Test fun `writes and reads a minimal book`() {
        val root = Files.createTempDirectory("dairn-book")
        root.resolve("stories/first/chapters").createDirectories()
        root.resolve("book.yaml").writeText("book-id: sample\nbook-version: 1\nstart-story: first\nstories:\n  - id: first\n    source: stories/first/story.yaml\n")
        root.resolve("stories/first/story.yaml").writeText("story-id: first\nstart-chapter: one\nchapters:\n  - id: one\n    source: chapters/one.ru.md\n")
        root.resolve("stories/first/chapters/one.ru.md").writeText("# One {#one}\n")
        val packagePath = root.resolve("sample.dairn")

        DairnBookPackage.write(root, packagePath)

        assertTrue(DairnBookPackage.validate(packagePath).valid)
        assertEquals(DairnBookPackage.PackageInfo("sample", "1", "first"), DairnBookPackage.read(packagePath))
    }

    @Test fun `rejects unsupported package version`() {
        val packagePath = Files.createTempFile("unsupported", ".dairn")
        ZipOutputStream(Files.newOutputStream(packagePath)).use { zip ->
            fun entry(name: String, contents: String) { zip.putNextEntry(ZipEntry(name)); zip.write(contents.toByteArray()); zip.closeEntry() }
            entry("dairn-package.yaml", "package-format: dairn-book-package\npackage-version: 9\nbook: book.yaml\n")
            entry("book.yaml", "book-id: sample\nbook-version: 1\nstart-story: absent\nstories:\n")
        }
        val result = DairnBookPackage.validate(packagePath)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it == "Unsupported package version" })
    }

    @Test fun `rejects a goto to an absent scene`() {
        val root = Files.createTempDirectory("dairn-bad-goto")
        root.resolve("stories/first/chapters").createDirectories()
        root.resolve("book.yaml").writeText("book-id: sample\nbook-version: 1\nstart-story: first\nstories:\n  - id: first\n    source: stories/first/story.yaml\n")
        root.resolve("stories/first/story.yaml").writeText("story-id: first\nstart-chapter: one\nchapters:\n  - id: one\n    source: chapters/one.ru.md\n")
        root.resolve("stories/first/chapters/one.ru.md").writeText("# One {#one}\n\n:::choice\ngoto: absent\n:::\n")
        val packagePath = root.resolve("sample.dairn")

        DairnBookPackage.write(root, packagePath)

        assertTrue(DairnBookPackage.validate(packagePath).errors.any { it.contains("Invalid goto target") })
    }
}

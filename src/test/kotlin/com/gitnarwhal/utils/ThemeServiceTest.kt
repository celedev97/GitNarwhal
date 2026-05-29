package com.gitnarwhal.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ThemeServiceTest {

    @Test
    fun `listThemes finds bundled nord and gray`() {
        val themes = ThemeService.listThemes()
        val paths = themes.map { it.path }
        assertTrue(paths.any { it.endsWith("/nord.theme.json") }, "nord theme should be present, got: $paths")
        assertTrue(paths.any { it.endsWith("/gray.theme.json") }, "gray theme should be present, got: $paths")
    }

    @Test
    fun `bundled nord theme parses name and dark flag`() {
        val nord = ThemeService.listThemes().first { it.path.endsWith("/nord.theme.json") }
        assertEquals("Dark (Nord)", nord.name)
        assertTrue(nord.dark, "Nord should be marked dark=true")
    }

    @Test
    fun `bundled gray theme parses as light`() {
        val gray = ThemeService.listThemes().first { it.path.endsWith("/gray.theme.json") }
        assertEquals(false, gray.dark, "Gray should be marked dark=false")
    }

    @Test
    fun `default theme path points to a classpath resource that exists`() {
        val path = ThemeService.defaultThemePath()
        assertTrue(path.startsWith("classpath:"), "default path should be classpath: prefixed, got '$path'")
        val resource = path.removePrefix("classpath:")
        val stream = ThemeService::class.java.getResourceAsStream(resource)
        assertNotNull(stream, "default theme '$resource' should resolve on classpath")
        stream?.close()
    }

    @Test
    fun `registerDefaultsSource does not throw`() {
        assertDoesNotThrow { ThemeService.registerDefaultsSource() }
    }

    @Test
    fun `Theme data class toString returns name`() {
        val theme = Theme("My Theme", "classpath:/some/path.json", dark = true)
        assertEquals("My Theme", theme.toString())
    }

    @Test
    fun `Theme data class equals and copy work`() {
        val a = Theme("Dark", "classpath:/dark.json", dark = true)
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val c = a.copy(name = "Light", dark = false)
        assertNotEquals(a, c)
    }

    @Test
    fun `listThemes returns themes with non-blank paths`() {
        val themes = ThemeService.listThemes()
        assertTrue(themes.all { it.path.isNotBlank() }, "all themes should have non-blank paths")
        assertTrue(themes.all { it.name.isNotBlank() }, "all themes should have non-blank names")
    }

    @Test
    fun `applyFromSettings runs without throwing`() {
        // load() catches all exceptions internally; safe even in headless mode
        assertDoesNotThrow { ThemeService.applyFromSettings() }
    }

    @Test
    fun `applyFromSettings handles blank theme by using default`() {
        val orig = Settings.theme
        try {
            Settings.theme = ""
            assertDoesNotThrow { ThemeService.applyFromSettings() }
        } finally {
            Settings.theme = orig
        }
    }

    @Test
    fun `setAndApply loads a classpath theme without throwing`() {
        val orig = Settings.theme
        try {
            assertDoesNotThrow { ThemeService.setAndApply(ThemeService.defaultThemePath()) }
        } finally {
            Settings.theme = orig
        }
    }

    @Test
    fun `applyFromSettings falls back gracefully for nonexistent path`() {
        val orig = Settings.theme
        try {
            Settings.theme = "/nonexistent/path/theme.json"
            // load() will fail but catch the exception; applyFromSettings tries fallback
            assertDoesNotThrow { ThemeService.applyFromSettings() }
        } finally {
            Settings.theme = orig
        }
    }

    @Test
    fun `setAndApply with external absolute path covers FileInputStream branch`() {
        val tmp = Files.createTempFile("test-theme-", ".theme.json").toFile()
        val orig = Settings.theme
        try {
            tmp.writeText("""{"name":"Ext Test","dark":false}""")
            assertDoesNotThrow { ThemeService.setAndApply(tmp.absolutePath) }
        } finally {
            Settings.theme = orig
            Settings.save()
            tmp.delete()
        }
    }

    @Test
    fun `listThemes includes external themes from user themes dir`() {
        val dir = Path.of(System.getProperty("user.home"), ".gitnarwhal", "themes")
        Files.createDirectories(dir)
        val testTheme = dir.resolve("zzz-test-external.theme.json").toFile()
        try {
            testTheme.writeText("""{"name":"External Test","dark":false}""")
            val themes = ThemeService.listThemes()
            assertTrue(
                themes.any { it.path == testTheme.absolutePath },
                "listThemes should include external theme, got: ${themes.map { it.path }}"
            )
        } finally {
            testTheme.delete()
        }
    }
}

package com.gitnarwhal.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}

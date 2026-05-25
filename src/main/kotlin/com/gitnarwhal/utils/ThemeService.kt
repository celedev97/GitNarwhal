package com.gitnarwhal.utils

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.IntelliJTheme
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Theme entry — either bundled (classpath:/...) or external (absolute path on disk).
 * The [path] field is what gets persisted to Settings.
 */
data class Theme(
    val name: String,
    val path: String,
    val dark: Boolean
) {
    override fun toString(): String = name
}

object ThemeService {

    private const val CLASSPATH_PREFIX = "classpath:"
    private const val BUNDLED_THEMES_PACKAGE = "/com/gitnarwhal/themes"
    private val EXTERNAL_THEMES_DIR: Path =
        Path.of(System.getProperty("user.home"), ".gitnarwhal", "themes")

    /** Bundled themes shipped inside the jar. */
    private val bundledThemes: List<String> = listOf(
        "$BUNDLED_THEMES_PACKAGE/claude.theme.json",
        "$BUNDLED_THEMES_PACKAGE/nord.theme.json",
        "$BUNDLED_THEMES_PACKAGE/gray.theme.json"
    )

    fun listThemes(): List<Theme> {
        val out = mutableListOf<Theme>()

        bundledThemes.forEach { resourcePath ->
            ThemeService::class.java.getResourceAsStream(resourcePath)?.use { stream ->
                out += parseTheme(stream, CLASSPATH_PREFIX + resourcePath)
            }
        }

        if (Files.isDirectory(EXTERNAL_THEMES_DIR)) {
            EXTERNAL_THEMES_DIR.toFile().listFiles { f -> f.name.endsWith(".theme.json") }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    try {
                        FileInputStream(file).use { stream ->
                            out += parseTheme(stream, file.absolutePath)
                        }
                    } catch (e: Exception) {
                        System.err.println("Failed to read theme ${file.absolutePath}: ${e.message}")
                    }
                }
        }

        return out
    }

    /** Applies the theme stored in Settings, falling back to the default on error. */
    fun applyFromSettings() {
        val path = Settings.theme.ifBlank { defaultThemePath() }
        if (!load(path)) {
            val fallback = defaultThemePath()
            if (path != fallback) {
                System.err.println("Theme '$path' failed to load, falling back to $fallback")
                Settings.theme = fallback
                Settings.save()
                load(fallback)
            }
        }
    }

    fun setAndApply(path: String) {
        Settings.theme = path
        Settings.save()
        load(path)
    }

    fun defaultThemePath(): String = CLASSPATH_PREFIX + bundledThemes[0]

    private fun load(path: String): Boolean {
        return try {
            openStream(path).use { stream -> IntelliJTheme.setup(stream) }
            FlatLaf.updateUI()
            true
        } catch (e: Exception) {
            System.err.println("Error applying theme '$path': ${e.message}")
            false
        }
    }

    private fun openStream(path: String): InputStream {
        return if (path.startsWith(CLASSPATH_PREFIX)) {
            val resource = path.removePrefix(CLASSPATH_PREFIX)
            ThemeService::class.java.getResourceAsStream(resource)
                ?: error("Bundled theme not found on classpath: $resource")
        } else {
            FileInputStream(File(path))
        }
    }

    private fun parseTheme(stream: InputStream, path: String): Theme {
        val text = stream.bufferedReader().readText()
        val json = JSONObject(text)
        val name = json.optString("name", path.substringAfterLast('/').removeSuffix(".theme.json"))
        val dark = json.optBoolean("dark", false)
        return Theme(name, path, dark)
    }

    /** Registers the GitNarwhal themes package so FlatLaf picks up FlatLaf.properties. */
    fun registerDefaultsSource() {
        FlatLaf.registerCustomDefaultsSource("com.gitnarwhal.themes")
    }
}

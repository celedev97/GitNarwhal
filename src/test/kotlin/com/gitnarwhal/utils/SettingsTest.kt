package com.gitnarwhal.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class SettingsTest {

    // Backup/restore mutable settings to avoid contaminating real config file
    private var savedTerminalPreset   = ""
    private var savedTerminalCommand  = ""
    private var savedCloneFolder      = ""
    private var savedReopenTabs       = true
    private var savedIgnoreWs         = false
    private var savedIgnorePatterns   = ""
    private var savedDiffFontFamily   = ""
    private var savedDiffFontSize     = 12
    private var savedEnableForce      = true
    private var savedSafeForce        = true
    private var savedGitPath          = ""
    private var savedTheme            = ""

    @BeforeEach
    fun backup() {
        savedTerminalPreset  = Settings.terminalPreset
        savedTerminalCommand = Settings.terminalCommand
        savedCloneFolder     = Settings.defaultCloneFolder
        savedReopenTabs      = Settings.reopenTabs
        savedIgnoreWs        = Settings.diffIgnoreWhitespace
        savedIgnorePatterns  = Settings.diffIgnorePatterns
        savedDiffFontFamily  = Settings.diffFontFamily
        savedDiffFontSize    = Settings.diffFontSize
        savedEnableForce     = Settings.enableForcePush
        savedSafeForce       = Settings.safeForcePush
        savedGitPath         = Settings.gitPath
        savedTheme           = Settings.theme
    }

    @AfterEach
    fun restore() {
        Settings.terminalPreset      = savedTerminalPreset
        Settings.terminalCommand     = savedTerminalCommand
        Settings.defaultCloneFolder  = savedCloneFolder
        Settings.reopenTabs          = savedReopenTabs
        Settings.diffIgnoreWhitespace = savedIgnoreWs
        Settings.diffIgnorePatterns  = savedIgnorePatterns
        Settings.diffFontFamily      = savedDiffFontFamily
        Settings.diffFontSize        = savedDiffFontSize
        Settings.enableForcePush     = savedEnableForce
        Settings.safeForcePush       = savedSafeForce
        Settings.gitPath             = savedGitPath
        Settings.theme               = savedTheme
    }

    @Test
    fun `terminalPreset default is auto or already set`() {
        assertNotNull(Settings.terminalPreset)
    }

    @Test
    fun `terminalPreset can be set and read back`() {
        Settings.terminalPreset = "pwsh"
        assertEquals("pwsh", Settings.terminalPreset)
        Settings.terminalPreset = "gitbash"
        assertEquals("gitbash", Settings.terminalPreset)
        Settings.terminalPreset = "auto"
        assertEquals("auto", Settings.terminalPreset)
    }

    @Test
    fun `terminalCommand can be set and read back`() {
        Settings.terminalCommand = "my-terminal \$REPO"
        assertEquals("my-terminal \$REPO", Settings.terminalCommand)
    }

    @Test
    fun `terminalCommand can be empty string`() {
        Settings.terminalCommand = ""
        assertEquals("", Settings.terminalCommand)
    }

    @Test
    fun `defaultCloneFolder can be set and read back`() {
        Settings.defaultCloneFolder = "/tmp/test-repos"
        assertEquals("/tmp/test-repos", Settings.defaultCloneFolder)
    }

    @Test
    fun `reopenTabs can be toggled`() {
        Settings.reopenTabs = true
        assertTrue(Settings.reopenTabs)
        Settings.reopenTabs = false
        assertFalse(Settings.reopenTabs)
    }

    @Test
    fun `diffIgnoreWhitespace can be toggled`() {
        Settings.diffIgnoreWhitespace = true
        assertTrue(Settings.diffIgnoreWhitespace)
        Settings.diffIgnoreWhitespace = false
        assertFalse(Settings.diffIgnoreWhitespace)
    }

    @Test
    fun `diffIgnorePatterns can be set`() {
        Settings.diffIgnorePatterns = "*.lock, *.sum"
        assertEquals("*.lock, *.sum", Settings.diffIgnorePatterns)
    }

    @Test
    fun `diffFontFamily can be set`() {
        Settings.diffFontFamily = "Courier New"
        assertEquals("Courier New", Settings.diffFontFamily)
    }

    @Test
    fun `diffFontSize can be set`() {
        Settings.diffFontSize = 14
        assertEquals(14, Settings.diffFontSize)
    }

    @Test
    fun `enableForcePush and safeForcePush can be set`() {
        Settings.enableForcePush = false
        assertFalse(Settings.enableForcePush)
        Settings.safeForcePush = false
        assertFalse(Settings.safeForcePush)
    }

    @Test
    fun `gitPath can be set and cleared`() {
        Settings.gitPath = "/usr/local/bin/git"
        assertEquals("/usr/local/bin/git", Settings.gitPath)
        Settings.gitPath = ""
        assertEquals("", Settings.gitPath)
    }

    @Test
    fun `theme can be set`() {
        val newTheme = "classpath:/com/gitnarwhal/themes/nord.theme.json"
        Settings.theme = newTheme
        assertEquals(newTheme, Settings.theme)
    }

    @Test
    fun `columnWidths contains expected keys`() {
        val cw = Settings.columnWidths
        assertNotNull(cw)
        assertTrue(cw.has("graph"), "columnWidths should have 'graph'")
        assertTrue(cw.has("date"),  "columnWidths should have 'date'")
        assertTrue(cw.has("committer"), "columnWidths should have 'committer'")
        assertTrue(cw.has("commit"),    "columnWidths should have 'commit'")
    }

    @Test
    fun `customActions is a JSON array`() {
        val arr = Settings.customActions
        assertNotNull(arr)
    }

    @Test
    fun `customActions can be replaced`() {
        val orig = Settings.customActions
        val newArr = JSONArray()
        val action = JSONObject()
        action.put("name", "Test Action")
        action.put("command", "echo hello")
        action.put("params", "")
        action.put("shortcut", "")
        newArr.put(action)
        Settings.customActions = newArr
        assertEquals(1, Settings.customActions.length())
        assertEquals("Test Action", Settings.customActions.getJSONObject(0).getString("name"))
        Settings.customActions = orig
    }

    @Test
    fun `Settings FILE path is set`() {
        assertNotNull(Settings.FILE)
        assertTrue(Settings.FILE.isNotBlank())
    }
}

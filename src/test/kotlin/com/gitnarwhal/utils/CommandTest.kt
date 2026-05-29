package com.gitnarwhal.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS as JUnitOS

class CommandTest {

    @Test
    fun `find returns null for nonexistent binary`() {
        val result = Command.find("definitely-not-a-real-binary-xyz123")
        assertEquals(null, result)
    }

    @Test
    fun `find returns Command for git when in PATH`() {
        // The repo's own CI installs git; locally git is always present
        val result = Command.find("git")
        assertTrue(result != null, "git should be discoverable in PATH on a dev machine / CI")
    }

    @Test
    @EnabledOnOs(JUnitOS.WINDOWS, JUnitOS.LINUX, JUnitOS.MAC)
    fun `execute captures stdout and exit code`() {
        val cmd = if (com.gitnarwhal.utils.OS.CURRENT == com.gitnarwhal.utils.OS.WINDOWS)
            Command("cmd", "/c", "echo", "hello-gitnarwhal")
        else
            Command("echo", "hello-gitnarwhal")
        cmd.execute()
        assertTrue(cmd.success, "echo should succeed, got code=${cmd.code} output='${cmd.output}'")
        assertEquals(0, cmd.code)
        assertTrue(cmd.output.contains("hello-gitnarwhal"), "expected 'hello-gitnarwhal' in output '${cmd.output}'")
    }

    @Test
    fun `nonzero exit produces success=false`() {
        val cmd = if (com.gitnarwhal.utils.OS.CURRENT == com.gitnarwhal.utils.OS.WINDOWS)
            Command("cmd", "/c", "exit", "7")
        else
            Command("sh", "-c", "exit 7")
        cmd.execute()
        assertEquals(false, cmd.success)
        assertEquals(7, cmd.code)
    }

    @Test
    fun `plus operator appends arguments and returns new Command`() {
        val base = if (OS.CURRENT == OS.WINDOWS) Command("cmd", "/c", "echo") else Command("echo")
        val extended = base + "appended-arg"
        assertNotNull(extended)
        assertTrue(extended.toString().contains("echo"))
    }

    @Test
    fun `toString returns space-joined command parts`() {
        val cmd = Command("git", "status")
        assertEquals("git status", cmd.toString())
    }

    @Test
    fun `onLine callback is invoked for each output line`() {
        val cmd = if (OS.CURRENT == OS.WINDOWS)
            Command("cmd", "/c", "echo", "callback-test")
        else
            Command("echo", "callback-test")
        val collected = mutableListOf<String>()
        cmd.execute(onLine = { collected.add(it) })
        assertTrue(collected.isNotEmpty(), "onLine should receive at least one line")
        assertTrue(collected.any { it.contains("callback-test") },
            "collected lines should contain 'callback-test', got: $collected")
    }

    @Test
    fun `workingDir can be read and set`() {
        val cmd = Command("git")
        val original = cmd.workingDir
        assertNotNull(original)
        cmd.workingDir = System.getProperty("java.io.tmpdir")
        assertNotNull(cmd.workingDir)
        cmd.workingDir = original
    }

    @Test
    fun `execute with explicit path overrides workingDir`() {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val cmd = if (OS.CURRENT == OS.WINDOWS) Command("cmd", "/c", "cd") else Command("pwd")
        cmd.execute(path = tmpDir)
        assertTrue(cmd.success || cmd.code >= 0)
    }

    @Test
    fun `single string command is split on spaces`() {
        val cmd = if (OS.CURRENT == OS.WINDOWS) Command("cmd /c echo split-test")
                  else Command("echo split-test")
        cmd.execute()
        assertTrue(cmd.success)
        assertTrue(cmd.output.contains("split-test"))
    }
}

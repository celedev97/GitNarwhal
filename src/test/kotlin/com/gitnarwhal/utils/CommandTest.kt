package com.gitnarwhal.utils

import org.junit.jupiter.api.Assertions.assertEquals
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
}

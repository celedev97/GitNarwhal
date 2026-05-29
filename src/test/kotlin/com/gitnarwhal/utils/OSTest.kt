package com.gitnarwhal.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OSTest {

    @Test
    fun `containsOne matches any needle`() {
        assertTrue("hello world".containsOne("xyz", "world"))
        assertTrue(!"hello world".containsOne("xyz", "abc"))
    }

    @Test
    fun `containsAll requires every needle`() {
        assertTrue("hello world".containsAll("hello", "world"))
        assertTrue(!"hello world".containsAll("hello", "xyz"))
    }

    @Test
    fun `CURRENT resolves to a known OS`() {
        // sanity: don't crash, value is one of the enum members
        val current = OS.CURRENT
        assertTrue(current in setOf(OS.WINDOWS, OS.LINUX, OS.MAC))
    }

    @Test
    fun `EXE matches platform convention`() {
        when (OS.CURRENT) {
            OS.WINDOWS -> assertEquals(".exe", OS.EXE)
            else       -> assertEquals("",     OS.EXE)
        }
    }

    @Test
    fun `WHERE is the platform find-on-path command`() {
        when (OS.CURRENT) {
            OS.WINDOWS -> assertEquals("where", OS.WHERE)
            else       -> assertEquals("which", OS.WHERE)
        }
    }

    @Test
    fun `TERMINAL command is defined and non-blank`() {
        val t = OS.TERMINAL
        assertNotNull(t)
        assertTrue(t.toString().isNotBlank(), "TERMINAL should have a non-blank command string")
    }

    @Test
    fun `EXPLORER command is defined`() {
        assertNotNull(OS.EXPLORER)
        assertTrue(OS.EXPLORER.toString().isNotBlank())
    }

    @Test
    fun `BROWSER command is defined`() {
        assertNotNull(OS.BROWSER)
        assertTrue(OS.BROWSER.toString().isNotBlank())
    }

    @Test
    fun `toPath extension converts string to Path`() {
        val p = "/tmp/test-path".toPath()
        assertNotNull(p)
        assertTrue(p.toString().contains("tmp") || p.toString().contains("test-path"))
    }
}

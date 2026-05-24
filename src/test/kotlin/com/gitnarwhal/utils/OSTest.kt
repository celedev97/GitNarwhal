package com.gitnarwhal.utils

import org.junit.jupiter.api.Assertions.assertEquals
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
}

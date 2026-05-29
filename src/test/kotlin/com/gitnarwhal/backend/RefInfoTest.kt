package com.gitnarwhal.backend

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RefInfoTest {

    @Test
    fun `RefInfo stores name and type`() {
        val ref = RefInfo("main", RefType.LOCAL_BRANCH)
        assertEquals("main", ref.name)
        assertEquals(RefType.LOCAL_BRANCH, ref.type)
    }

    @Test
    fun `RefInfo equals and hashCode`() {
        val a = RefInfo("main", RefType.LOCAL_BRANCH)
        val b = RefInfo("main", RefType.LOCAL_BRANCH)
        val c = RefInfo("dev",  RefType.LOCAL_BRANCH)
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `RefInfo copy produces a modified instance`() {
        val original = RefInfo("main", RefType.LOCAL_BRANCH)
        val copy = original.copy(name = "dev")
        assertEquals("dev", copy.name)
        assertEquals(RefType.LOCAL_BRANCH, copy.type)
        assertNotEquals(original, copy)
    }

    @Test
    fun `RefInfo toString is non-null`() {
        val ref = RefInfo("v1.0", RefType.TAG)
        assertNotNull(ref.toString())
        assertTrue(ref.toString().contains("v1.0"))
    }

    @Test
    fun `RefType all values are accessible`() {
        val values = RefType.values()
        assertTrue(values.contains(RefType.HEAD))
        assertTrue(values.contains(RefType.LOCAL_BRANCH))
        assertTrue(values.contains(RefType.REMOTE_BRANCH))
        assertTrue(values.contains(RefType.TAG))
    }

    @Test
    fun `RefType valueOf resolves by name`() {
        assertEquals(RefType.HEAD,          RefType.valueOf("HEAD"))
        assertEquals(RefType.LOCAL_BRANCH,  RefType.valueOf("LOCAL_BRANCH"))
        assertEquals(RefType.REMOTE_BRANCH, RefType.valueOf("REMOTE_BRANCH"))
        assertEquals(RefType.TAG,           RefType.valueOf("TAG"))
    }
}

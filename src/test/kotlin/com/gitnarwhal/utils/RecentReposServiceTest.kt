package com.gitnarwhal.utils

import org.json.JSONArray
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class RecentReposServiceTest {

    private lateinit var savedRepos: JSONArray

    @BeforeEach
    fun backup() {
        savedRepos = Settings.recentRepos
        // Start each test with a clean slate
        Settings.recentRepos = JSONArray()
    }

    @AfterEach
    fun restore() {
        Settings.recentRepos = savedRepos
    }

    @Test
    fun `getAll returns empty list when no repos are stored`() {
        val all = RecentReposService.getAll()
        assertTrue(all.isEmpty(), "expected empty list, got $all")
    }

    @Test
    fun `record adds a single repo`() {
        RecentReposService.record("MyRepo", "/path/to/repo")
        val all = RecentReposService.getAll()
        assertEquals(1, all.size)
        assertEquals("MyRepo", all[0].name)
        assertEquals("/path/to/repo", all[0].path)
    }

    @Test
    fun `record deduplicates the same path`() {
        RecentReposService.record("First", "/dup/path")
        RecentReposService.record("Second", "/dup/path")
        val all = RecentReposService.getAll()
        assertEquals(1, all.size, "duplicate path should be deduplicated")
        assertEquals("Second", all[0].name, "newer entry should replace old one")
    }

    @Test
    fun `record prepends — most recent is first`() {
        RecentReposService.record("OlderRepo", "/older")
        RecentReposService.record("NewerRepo", "/newer")
        val all = RecentReposService.getAll()
        assertEquals(2, all.size)
        assertEquals("NewerRepo", all[0].name)
        assertEquals("OlderRepo",  all[1].name)
    }

    @Test
    fun `record sets a non-zero lastOpened timestamp`() {
        val before = System.currentTimeMillis()
        RecentReposService.record("Timestamped", "/ts/path")
        val all = RecentReposService.getAll()
        assertTrue(all[0].lastOpened >= before, "lastOpened should be >= current time at recording")
    }

    @Test
    fun `remove deletes an entry by path`() {
        RecentReposService.record("ToRemove", "/remove/me")
        RecentReposService.record("KeepMe",   "/keep/me")
        RecentReposService.remove("/remove/me")
        val all = RecentReposService.getAll()
        assertEquals(1, all.size)
        assertEquals("KeepMe", all[0].name)
    }

    @Test
    fun `remove on nonexistent path does nothing`() {
        RecentReposService.record("Existing", "/existing")
        RecentReposService.remove("/no-such-path")
        assertEquals(1, RecentReposService.getAll().size)
    }

    @Test
    fun `remove on empty list does nothing`() {
        assertDoesNotThrow { RecentReposService.remove("/any/path") }
        assertTrue(RecentReposService.getAll().isEmpty())
    }

    @Test
    fun `record multiple distinct repos are all stored`() {
        RecentReposService.record("A", "/a")
        RecentReposService.record("B", "/b")
        RecentReposService.record("C", "/c")
        assertEquals(3, RecentReposService.getAll().size)
    }

    @Test
    fun `RecentRepo data class fields are accessible`() {
        RecentReposService.record("TestRepo", "/test/path")
        val repo = RecentReposService.getAll()[0]
        assertNotNull(repo.name)
        assertNotNull(repo.path)
        assertTrue(repo.lastOpened > 0)
    }
}

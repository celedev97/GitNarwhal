package com.gitnarwhal.utils

import org.json.JSONArray
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class RecentReposServiceTest {

    private lateinit var savedRepos: JSONArray
    private lateinit var savedFolders: JSONArray

    @BeforeEach
    fun backup() {
        savedRepos    = Settings.recentRepos
        savedFolders  = Settings.recentFolders
        Settings.recentRepos   = JSONArray()
        Settings.recentFolders = JSONArray()
    }

    @AfterEach
    fun restore() {
        Settings.recentRepos   = savedRepos
        Settings.recentFolders = savedFolders
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

    // ── renameRepo ────────────────────────────────────────────────────────────

    @Test
    fun `renameRepo updates display name`() {
        RecentReposService.record("OldName", "/rename/path")
        RecentReposService.renameRepo("/rename/path", "NewName")
        assertEquals("NewName", RecentReposService.getAll()[0].name)
    }

    @Test
    fun `renameRepo on nonexistent path does nothing`() {
        RecentReposService.record("Keep", "/keep")
        assertDoesNotThrow { RecentReposService.renameRepo("/no-such", "X") }
        assertEquals("Keep", RecentReposService.getAll()[0].name)
    }

    // ── Folders ───────────────────────────────────────────────────────────────

    @Test
    fun `getFolders returns empty list when none exist`() {
        assertTrue(RecentReposService.getFolders().isEmpty())
    }

    @Test
    fun `addFolder creates a folder with non-blank id and name`() {
        val id = RecentReposService.addFolder("Work")
        val folders = RecentReposService.getFolders()
        assertEquals(1, folders.size)
        assertEquals("Work", folders[0].name)
        assertEquals(id, folders[0].id)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `addFolder allows multiple folders`() {
        RecentReposService.addFolder("Work")
        RecentReposService.addFolder("Personal")
        assertEquals(2, RecentReposService.getFolders().size)
    }

    @Test
    fun `renameFolder updates folder name`() {
        val id = RecentReposService.addFolder("OldFolder")
        RecentReposService.renameFolder(id, "NewFolder")
        assertEquals("NewFolder", RecentReposService.getFolders()[0].name)
    }

    @Test
    fun `renameFolder on nonexistent id does nothing`() {
        RecentReposService.addFolder("Keep")
        assertDoesNotThrow { RecentReposService.renameFolder("no-such-id", "X") }
        assertEquals("Keep", RecentReposService.getFolders()[0].name)
    }

    @Test
    fun `setRepoFolder assigns a repo to a folder`() {
        val id = RecentReposService.addFolder("MyFolder")
        RecentReposService.record("Repo", "/repo/path")
        RecentReposService.setRepoFolder("/repo/path", id)
        assertEquals(id, RecentReposService.getAll()[0].folderId)
    }

    @Test
    fun `setRepoFolder with null removes repo from folder`() {
        val id = RecentReposService.addFolder("F")
        RecentReposService.record("Repo", "/repo/path")
        RecentReposService.setRepoFolder("/repo/path", id)
        RecentReposService.setRepoFolder("/repo/path", null)
        assertNull(RecentReposService.getAll()[0].folderId)
    }

    @Test
    fun `record preserves folderId when re-recording same path`() {
        val id = RecentReposService.addFolder("F")
        RecentReposService.record("Repo", "/repo/path")
        RecentReposService.setRepoFolder("/repo/path", id)
        RecentReposService.record("Repo Updated", "/repo/path")
        assertEquals(id, RecentReposService.getAll()[0].folderId)
    }

    @Test
    fun `removeFolder deletes folder and ungroups its repos`() {
        val id = RecentReposService.addFolder("ToDelete")
        RecentReposService.record("Repo", "/repo/path")
        RecentReposService.setRepoFolder("/repo/path", id)
        RecentReposService.removeFolder(id)
        assertTrue(RecentReposService.getFolders().isEmpty())
        assertNull(RecentReposService.getAll()[0].folderId)
    }

    @Test
    fun `removeFolder on nonexistent id does nothing`() {
        RecentReposService.addFolder("Keep")
        assertDoesNotThrow { RecentReposService.removeFolder("no-such-id") }
        assertEquals(1, RecentReposService.getFolders().size)
    }
}

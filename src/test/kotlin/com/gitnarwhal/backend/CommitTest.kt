package com.gitnarwhal.backend

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Color
import javax.swing.SwingUtilities

/**
 * Unit tests for [Commit] / [GitShow] — the git-show-backed property delegates.
 * Headless-safe: no RepoTab/UI is built. Commits are pre-populated so the lazy
 * git-show fallback is never hit (the only path that needs a repo context).
 */
class CommitTest {

    private val dateRegex = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")

    /** prePopulate order: [shortHash, author, authorDateUnix, committer, committerDateUnix, title]. */
    private fun sampleCommit(): Commit = Commit("a".repeat(40)).apply {
        prePopulate(listOf(
            "abc1234",
            "Alice <alice@example.com>",
            "0",                       // author date — unix epoch
            "Bob <bob@example.com>",
            "1000000000",             // committer date — unix epoch
            "Initial commit"
        ))
    }

    @Test
    fun `prePopulated properties read back without a repo context`() {
        val c = sampleCommit()
        assertEquals("abc1234", c.shortHash)
        assertEquals("Alice <alice@example.com>", c.author)
        assertEquals("Bob <bob@example.com>", c.committer)
        assertEquals("Initial commit", c.title)
    }

    @Test
    fun `date fields are formatted from unix epoch`() {
        val c = sampleCommit()
        assertTrue(dateRegex.matches(c.authorDate), "authorDate should be formatted, got '${c.authorDate}'")
        assertTrue(dateRegex.matches(c.committerDate), "committerDate should be formatted, got '${c.committerDate}'")
    }

    @Test
    fun `committerTimeStamp returns the raw unix value, not a formatted date`() {
        val c = sampleCommit()
        assertEquals("1000000000", c.committerTimeStamp)
    }

    @Test
    fun `blank date field is returned verbatim and does not throw`() {
        // Mirrors the synthetic "Uncommitted changes" node: empty date must not be epoch-parsed.
        val c = Commit("0".repeat(40)).apply {
            prePopulate(listOf("", "", "", "", "", "Uncommitted changes"))
        }
        assertEquals("", c.authorDate)
        assertEquals("Uncommitted changes", c.title)
    }

    @Test
    fun `message joins the trailing body lines`() {
        val c = Commit("b".repeat(40)).apply {
            // 6 header slots (indices 0-5) + body lines beyond linePositions.count()
            prePopulate(listOf(
                "deadbee", "Author <a@x>", "0", "Author <a@x>", "0", "Subject",
                "(skipped index 6)", "body line one", "body line two"
            ))
        }
        assertEquals("body line one\nbody line two", c.message)
    }

    @Test
    fun `reading a property on the EDT never blocks on git, returns a placeholder`() {
        val c = Commit("c".repeat(40))   // no prePopulate, no repo context
        var value: String? = null
        SwingUtilities.invokeAndWait { value = c.title }
        assertEquals("…", value)
    }

    @Test
    fun `reading an un-populated property off-EDT without a repo context throws`() {
        val c = Commit("d".repeat(40))   // repoTab == null
        assertThrows(Exception::class.java) { c.title }
    }

    @Test
    fun `graph and topology fields hold their assigned values`() {
        val c = Commit("e".repeat(40))
        c.x = 3; c.y = 7; c.color = Color.RED
        c.isCurrentHead = true
        c.refs = listOf(RefInfo("main", RefType.LOCAL_BRANCH))
        assertEquals(3, c.x)
        assertEquals(7, c.y)
        assertEquals(Color.RED, c.color)
        assertTrue(c.isCurrentHead)
        assertEquals("main", c.refs.single().name)
        assertEquals("e".repeat(40), c.toString())
    }
}

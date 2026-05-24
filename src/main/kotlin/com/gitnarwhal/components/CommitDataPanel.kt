package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.views.RepoTab
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class CommitDataPanel(private val repo: RepoTab) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    /**
     * Renders the detail pane for [commit].
     *
     * Pre-populated fields (shortHash, author, dates, title, committer) are shown
     * immediately — they are already cached from the git-log pass, so no I/O happens
     * on the EDT.
     *
     * The commit body (message) is fetched asynchronously via a SwingWorker so that
     * clicking a commit never blocks the UI.
     */
    fun showCommit(commit: Commit) {
        removeAll()

        // ── Immediately visible (pre-populated, no git call) ──────────────────
        add(row("Commit:", commit.hash + "  [", hashLink(commit.hash, commit.shortHash), "]"))

        val parentRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS); alignmentX = LEFT_ALIGNMENT
        }
        parentRow.add(bold("Parents: "))
        commit.parents.forEach { p ->
            parentRow.add(hashLink(p.hash, p.shortHash))
            parentRow.add(JLabel("  "))
        }
        add(parentRow)

        if (commit.committerDate != commit.authorDate) {
            add(row("Author:",      commit.author))
            add(row("Author Date:", commit.authorDate))
        }
        add(row("Committer:",      commit.committer))
        add(row("Committer Date:", commit.committerDate))
        add(Box.createVerticalStrut(8))
        add(wrappedText(commit.title, bold = true))

        // ── Body — fetched asynchronously ─────────────────────────────────────
        val bodyArea = wrappedText("", bold = false)
        add(bodyArea)

        revalidate()
        repaint()

        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                // git show outputs: shortHash\nauthor\n...\ntitle\nbody
                // Body starts at line index 6 (after the 6 pre-populated fields + title).
                val result = repo.git.show(commit)
                if (!result.success) return ""
                return result.output.lines().drop(6).joinToString("\n").trim()
            }
            override fun done() {
                val body = try { get() } catch (e: Exception) { return }
                if (body.isNotBlank()) {
                    (bodyArea as? JTextArea)?.text = body
                    revalidate()
                    repaint()
                }
            }
        }.execute()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun row(vararg parts: Any): JPanel {
        val p = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS); alignmentX = LEFT_ALIGNMENT }
        parts.forEachIndexed { i, part ->
            val comp: Component = when (part) {
                is Component -> part
                is String    -> if (i == 0) bold("$part ") else JLabel(part)
                else         -> JLabel(part.toString())
            }
            p.add(comp)
        }
        p.add(Box.createHorizontalGlue())
        return p
    }

    private fun bold(text: String): JLabel =
        JLabel(text).apply { font = font.deriveFont(Font.BOLD) }

    private fun hashLink(fullHash: String, label: String): JLabel =
        JLabel(label).apply {
            foreground = Color(0x4FC3F7)
            cursor     = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) { repo.selectCommit(fullHash) }
            })
        }

    private fun wrappedText(text: String, bold: Boolean): JComponent =
        JTextArea(text).apply {
            isEditable    = false
            lineWrap      = true
            wrapStyleWord = true
            isOpaque      = false
            border        = BorderFactory.createEmptyBorder(2, 0, 2, 0)
            alignmentX    = LEFT_ALIGNMENT
            if (bold) font = font.deriveFont(Font.BOLD)
        }
}

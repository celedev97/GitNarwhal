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

    fun showCommit(commit: Commit) {
        removeAll()

        add(row("Commit:", commit.hash + " [", hashLink(commit.hash, commit.shortHash), "]"))

        val parentRow = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS); alignmentX = LEFT_ALIGNMENT }
        parentRow.add(bold("Parents: "))
        commit.parents.forEach { parentCommit ->
            parentRow.add(hashLink(parentCommit.hash, parentCommit.shortHash))
            parentRow.add(JLabel(" "))
        }
        add(parentRow)

        if (commit.committerDate != commit.authorDate) {
            add(row("Author:", commit.author))
            add(row("Author Date:", commit.authorDate))
        }

        add(row("Committer:", commit.committer))
        add(row("Committer Date:", commit.committerDate))

        add(Box.createVerticalStrut(8))
        add(wrappedLabel(commit.title, bold = true))
        add(wrappedLabel(commit.message, bold = false))

        revalidate()
        repaint()
    }

    private fun row(vararg parts: Any): JPanel {
        val p = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS); alignmentX = LEFT_ALIGNMENT }
        parts.forEachIndexed { i, part ->
            val comp: Component = when (part) {
                is Component -> part
                is String -> if (i == 0) bold(part + " ") else JLabel(part)
                else -> JLabel(part.toString())
            }
            p.add(comp)
        }
        p.add(Box.createHorizontalGlue())
        return p
    }

    private fun bold(text: String): JLabel = JLabel(text).apply {
        font = font.deriveFont(Font.BOLD)
    }

    private fun hashLink(fullHash: String, label: String): JLabel {
        val l = JLabel(label)
        l.foreground = Color(0x4FC3F7)
        l.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        l.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                repo.selectCommit(fullHash)
            }
        })
        return l
    }

    private fun wrappedLabel(text: String, bold: Boolean): JComponent {
        val area = JTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
            if (bold) font = font.deriveFont(Font.BOLD)
        }
        area.alignmentX = LEFT_ALIGNMENT
        return area
    }
}

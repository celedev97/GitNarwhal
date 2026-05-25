package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.RefType
import com.gitnarwhal.views.RepoTab
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.swing.*

class CommitDataPanel(private val repo: RepoTab) : JPanel(BorderLayout()) {

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
        isOpaque = false
    }

    init {
        isOpaque = false
        add(contentPanel, BorderLayout.NORTH)
    }

    fun showCommit(commit: Commit) {
        contentPanel.removeAll()

        // ── 1. Hash row ───────────────────────────────────────────────────────
        val hashRow = flowRow()
        hashRow.add(hashChip(commit.shortHash))
        hashRow.add(smallLabel(commit.hash))
        hashRow.add(copyButton(commit.hash))
        contentPanel.add(hashRow)
        contentPanel.add(vgap(4))

        // ── 2. Ref badges ─────────────────────────────────────────────────────
        val visibleRefs = commit.refs.filter { it.type != RefType.HEAD }
        val headRef     = commit.refs.firstOrNull { it.type == RefType.HEAD }
        if (commit.refs.isNotEmpty()) {
            val refRow = flowRow()
            if (headRef != null) refRow.add(refBadge("HEAD", Color(0xE5_73_73)))
            visibleRefs.forEach { ref ->
                val bg = when (ref.type) {
                    RefType.LOCAL_BRANCH  -> Color(0x42_A5_F5)
                    RefType.REMOTE_BRANCH -> Color(0x66_BB_6A)
                    RefType.TAG           -> Color(0xFF_B3_00)
                    else                  -> Color(0x78_78_78)
                }
                refRow.add(refBadge(ref.name, bg))
            }
            contentPanel.add(refRow)
            contentPanel.add(vgap(4))
        }

        // ── 3. Commit title ───────────────────────────────────────────────────
        contentPanel.add(vgap(4))
        contentPanel.add(titleArea(commit.title))
        contentPanel.add(vgap(6))

        // ── 4. Body placeholder (async filled) ───────────────────────────────
        val bodyArea = bodyTextArea("")
        contentPanel.add(bodyArea)

        // ── 5. Separator ──────────────────────────────────────────────────────
        contentPanel.add(vgap(8))
        contentPanel.add(separator())
        contentPanel.add(vgap(6))

        // ── 6. Author / Committer ─────────────────────────────────────────────
        val sameCommitter = commit.committer == commit.author && commit.committerDate == commit.authorDate
        contentPanel.add(personRow("Authored by", commit.author, commit.authorDate))
        if (!sameCommitter) {
            contentPanel.add(vgap(2))
            contentPanel.add(personRow("Committed by", commit.committer, commit.committerDate))
        }

        // ── 7. Parents ────────────────────────────────────────────────────────
        if (commit.parents.isNotEmpty()) {
            contentPanel.add(vgap(6))
            val parentRow = flowRow()
            parentRow.add(metaLabel("Parents:  "))
            commit.parents.forEach { p ->
                parentRow.add(hashLink(p.hash, p.shortHash))
                parentRow.add(JLabel("  ").apply { isOpaque = false })
            }
            contentPanel.add(parentRow)
        }

        contentPanel.revalidate()
        contentPanel.repaint()

        // ── Async: fetch body ─────────────────────────────────────────────────
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                val result = repo.git.show(commit)
                if (!result.success) return ""
                return result.output.lines().drop(6).joinToString("\n").trim()
            }
            override fun done() {
                val body = try { get() } catch (_: Exception) { return }
                if (body.isNotBlank()) {
                    (bodyArea as? JTextArea)?.let { ta ->
                        ta.text = body
                        ta.isVisible = true
                    }
                    contentPanel.revalidate()
                    contentPanel.repaint()
                }
            }
        }.execute()
    }

    // ── Component builders ────────────────────────────────────────────────────

    private fun hashChip(text: String): JLabel {
        val accent = UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)
        return JLabel(" $text ").apply {
            font       = Font(Font.MONOSPACED, Font.BOLD, 12)
            foreground = Color.WHITE
            background = accent
            isOpaque   = true
            border     = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
    }

    private fun smallLabel(text: String): JLabel =
        JLabel(text).apply {
            font       = Font(Font.MONOSPACED, Font.PLAIN, 11)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }

    private fun metaLabel(text: String): JLabel =
        JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }

    private fun copyButton(hash: String): JLabel =
        JLabel("⎘").apply {
            font       = font.deriveFont(12f)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            cursor     = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Copy full hash"
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val sel = StringSelection(hash)
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                }
                override fun mouseEntered(e: MouseEvent) {
                    foreground = UIManager.getColor("Label.foreground") ?: Color.WHITE
                }
                override fun mouseExited(e: MouseEvent) {
                    foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                }
            })
        }

    private fun refBadge(name: String, bg: Color): JLabel =
        JLabel(" $name ").apply {
            font       = font.deriveFont(Font.BOLD, 10f)
            foreground = Color.WHITE
            background = bg
            isOpaque   = true
            border     = BorderFactory.createEmptyBorder(1, 5, 1, 5)
        }

    private fun titleArea(text: String): JTextArea =
        JTextArea(text).apply {
            font          = font.deriveFont(Font.BOLD, 13f)
            isEditable    = false
            lineWrap      = true
            wrapStyleWord = true
            isOpaque      = false
            border        = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            alignmentX    = LEFT_ALIGNMENT
            foreground    = UIManager.getColor("Label.foreground") ?: Color.WHITE
        }

    private fun bodyTextArea(text: String): JTextArea =
        JTextArea(text).apply {
            font          = font.deriveFont(12f)
            isEditable    = false
            lineWrap      = true
            wrapStyleWord = true
            isOpaque      = false
            border        = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            alignmentX    = LEFT_ALIGNMENT
            foreground    = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            isVisible     = text.isNotBlank()
        }

    private fun personRow(label: String, person: String, rawDate: String): JPanel {
        val row = flowRow()
        row.add(metaLabel("$label  "))

        // parse "Name <email>" — show name bold, email smaller
        val emailMatch = Regex("""^(.*?)\s*<([^>]+)>$""").find(person)
        if (emailMatch != null) {
            val (name, email) = emailMatch.destructured
            row.add(JLabel(name.trim()).apply { font = font.deriveFont(Font.BOLD, 12f) })
            row.add(JLabel("  <$email>  ").apply {
                font = font.deriveFont(11f)
                foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            })
        } else {
            row.add(JLabel(person).apply { font = font.deriveFont(Font.BOLD, 12f) })
        }
        row.add(JLabel(formatDate(rawDate)).apply {
            font = font.deriveFont(11f)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        })
        return row
    }

    private fun hashLink(fullHash: String, label: String): JLabel =
        JLabel(label).apply {
            font       = Font(Font.MONOSPACED, Font.PLAIN, 11)
            foreground = UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)
            cursor     = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border     = BorderFactory.createLineBorder(
                (UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)).darker(), 1, true)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) { repo.selectCommit(fullHash) }
            })
        }

    private fun separator(): JSeparator =
        JSeparator(SwingConstants.HORIZONTAL).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = java.awt.Dimension(Int.MAX_VALUE, 1)
        }

    private fun flowRow(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque   = false
        }

    private fun vgap(h: Int): Component = Box.createVerticalStrut(h)

    // ── Date formatting ───────────────────────────────────────────────────────

    private val INPUT_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val OUT_FMT   = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm")
                                .withZone(ZoneId.systemDefault())

    private fun formatDate(raw: String): String {
        return try {
            // raw is either "yyyy-MM-dd HH:mm:ss" (from GitShow) or a unix epoch string
            val epochSec = raw.toLongOrNull()
            val instant  = if (epochSec != null) Instant.ofEpochSecond(epochSec)
                           else INPUT_FMT.parse(raw).toInstant()
            val now      = Instant.now()
            val diffMin  = ChronoUnit.MINUTES.between(instant, now)
            when {
                diffMin < 1    -> "just now"
                diffMin < 60   -> "$diffMin minutes ago"
                diffMin < 1440 -> "${diffMin / 60} hours ago"
                diffMin < 2880 -> "yesterday"
                else           -> OUT_FMT.format(instant)
            }
        } catch (_: Exception) { raw }
    }
}

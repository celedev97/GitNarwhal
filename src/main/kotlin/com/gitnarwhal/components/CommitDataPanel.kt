package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.RefType
import com.gitnarwhal.views.RepoTab
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class CommitDataPanel(private val repo: RepoTab) : JPanel(BorderLayout()) {

    private var currentCommit: Commit? = null

    private val badgePanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(4, 8, 0, 8)
    }

    // Single selectable text pane for all metadata + message
    private val textPane = JTextPane().apply {
        isEditable = false
        border = BorderFactory.createEmptyBorder(6, 10, 8, 10)
        isOpaque = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        isOpaque = false
        val top = JPanel(BorderLayout()).apply { isOpaque = false }
        top.add(badgePanel, BorderLayout.CENTER)
        add(top, BorderLayout.NORTH)
        add(textPane, BorderLayout.CENTER)

        // Click on colored link spans (parent hashes)
        textPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val pos = textPane.viewToModel2D(e.point).toInt()
                val attrs = textPane.styledDocument.getCharacterElement(pos).attributes
                val href = attrs.getAttribute("linkHref") as? String ?: return
                repo.selectCommit(href)
            }
        })

        // Right-click context menu for quick copy
        textPane.componentPopupMenu = JPopupMenu().apply {
            add(JMenuItem("Copy Commit Hash").apply {
                addActionListener {
                    currentCommit?.hash?.let { copy(it) }
                }
            })
            add(JMenuItem("Copy Short Hash").apply {
                addActionListener {
                    currentCommit?.shortHash?.let { copy(it) }
                }
            })
            add(JMenuItem("Copy Author").apply {
                addActionListener {
                    currentCommit?.author?.let { copy(it) }
                }
            })
            addSeparator()
            add(JMenuItem("Copy").apply {
                addActionListener { textPane.copy() }
            })
        }
    }

    private fun copy(text: String) =
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

    fun showCommit(commit: Commit) {
        currentCommit = commit
        // ── Ref badges ────────────────────────────────────────────────────────
        badgePanel.removeAll()
        val headRef     = commit.refs.firstOrNull { it.type == RefType.HEAD }
        val visibleRefs = commit.refs.filter  { it.type != RefType.HEAD }
        if (headRef != null) badgePanel.add(badge("HEAD", Color(0xE5_73_73)))
        visibleRefs.forEach { ref ->
            val bg = when (ref.type) {
                RefType.LOCAL_BRANCH  -> Color(0x3E_C4_BD)
                RefType.REMOTE_BRANCH -> Color(0x66_BB_6A)
                RefType.TAG           -> Color(0xFF_B3_00)
                else                  -> Color(0x78_78_78)
            }
            badgePanel.add(badge(ref.name, bg))
        }

        // ── Text pane: immediate metadata ────────────────────────────────────
        val doc = textPane.styledDocument
        doc.remove(0, doc.length)

        val bold   = boldAttr()
        val normal = normalAttr()
        val muted  = mutedAttr()

        fun line(label: String, value: String) {
            doc.insertString(doc.length, label,  bold)
            doc.insertString(doc.length, value,  normal)
            doc.insertString(doc.length, "\n",   normal)
        }

        line("Commit:     ", "${commit.hash}  [${commit.shortHash}]")

        // Parents — inserted as plain text (links added via HTML approach below)
        doc.insertString(doc.length, "Parents:    ", bold)
        commit.parents.forEachIndexed { i, p ->
            if (i > 0) doc.insertString(doc.length, "  ", normal)
            insertLink(doc, p.shortHash, p.hash)
        }
        doc.insertString(doc.length, "\n", normal)

        line("Author:     ", commit.author)
        line("Date:       ", formatDate(commit.authorDate))

        if (commit.committer != commit.author) {
            val committerName = commit.committer.substringBefore(" <").trim()
            line("Committer:  ", committerName)
        }

        // title on its own line after a blank
        doc.insertString(doc.length, "\n", normal)
        doc.insertString(doc.length, commit.title + "\n", boldTitle())

        // Body — async
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                val result = repo.git.show(commit)
                if (!result.success) return ""
                return result.output.lines().drop(6).joinToString("\n").trim()
            }
            override fun done() {
                val body = try { get() } catch (_: Exception) { return }
                if (body.isNotBlank()) {
                    doc.insertString(doc.length, "\n" + body + "\n", mutedAttr())
                }
                badgePanel.revalidate(); badgePanel.repaint()
                textPane.revalidate();   textPane.repaint()
                textPane.caretPosition = 0
            }
        }.execute()

        badgePanel.revalidate(); badgePanel.repaint()
        textPane.revalidate();   textPane.repaint()
        textPane.caretPosition = 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun badge(name: String, bg: Color): JLabel =
        JLabel(" $name ").apply {
            font       = font.deriveFont(Font.BOLD, 10f)
            foreground = Color.WHITE
            background = bg
            isOpaque   = true
            border     = BorderFactory.createEmptyBorder(1, 5, 2, 5)
        }

    private fun insertLink(doc: StyledDocument, label: String, href: String) {
        val accent = UIManager.getColor("Component.accentColor") ?: Color(0x3E_C4_BD)
        val attrs = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, accent)
            StyleConstants.setUnderline(this, false)
            StyleConstants.setBold(this, false)
            StyleConstants.setFontFamily(this, Font.MONOSPACED)
            StyleConstants.setFontSize(this, 12)
        }
        // Store href as attribute so click listener can read it
        attrs.addAttribute("linkHref", href)
        doc.insertString(doc.length, label, attrs)
    }

    private fun boldAttr(): SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setBold(this, true)
        StyleConstants.setFontFamily(this, Font.MONOSPACED)
        StyleConstants.setFontSize(this, 12)
        StyleConstants.setForeground(this,
            UIManager.getColor("Label.foreground") ?: Color(0xE8_EA_F2))
    }

    private fun normalAttr(): SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setBold(this, false)
        StyleConstants.setFontFamily(this, Font.MONOSPACED)
        StyleConstants.setFontSize(this, 12)
        StyleConstants.setForeground(this,
            UIManager.getColor("Label.foreground") ?: Color(0xE8_EA_F2))
    }

    private fun mutedAttr(): SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setBold(this, false)
        StyleConstants.setFontFamily(this, Font.MONOSPACED)
        StyleConstants.setFontSize(this, 12)
        StyleConstants.setForeground(this,
            UIManager.getColor("Label.disabledForeground") ?: Color(0x8B_8F_A8))
    }

    private fun boldTitle(): SimpleAttributeSet = SimpleAttributeSet().apply {
        StyleConstants.setBold(this, true)
        StyleConstants.setFontFamily(this, Font.SANS_SERIF)
        StyleConstants.setFontSize(this, 13)
        StyleConstants.setForeground(this,
            UIManager.getColor("Label.foreground") ?: Color(0xE8_EA_F2))
    }

    private val INPUT_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val OUT_FMT = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    private fun formatDate(raw: String): String {
        return try {
            val epochSec = raw.toLongOrNull()
            val instant  = if (epochSec != null) Instant.ofEpochSecond(epochSec)
                           else INPUT_FMT.parse(raw).toInstant()
            OUT_FMT.format(instant)
        } catch (_: Exception) { raw }
    }
}

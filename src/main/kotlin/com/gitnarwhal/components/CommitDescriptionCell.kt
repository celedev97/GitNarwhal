package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.RefType
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.table.DefaultTableCellRenderer

class CommitDescriptionCell : DefaultTableCellRenderer() {

    private var commit: Commit? = null
    private var isRowSelected = false

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
        commit = value as? Commit
        isRowSelected = isSelected
        return this
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val c = commit ?: return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val midY = height / 2
            var x = 4

            // ── Ref pills ─────────────────────────────────────────────────────
            // Remote branches that share a name with a local branch are merged into a single combined chip
            val consumedRemotes = buildSet<String> {
                for (ref in c.refs) {
                    if (ref.type != RefType.LOCAL_BRANCH) continue
                    c.refs.filter { it.type == RefType.REMOTE_BRANCH && it.name.substringAfter("/") == ref.name }
                          .forEach { add(it.name) }
                }
            }
            val localBranchesWithRemote = consumedRemotes.map { it.substringAfter("/") }.toSet()

            for (ref in c.refs) {
                if (ref.type == RefType.REMOTE_BRANCH && ref.name in consumedRemotes) continue

                val (bg, fg) = when (ref.type) {
                    RefType.HEAD          -> Color(0xC6, 0x77, 0x00) to Color.WHITE
                    RefType.LOCAL_BRANCH  -> Color(0x2E, 0x7D, 0x32) to Color.WHITE
                    RefType.REMOTE_BRANCH -> Color(0x1A, 0x23, 0x7E) to Color(0x90, 0xCA, 0xF9)
                    RefType.TAG           -> Color(0x5E, 0x35, 0xB1) to Color(0xCE, 0x93, 0xD8)
                }
                val isCurrent  = c.isCurrentHead && ref.type == RefType.LOCAL_BRANCH
                val showIcons  = ref.type == RefType.LOCAL_BRANCH && ref.name in localBranchesWithRemote
                g2.font = font.deriveFont(
                    if (isCurrent) Font.BOLD else Font.PLAIN,
                    (font.size - 2).toFloat()
                )
                val fm = g2.fontMetrics
                val ph = fm.height + 2
                val py = midY - ph / 2

                if (showIcons) {
                    val iconSize = (fm.height - 4).coerceAtLeast(8)
                    val ix1  = x + 4
                    val ix2  = ix1 + iconSize + 2
                    val textX = ix2 + iconSize + 4
                    val tw   = fm.stringWidth(ref.name)
                    val pw   = textX - x + tw + 5
                    g2.color = bg
                    g2.fillRoundRect(x, py, pw, ph, 6, 6)
                    FontIcon.of(MaterialDesign.MDI_LAPTOP, iconSize, fg).paintIcon(this, g2, ix1, py + (ph - iconSize) / 2)
                    FontIcon.of(MaterialDesign.MDI_CLOUD,  iconSize, fg).paintIcon(this, g2, ix2, py + (ph - iconSize) / 2)
                    g2.color = fg
                    g2.drawString(ref.name, textX, midY + fm.ascent / 2 - 1)
                    x += pw + 4
                } else {
                    val tw = fm.stringWidth(ref.name)
                    val pw = tw + 10
                    g2.color = bg
                    g2.fillRoundRect(x, py, pw, ph, 6, 6)
                    g2.color = fg
                    g2.drawString(ref.name, x + 5, midY + fm.ascent / 2 - 1)
                    x += pw + 4
                }
            }

            // ── Commit title ──────────────────────────────────────────────────
            g2.font  = if (c.isCurrentHead) font.deriveFont(Font.BOLD) else font
            val fm   = g2.fontMetrics
            g2.color = if (isRowSelected) UIManager.getColor("Table.selectionForeground") ?: Color.WHITE
                       else foreground

            var title = c.title
            val maxW  = width - x - 4
            if (maxW > 0 && fm.stringWidth(title) > maxW) {
                while (title.isNotEmpty() && fm.stringWidth("$title…") > maxW) title = title.dropLast(1)
                title += "…"
            }
            g2.drawString(title, x, midY + fm.ascent / 2 - 1)
        } finally {
            g2.dispose()
        }
    }
}

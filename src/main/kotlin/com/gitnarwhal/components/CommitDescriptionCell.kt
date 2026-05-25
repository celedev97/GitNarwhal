package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.RefType
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

            val midY     = height / 2
            val pillFont = font.deriveFont(Font.PLAIN, (font.size - 2).toFloat())
            var x = 4

            // ── Ref pills ─────────────────────────────────────────────────────
            for (ref in c.refs) {
                val (bg, fg) = when (ref.type) {
                    RefType.HEAD          -> Color(0xC6, 0x77, 0x00) to Color.WHITE
                    RefType.LOCAL_BRANCH  -> Color(0x2E, 0x7D, 0x32) to Color.WHITE
                    RefType.REMOTE_BRANCH -> Color(0x1A, 0x23, 0x7E) to Color(0x90, 0xCA, 0xF9)
                    RefType.TAG           -> Color(0x5E, 0x35, 0xB1) to Color(0xCE, 0x93, 0xD8)
                }
                g2.font = pillFont
                val fm  = g2.fontMetrics
                val tw  = fm.stringWidth(ref.name)
                val pw  = tw + 10
                val ph  = fm.height + 2
                val py  = midY - ph / 2

                g2.color = bg
                g2.fillRoundRect(x, py, pw, ph, 6, 6)
                g2.color = fg
                g2.drawString(ref.name, x + 5, midY + fm.ascent / 2 - 1)
                x += pw + 4
            }

            // ── Commit title ──────────────────────────────────────────────────
            g2.font  = font
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

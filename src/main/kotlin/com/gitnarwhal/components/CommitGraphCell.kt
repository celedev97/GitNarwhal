package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class CommitGraphCell : DefaultTableCellRenderer() {

    private var commit: Commit? = null

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
        commit = value as? Commit
        preferredSize = Dimension(80, table.rowHeight)
        return this
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val c = commit ?: return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val cy = height / 2
            val cx = 20 + (c.x.coerceAtLeast(0) * 16)
            val r  = 5
            g2.color = if (background == foreground) Color.GRAY else foreground
            g2.fillOval(cx - r, cy - r, r * 2, r * 2)
        } finally {
            g2.dispose()
        }
    }
}

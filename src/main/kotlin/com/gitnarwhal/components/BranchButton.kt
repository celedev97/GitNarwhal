package com.gitnarwhal.components

import com.gitnarwhal.views.RepoTab
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.SwingConstants

class BranchButton(
    val branchName: String,
    repo: RepoTab,
    selected: Boolean = false
) : JLabel(branchName, SwingConstants.LEFT) {

    var selected: Boolean = false
        set(value) {
            field = value
            putClientProperty("JComponent.outline", if (value) "focus" else null)
            font = font.deriveFont(if (value) java.awt.Font.BOLD else java.awt.Font.PLAIN)
            repaint()
        }

    var tracking: String? = null

    init {
        border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        this.selected = selected

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    if (repo.git.selectBranch(branchName).success) {
                        println("CHECKOUT: $branchName")
                        repo.refreshBranches()
                    }
                }
            }
        })
    }
}

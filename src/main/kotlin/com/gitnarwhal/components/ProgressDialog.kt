package com.gitnarwhal.components

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

/**
 * Modal progress dialog for long-running git operations.
 *
 * While running shows an indeterminate bar.
 * Call [finish] on EDT when done:
 *  - success + "Show output" unchecked → auto-closes
 *  - success + "Show output" checked   → stays open, shows output
 *  - failure                            → stays open, shows output automatically
 */
class ProgressDialog(
    parent: java.awt.Window?,
    title: String
) : JDialog(parent, title, ModalityType.APPLICATION_MODAL) {

    private val statusLabel  = JLabel(title)
    private val progressBar  = JProgressBar().apply { isIndeterminate = true }
    private val showOutputCk = JCheckBox("Show output")
    private val outputArea   = JTextArea(10, 60).apply {
        isEditable    = false
        font          = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap      = true
        wrapStyleWord = false
    }
    private val outputScroll = JScrollPane(outputArea).apply { isVisible = false }
    private val closeBtn     = JButton("Close").apply { isEnabled = false }

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        isResizable = true

        showOutputCk.addItemListener {
            outputScroll.isVisible = showOutputCk.isSelected
            pack()
            setLocationRelativeTo(owner)
        }
        closeBtn.addActionListener { dispose() }

        val top = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 16, 8, 16)
            add(statusLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.SOUTH)
        }
        val bottom = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(4, 16, 12, 16)
            add(showOutputCk)
            add(Box.createHorizontalGlue())
            add(closeBtn)
        }

        contentPane = JPanel(BorderLayout()).also {
            it.add(top,          BorderLayout.NORTH)
            it.add(outputScroll, BorderLayout.CENTER)
            it.add(bottom,       BorderLayout.SOUTH)
        }
        pack()
        size = Dimension(520, size.height.coerceAtLeast(130))
        setLocationRelativeTo(parent)
    }

    /**
     * Marks the operation done. Must be called on the EDT.
     * Auto-closes on success unless "Show output" is checked.
     * Keeps open on failure showing output.
     */
    fun finish(output: String, success: Boolean) {
        progressBar.isIndeterminate = false
        progressBar.value      = 100
        statusLabel.text       = if (success) "Done." else "Failed."
        outputArea.text        = output.trim()
        outputArea.caretPosition = 0
        closeBtn.isEnabled     = true

        val keepOpen = !success || showOutputCk.isSelected
        if (keepOpen) {
            outputScroll.isVisible  = true
            showOutputCk.isSelected = true
            pack()
            size = Dimension(size.width.coerceAtLeast(520), 420)
            setLocationRelativeTo(owner)
        } else {
            dispose()
        }
    }
}

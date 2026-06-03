package com.gitnarwhal.components

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.*

/**
 * Global handler for uncaught exceptions (background threads AND the EDT — modern
 * JDKs route EDT exceptions through the thread's default uncaught handler too).
 *
 * Shows the user the full stack trace in a copyable dialog instead of silently
 * dumping it to stderr where they'll never see it.
 */
object ExceptionReporter {

    @Volatile private var showing = false

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Still log to stderr for terminal/log capture.
            System.err.println("Uncaught exception on '${thread.name}':")
            throwable.printStackTrace()
            show(thread.name, throwable)
        }
    }

    /** Public so call sites can surface a caught-but-unexpected exception explicitly. */
    fun show(threadName: String?, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        val run = Runnable {
            // Avoid stacking a dialog per exception during an error storm.
            if (showing) return@Runnable
            showing = true
            try {
                val area = JTextArea(trace).apply {
                    isEditable = false
                    font       = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    caretPosition = 0
                }
                val scroll = JScrollPane(area).apply { preferredSize = Dimension(760, 360) }

                val header = JLabel(
                    "<html><b>An unexpected error occurred.</b><br>" +
                    "${throwable.javaClass.name}: ${(throwable.message ?: "").take(200)}</html>"
                ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

                val panel = JPanel(BorderLayout()).apply {
                    add(header, BorderLayout.NORTH)
                    add(scroll, BorderLayout.CENTER)
                }

                val owner  = activeWindow()
                val dialog = JDialog(owner, "GitNarwhal — Error", java.awt.Dialog.ModalityType.MODELESS)

                val copyBtn = JButton("Copy to clipboard").apply {
                    addActionListener {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(trace), null)
                        text = "Copied"
                    }
                }
                val closeBtn = JButton("Close").apply { addActionListener { dialog.dispose() } }

                val buttons = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    add(copyBtn)
                    add(Box.createHorizontalGlue())
                    add(closeBtn)
                }

                dialog.apply {
                    defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                    contentPane = JPanel(BorderLayout(0, 8)).apply {
                        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
                        add(panel,   BorderLayout.CENTER)
                        add(buttons, BorderLayout.SOUTH)
                    }
                    addWindowListener(object : java.awt.event.WindowAdapter() {
                        override fun windowClosed(e: java.awt.event.WindowEvent) { showing = false }
                    })
                    pack()
                    setLocationRelativeTo(owner)
                    isVisible = true
                }
            } catch (t: Throwable) {
                // Never let the reporter itself crash the handler.
                showing = false
                System.err.println("ExceptionReporter failed: ${t.message}")
            }
        }
        if (SwingUtilities.isEventDispatchThread()) run.run() else SwingUtilities.invokeLater(run)
    }

    private fun activeWindow(): java.awt.Window? =
        java.awt.Window.getWindows().firstOrNull { it.isVisible && it is JFrame }
}

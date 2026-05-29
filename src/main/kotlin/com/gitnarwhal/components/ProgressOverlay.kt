package com.gitnarwhal.components

import java.awt.*
import java.awt.event.MouseAdapter
import javax.swing.*

/**
 * In-app progress overlay: replaces ProgressDialog with a glass-pane overlay
 * anchored to the main window. No separate OS window.
 *
 * Usage:
 *   val overlay = ProgressOverlay()
 *   overlay.show(SwingUtilities.getRootPane(this), "Pushing…")
 *   // on EDT when done:
 *   overlay.finish(output, success)
 */
class ProgressOverlay : JPanel(null) {

    private val statusLabel  = JLabel("Working…")
    private val progressBar  = JProgressBar().apply { isIndeterminate = true }
    private val showOutputCk = JCheckBox("Show output")
    private val outputArea   = JTextArea(8, 60).apply {
        isEditable    = false
        font          = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap      = true
        wrapStyleWord = false
    }
    private val outputScroll = JScrollPane(outputArea).apply { isVisible = false }
    private val closeBtn     = JButton("Close").apply { isEnabled = false }

    private val card = JPanel(BorderLayout(0, 8)).apply {
        background = UIManager.getColor("Panel.background") ?: Color(0x2B, 0x2B, 0x2B)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                UIManager.getColor("Separator.foreground") ?: Color(0x55, 0x55, 0x55)
            ),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)
        )
        val top = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.SOUTH)
        }
        val bottom = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(showOutputCk)
            add(Box.createHorizontalGlue())
            add(closeBtn)
        }
        add(top,          BorderLayout.NORTH)
        add(outputScroll, BorderLayout.CENTER)
        add(bottom,       BorderLayout.SOUTH)
    }

    init {
        isOpaque = false
        add(card)

        showOutputCk.addItemListener {
            outputScroll.isVisible = showOutputCk.isSelected
            repositionCard()
        }
        closeBtn.addActionListener { dismiss() }

        // Block all mouse events on the backdrop so the UI underneath is frozen
        addMouseListener(object : MouseAdapter() {})
        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {})
    }

    private var savedGlassPane: Component? = null
    private var rootPane: JRootPane?       = null
    private var onDismiss: (() -> Unit)?   = null

    fun show(rp: JRootPane, title: String, onDismiss: (() -> Unit)? = null) {
        this.rootPane  = rp
        this.onDismiss = onDismiss

        statusLabel.text          = title
        progressBar.isIndeterminate = true
        closeBtn.isEnabled        = false
        outputScroll.isVisible    = false
        showOutputCk.isSelected   = false
        outputArea.text           = ""

        savedGlassPane = rp.glassPane
        rp.glassPane   = this
        isVisible      = true
        repositionCard()
    }

    fun finish(output: String, success: Boolean) {
        progressBar.isIndeterminate = false
        progressBar.value      = 100
        statusLabel.text       = if (success) "Done." else "Failed."
        outputArea.text        = output.trim()
        outputArea.caretPosition = 0
        closeBtn.isEnabled     = true

        if (!success || showOutputCk.isSelected) {
            outputScroll.isVisible  = true
            showOutputCk.isSelected = true
            repositionCard()
        } else {
            dismiss()
        }
    }

    /** Append a line of streaming output; makes the output panel visible immediately. */
    fun appendOutput(line: String) {
        if (!outputScroll.isVisible) {
            outputScroll.isVisible = true
            repositionCard()
        }
        outputArea.append(line + "\n")
        outputArea.caretPosition = outputArea.document.length
    }

    /** Call instead of [finish] when output was already streamed via [appendOutput]. */
    fun finishStreaming(success: Boolean) {
        progressBar.isIndeterminate = false
        progressBar.value  = 100
        statusLabel.text   = if (success) "Done." else "Failed."
        closeBtn.isEnabled = true
        if (!success) {
            outputScroll.isVisible  = true
            showOutputCk.isSelected = true
            repositionCard()
        } else if (!showOutputCk.isSelected) {
            dismiss()   // success, user didn't pin output → auto-close
        }
    }

    override fun doLayout() {
        super.doLayout()
        repositionCard()
    }

    override fun paintComponent(g: Graphics) {
        (g as Graphics2D).color = Color(0, 0, 0, 160)
        g.fillRect(0, 0, width, height)
    }

    private fun repositionCard() {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val cardW = (w - 80).coerceAtLeast(400)
        val cardH = if (outputScroll.isVisible) (h - 80).coerceAtLeast(200)
                    else 140
        card.setBounds((w - cardW) / 2, (h - cardH) / 2, cardW, cardH)
        revalidate()
        repaint()
    }

    private fun dismiss() {
        val rp = rootPane ?: return
        isVisible    = false
        rp.glassPane = savedGlassPane
        savedGlassPane?.isVisible = false
        rootPane     = null
        onDismiss?.invoke()
    }
}

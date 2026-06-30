package com.gitnarwhal.components

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.net.URI
import javax.swing.*
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

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

    private val urlRegex   = Regex("""https?://\S+""")
    private object UrlAttrKey

    // ANSI parser state — persists across appendOutput calls to handle multi-line streams
    private var ansiFg: Color? = null
    private var ansiBold = false

    private val statusLabel  = JLabel("Working…")
    private val progressBar  = JProgressBar().apply { isIndeterminate = true }
    private val defaultProgressColor = progressBar.foreground
    private val showOutputCk = JCheckBox("Show output")
    private val outputPane   = JTextPane().apply {
        isEditable = false
        font       = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val outputScroll = JScrollPane(outputPane).apply { isVisible = false }
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

        outputPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val pos   = outputPane.viewToModel2D(e.point).toInt()
                val url   = outputPane.styledDocument.getCharacterElement(pos)
                    .attributes.getAttribute(UrlAttrKey) as? String ?: return
                runCatching { Desktop.getDesktop().browse(URI(url)) }
            }
        })
        outputPane.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val pos    = outputPane.viewToModel2D(e.point).toInt()
                val hasUrl = outputPane.styledDocument.getCharacterElement(pos)
                    .attributes.getAttribute(UrlAttrKey) != null
                outputPane.cursor = if (hasUrl) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                    else Cursor.getDefaultCursor()
            }
        })
    }

    private var savedGlassPane: Component? = null
    private var rootPane: JRootPane?       = null
    private var onDismiss: (() -> Unit)?   = null

    fun show(rp: JRootPane, title: String, onDismiss: (() -> Unit)? = null) {
        this.rootPane  = rp
        this.onDismiss = onDismiss

        statusLabel.text            = title
        progressBar.isIndeterminate = true
        progressBar.foreground      = defaultProgressColor
        closeBtn.isEnabled          = false
        outputScroll.isVisible    = false
        showOutputCk.isSelected   = false
        outputPane.text           = ""
        ansiFg = null; ansiBold = false

        savedGlassPane = rp.glassPane
        rp.glassPane   = this
        isVisible      = true
        repositionCard()
    }

    fun finish(output: String, success: Boolean) {
        progressBar.isIndeterminate = false
        progressBar.value      = 100
        progressBar.foreground = if (success) Color(0x4C, 0xAF, 0x50) else Color(0xF4, 0x43, 0x36)
        statusLabel.text       = if (success) "Done." else "Failed."
        ansiFg = null; ansiBold = false
        outputPane.text = ""
        appendAnsiText(output.trim())
        outputPane.caretPosition = 0
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
        appendAnsiText(line + "\n")
        outputPane.caretPosition = outputPane.document.length
    }

    private val ansiRegex = Regex("""\[([\d;]*)m""")

    private fun appendAnsiText(text: String) {
        var pos = 0
        for (match in ansiRegex.findAll(text)) {
            if (match.range.first > pos) insertStyled(text.substring(pos, match.range.first))
            applyAnsiCode(match.groupValues[1])
            pos = match.range.last + 1
        }
        if (pos < text.length) insertStyled(text.substring(pos))
    }

    private fun applyAnsiCode(params: String) {
        val codes = if (params.isEmpty()) listOf(0) else params.split(";").mapNotNull { it.toIntOrNull() }
        for (code in codes) {
            when (code) {
                0    -> { ansiFg = null; ansiBold = false }
                1    -> ansiBold = true
                22   -> ansiBold = false
                30   -> ansiFg = Color(0x1C, 0x1C, 0x1C)
                31   -> ansiFg = Color(0xCC, 0x00, 0x00)
                32   -> ansiFg = Color(0x00, 0xAA, 0x00)
                33   -> ansiFg = Color(0xAA, 0xAA, 0x00)
                34   -> ansiFg = Color(0x00, 0x55, 0xCC)
                35   -> ansiFg = Color(0xAA, 0x00, 0xAA)
                36   -> ansiFg = Color(0x00, 0xAA, 0xAA)
                37   -> ansiFg = Color(0xCC, 0xCC, 0xCC)
                39   -> ansiFg = null
                90   -> ansiFg = Color(0x88, 0x88, 0x88)
                91   -> ansiFg = Color(0xFF, 0x55, 0x55)
                92   -> ansiFg = Color(0x55, 0xFF, 0x55)
                93   -> ansiFg = Color(0xFF, 0xFF, 0x55)
                94   -> ansiFg = Color(0x55, 0x55, 0xFF)
                95   -> ansiFg = Color(0xFF, 0x55, 0xFF)
                96   -> ansiFg = Color(0x55, 0xFF, 0xFF)
                97   -> ansiFg = Color(0xFF, 0xFF, 0xFF)
            }
        }
    }

    private fun insertStyled(text: String) {
        val attrs    = SimpleAttributeSet()
        ansiFg?.let { StyleConstants.setForeground(attrs, it) }
        if (ansiBold) StyleConstants.setBold(attrs, true)
        val doc      = outputPane.styledDocument
        val insertAt = doc.length
        doc.insertString(insertAt, text, attrs)
        applyUrlStyles(insertAt, text)
    }

    private fun applyUrlStyles(startOffset: Int, text: String) {
        for (match in urlRegex.findAll(text)) {
            val attrs = SimpleAttributeSet()
            StyleConstants.setForeground(attrs, Color(0x55, 0x88, 0xFF))
            StyleConstants.setUnderline(attrs, true)
            attrs.addAttribute(UrlAttrKey, match.value)
            outputPane.styledDocument.setCharacterAttributes(
                startOffset + match.range.first, match.value.length, attrs, false
            )
        }
    }

    /** Call instead of [finish] when output was already streamed via [appendOutput]. */
    fun finishStreaming(success: Boolean) {
        progressBar.isIndeterminate = false
        progressBar.value  = 100
        progressBar.foreground = if (success) Color(0x4C, 0xAF, 0x50) else Color(0xF4, 0x43, 0x36)
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

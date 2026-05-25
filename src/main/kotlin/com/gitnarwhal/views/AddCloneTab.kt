package com.gitnarwhal.views

import com.gitnarwhal.components.AddCloneTab.AddTab
import com.gitnarwhal.components.AddCloneTab.CloneTab
import com.gitnarwhal.components.AddCloneTab.CreateTab
import com.gitnarwhal.utils.NativeFileChooser
import com.gitnarwhal.utils.toPath
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.*

class AddCloneTab(val mainView: MainView) : JPanel(BorderLayout()) {

    val tabTitle: String = "New Tab"

    private val cards     = CardLayout()
    private val container = JPanel(cards)

    val cloneTab  = CloneTab(this)
    val addTab    = AddTab(this)
    val createTab = CreateTab(this)

    private val cloneBtn  = NavTabButton("Clone",  MaterialDesign.MDI_DOWNLOAD)
    private val addBtn    = NavTabButton("Add",    MaterialDesign.MDI_FOLDER_PLUS)
    private val createBtn = NavTabButton("Create", MaterialDesign.MDI_PLUS)

    // kept for external callers that use the old button references
    val activateCloneTab:  JButton get() = cloneBtn
    val activateAddTab:    JButton get() = addBtn
    val activateCreateTab: JButton get() = createBtn

    /** Shared path field — visible only when Add or Create tab is active. */
    val sharedPathField = JTextField(40)
    private val pathRow = buildPathRow()

    init {
        container.add(cloneTab,  CARD_CLONE)
        container.add(addTab,    CARD_ADD)
        container.add(createTab, CARD_CREATE)

        val navBar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground") ?: Color(0x44_44_44))
        }
        navBar.add(Box.createHorizontalStrut(8))
        navBar.add(cloneBtn)
        navBar.add(addBtn)
        navBar.add(createBtn)
        navBar.add(Box.createHorizontalGlue())

        val north = JPanel(BorderLayout())
        north.add(navBar,   BorderLayout.NORTH)
        north.add(pathRow,  BorderLayout.SOUTH)

        cloneBtn.addActionListener  { switchTo(CARD_CLONE) }
        addBtn.addActionListener    { switchTo(CARD_ADD) }
        createBtn.addActionListener { switchTo(CARD_CREATE) }

        add(north,     BorderLayout.NORTH)
        add(container, BorderLayout.CENTER)

        switchTo(CARD_CLONE)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun switchTab(buttonOrCard: Any) {
        val card = when (buttonOrCard) {
            cloneBtn  -> CARD_CLONE
            addBtn    -> CARD_ADD
            createBtn -> CARD_CREATE
            is String -> buttonOrCard
            else      -> CARD_CLONE
        }
        switchTo(card)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun switchTo(card: String) {
        cards.show(container, card)
        val active = when (card) {
            CARD_CLONE  -> cloneBtn
            CARD_ADD    -> addBtn
            CARD_CREATE -> createBtn
            else        -> cloneBtn
        }
        listOf(cloneBtn, addBtn, createBtn).forEach { it.setNavSelected(it == active) }
        // path row only relevant for Add / Create
        pathRow.isVisible = card != CARD_CLONE
        revalidate()
    }

    private fun buildPathRow(): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6))
        row.add(JLabel("Path:"))
        row.add(sharedPathField)
        val browseBtn = JButton("Browse…")
        browseBtn.addActionListener {
            val win = SwingUtilities.getWindowAncestor(this)
            val dir = NativeFileChooser.chooseDirectory(win, "Select Folder") ?: return@addActionListener
            sharedPathField.text = dir.absolutePath
            // auto-fill name in AddTab from folder name
            addTab.nameField.text = dir.toPath().fileName?.toString() ?: ""
        }
        row.add(browseBtn)
        row.isVisible = false
        return row
    }

    // ── NavTabButton ──────────────────────────────────────────────────────────

    private inner class NavTabButton(label: String, ikon: Ikon) : JButton() {

        private val fontIcon = FontIcon.of(ikon, 22, unselectedColor())

        init {
            text                   = label
            icon                   = fontIcon
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition   = SwingConstants.BOTTOM
            isBorderPainted        = false
            isContentAreaFilled    = false
            isFocusPainted         = false
            preferredSize          = Dimension(80, 58)
            maximumSize            = Dimension(80, 58)
            font                   = font.deriveFont(11f)
            foreground             = unselectedColor()
            cursor                 = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        }

        fun setNavSelected(sel: Boolean) {
            val color = if (sel) accentColor() else unselectedColor()
            fontIcon.iconColor = color
            foreground         = color
            putClientProperty("selected", sel)
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (getClientProperty("selected") == true) {
                g.color = accentColor()
                g.fillRect(0, height - 3, width, 3)
            }
        }

        private fun accentColor(): Color =
            UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)

        private fun unselectedColor(): Color =
            UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY
    }

    companion object {
        const val CARD_CLONE  = "clone"
        const val CARD_ADD    = "add"
        const val CARD_CREATE = "create"
    }
}

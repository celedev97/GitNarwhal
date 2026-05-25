package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.nio.file.Files
import javax.swing.*

class AddTab(private val addCloneTab: AddCloneTab) : JPanel(BorderLayout()) {

    internal val pathField = JTextField()
    val nameField         = JTextField()

    init {
        isOpaque = false

        val form = JPanel().apply {
            isOpaque   = false
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = 0f
        }

        form.add(title("Add a repository"))
        form.add(Box.createVerticalStrut(6))
        form.add(subtitle("Choose a working copy repository folder to add to GitNarwhal"))
        form.add(Box.createVerticalStrut(28))

        pathField.putClientProperty("JTextField.placeholderText", "Working Copy Path:")
        val browseBtn = JButton("Browse")
        browseBtn.addActionListener {
            addCloneTab.browseForPath(this) { dir ->
                nameField.text = dir.toPath().fileName?.toString() ?: ""
            }
        }
        form.add(pathRow(pathField, browseBtn))
        form.add(Box.createVerticalStrut(10))

        nameField.putClientProperty("JTextField.placeholderText", "Name:")
        nameField.alignmentX  = 0f
        nameField.maximumSize = Dimension(Int.MAX_VALUE, nameField.preferredSize.height)
        form.add(nameField)
        form.add(Box.createVerticalStrut(20))

        val addBtn = accentButton("Add")
        addBtn.alignmentX = 0f
        addBtn.addActionListener { run() }
        form.add(addBtn)

        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(40, 48, 40, 48)
            add(form, BorderLayout.NORTH)
        }, BorderLayout.CENTER)
    }

    fun run() {
        val pathText = pathField.text.trim()
        if (!Files.isDirectory(pathText.toPath())) {
            JOptionPane.showMessageDialog(this, "The specified path is not a directory", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        val openTabsPaths = Settings.openTabs.map { (it as JSONObject).getString("path") }
        if (openTabsPaths.contains(pathText)) {
            JOptionPane.showMessageDialog(this, "This repository is already open in another tab", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        with(addCloneTab.mainView) {
            val name = nameField.text.ifBlank { pathText.toPath().fileName?.toString() ?: pathText }
            val repo = RepoTab(pathText, name)
            addTab(repo); selectTab(repo); closeTab(addCloneTab)
        }
    }
}

// ── Shared helpers (package-private) ─────────────────────────────────────────

internal fun title(text: String): JLabel = JLabel(text).apply {
    font            = font.deriveFont(Font.BOLD, 26f)
    alignmentX      = 0f
    horizontalAlignment = SwingConstants.LEFT
}

internal fun subtitle(text: String): JLabel = JLabel(text).apply {
    font            = font.deriveFont(Font.PLAIN, 12f)
    alignmentX      = 0f
    horizontalAlignment = SwingConstants.LEFT
    foreground      = UIManager.getColor("Label.disabledForeground")
}

internal fun pathRow(field: JTextField, browseBtn: JButton): JPanel {
    field.maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height)
    return JPanel(BorderLayout(8, 0)).apply {
        isOpaque    = false
        alignmentX  = 0f
        maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height + 2)
        add(field,     BorderLayout.CENTER)
        add(browseBtn, BorderLayout.EAST)
    }
}

internal fun accentButton(text: String): JButton = JButton(text).apply {
    putClientProperty("FlatLaf.style",
        "background: #1A6FBF; foreground: #FFFFFF; hoverBackground: #2078CC; pressedBackground: #155DA0")
}

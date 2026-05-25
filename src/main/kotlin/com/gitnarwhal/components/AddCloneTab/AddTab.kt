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

    // path field backed by the shared document — stays in sync with CreateTab
    private val pathField = JTextField(addCloneTab.sharedPathDoc, "", 0)
    val nameField         = JTextField()

    init {
        isOpaque = false

        val form = JPanel().apply {
            isOpaque = false
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // ── Title ─────────────────────────────────────────────────────────────
        form.add(label("Add a repository", size = 26f, bold = true))
        form.add(Box.createVerticalStrut(6))
        form.add(label("Choose a working copy repository folder to add to GitNarwhal", size = 12f))
        form.add(Box.createVerticalStrut(28))

        // ── Path row ──────────────────────────────────────────────────────────
        pathField.putClientProperty("JTextField.placeholderText", "Working Copy Path:")
        val browseBtn = JButton("Browse")
        browseBtn.addActionListener {
            addCloneTab.browseForPath(this) { dir ->
                nameField.text = dir.toPath().fileName?.toString() ?: ""
            }
        }
        form.add(pathRow(pathField, browseBtn))
        form.add(Box.createVerticalStrut(10))

        // ── Name ──────────────────────────────────────────────────────────────
        nameField.putClientProperty("JTextField.placeholderText", "Name:")
        nameField.maximumSize = Dimension(Int.MAX_VALUE, nameField.preferredSize.height)
        form.add(nameField)
        form.add(Box.createVerticalStrut(20))

        // ── Action button ─────────────────────────────────────────────────────
        val addBtn = accentButton("Add")
        addBtn.addActionListener { run() }
        val btnRow = JPanel(BorderLayout()).apply { isOpaque = false }
        btnRow.add(addBtn, BorderLayout.WEST)
        form.add(btnRow)

        // ── Outer wrapper with padding ────────────────────────────────────────
        val outer = JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(40, 48, 40, 48)
        }
        outer.add(form, BorderLayout.NORTH)
        add(outer, BorderLayout.CENTER)
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

internal fun label(text: String, size: Float, bold: Boolean = false): JLabel =
    JLabel(text).apply {
        font      = font.deriveFont(if (bold) Font.BOLD else Font.PLAIN, size)
        alignmentX = 0f
    }

internal fun pathRow(field: JTextField, browseBtn: JButton): JPanel {
    field.maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height)
    val row = JPanel(BorderLayout(8, 0)).apply { isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, field.preferredSize.height + 2) }
    row.add(field,     BorderLayout.CENTER)
    row.add(browseBtn, BorderLayout.EAST)
    return row
}

internal fun accentButton(text: String): JButton =
    JButton(text).apply {
        putClientProperty("FlatLaf.style",
            "background: #1A6FBF; foreground: #FFFFFF; hoverBackground: #2078CC; pressedBackground: #155DA0")
    }

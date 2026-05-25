package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Files
import javax.swing.*

class CreateTab(private val addCloneTab: AddCloneTab) : JPanel(BorderLayout()) {

    // path field backed by the shared document — stays in sync with AddTab
    private val pathField = JTextField(addCloneTab.sharedPathDoc, "", 0)
    val nameField         = JTextField()

    init {
        isOpaque = false

        val form = JPanel().apply {
            isOpaque = false
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // ── Title ─────────────────────────────────────────────────────────────
        form.add(label("Create a repository", size = 26f, bold = true))
        form.add(Box.createVerticalStrut(28))

        // ── Path row ──────────────────────────────────────────────────────────
        pathField.putClientProperty("JTextField.placeholderText", "Destination Path:")
        val browseBtn = JButton("Browse")
        browseBtn.addActionListener { addCloneTab.browseForPath(this) }
        form.add(pathRow(pathField, browseBtn))
        form.add(Box.createVerticalStrut(10))

        // ── Name ──────────────────────────────────────────────────────────────
        nameField.putClientProperty("JTextField.placeholderText", "Name:")
        nameField.maximumSize = Dimension(Int.MAX_VALUE, nameField.preferredSize.height)
        form.add(nameField)
        form.add(Box.createVerticalStrut(20))

        // ── Action button ─────────────────────────────────────────────────────
        val createBtn = accentButton("Create")
        createBtn.addActionListener { run() }
        val btnRow = JPanel(BorderLayout()).apply { isOpaque = false }
        btnRow.add(createBtn, BorderLayout.WEST)
        form.add(btnRow)

        val outer = JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(40, 48, 40, 48)
        }
        outer.add(form, BorderLayout.NORTH)
        add(outer, BorderLayout.CENTER)
    }

    fun run() {
        val parent = pathField.text.trim()
        val name   = nameField.text.trim()
        if (parent.isBlank()) { err("Destination path is required"); return }
        if (name.isBlank())   { err("Name is required"); return }

        val parentPath = parent.toPath()
        if (!Files.isDirectory(parentPath)) { err("Path is not a directory"); return }

        val repoDir = File(parentPath.toFile(), name)
        if (repoDir.exists() && repoDir.list()?.isNotEmpty() == true) {
            err("Destination already exists and is non-empty: ${repoDir.absolutePath}"); return
        }
        if (!repoDir.exists() && !repoDir.mkdirs()) {
            err("Could not create directory: ${repoDir.absolutePath}"); return
        }

        val cmd = Git.Static.init(repoDir.absolutePath)
        if (!cmd.success) { err("git init failed:\n\n${cmd.output}"); return }

        with(addCloneTab.mainView) {
            val repo = RepoTab(repoDir.absolutePath, name)
            addTab(repo); selectTab(repo); closeTab(addCloneTab)
        }
    }

    private fun err(msg: String) =
        JOptionPane.showMessageDialog(this, msg, "Create", JOptionPane.ERROR_MESSAGE)
}

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

    internal val pathField = JTextField()
    val nameField         = JTextField()

    init {
        isOpaque = false

        val form = JPanel().apply {
            isOpaque   = false
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = 0f
        }

        form.add(title("Create a repository"))
        form.add(Box.createVerticalStrut(28))

        pathField.putClientProperty("JTextField.placeholderText", "Destination Path:")
        val browseBtn = JButton("Browse")
        browseBtn.addActionListener { addCloneTab.browseForPath(this) }
        form.add(pathRow(pathField, browseBtn))
        form.add(Box.createVerticalStrut(10))

        nameField.putClientProperty("JTextField.placeholderText", "Name:")
        nameField.alignmentX  = 0f
        nameField.maximumSize = Dimension(Int.MAX_VALUE, nameField.preferredSize.height)
        form.add(nameField)
        form.add(Box.createVerticalStrut(20))

        val createBtn = accentButton("Create")
        createBtn.alignmentX = 0f
        createBtn.addActionListener { run() }
        form.add(createBtn)

        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(40, 48, 40, 48)
            add(form, BorderLayout.NORTH)
        }, BorderLayout.CENTER)
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

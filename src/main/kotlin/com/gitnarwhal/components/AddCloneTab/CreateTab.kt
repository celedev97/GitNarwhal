package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.nio.file.Files
import javax.swing.*

class CreateTab(private val addCloneTab: AddCloneTab) : JPanel(GridBagLayout()) {

    /** Parent path comes from the shared field in AddCloneTab. */
    val nameField = JTextField(20)

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        val gbc = GridBagConstraints().apply {
            insets  = Insets(4, 4, 4, 4)
            fill    = GridBagConstraints.HORIZONTAL
            anchor  = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        add(JLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        add(nameField, gbc)
        gbc.gridwidth = 1

        gbc.gridx = 1; gbc.gridy = 1
        val runBtn = JButton("Create")
        runBtn.addActionListener { run() }
        add(runBtn, gbc)

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        add(JPanel().apply { isOpaque = false }, gbc)
    }

    fun run() {
        val parent = addCloneTab.sharedPathField.text.trim()
        val name   = nameField.text.trim()
        if (parent.isBlank()) { error("Parent path is required"); return }
        if (name.isBlank())   { error("Name is required"); return }

        val parentPath = parent.toPath()
        if (!Files.isDirectory(parentPath)) { error("Parent path is not a directory"); return }

        val repoDir = File(parentPath.toFile(), name)
        if (repoDir.exists() && repoDir.list()?.isNotEmpty() == true) {
            error("Destination already exists and is non-empty: ${repoDir.absolutePath}")
            return
        }
        if (!repoDir.exists() && !repoDir.mkdirs()) {
            error("Could not create directory: ${repoDir.absolutePath}")
            return
        }

        val cmd = Git.Static.init(repoDir.absolutePath)
        if (!cmd.success) {
            error("git init failed:\n\n${cmd.output}")
            return
        }

        with(addCloneTab.mainView) {
            val repo = RepoTab(repoDir.absolutePath, name)
            addTab(repo)
            selectTab(repo)
            closeTab(addCloneTab)
        }
    }

    private fun error(message: String) {
        JOptionPane.showMessageDialog(this, message, "Create", JOptionPane.ERROR_MESSAGE)
    }
}

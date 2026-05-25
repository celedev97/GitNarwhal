package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.nio.file.Files
import javax.swing.*

class CreateTab(private val addCloneTab: AddCloneTab) : JPanel(BorderLayout()) {

    val nameField = JTextField(28)

    init {
        isOpaque = false

        val form = JPanel(GridBagLayout())
        form.isOpaque = false
        form.maximumSize = Dimension(480, Int.MAX_VALUE)

        val gbc = GridBagConstraints().apply {
            insets  = Insets(6, 6, 6, 6)
            anchor  = GridBagConstraints.WEST
        }

        // row 0: Name label + field
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        form.add(JLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        form.add(nameField, gbc)

        // row 1: Create button — right-aligned, normal width
        gbc.gridx = 1; gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.EAST
        val createBtn = JButton("Create Repository")
        createBtn.addActionListener { run() }
        form.add(createBtn, gbc)

        val wrapper = JPanel()
        wrapper.isOpaque = false
        wrapper.layout = BoxLayout(wrapper, BoxLayout.X_AXIS)
        wrapper.add(Box.createHorizontalGlue())
        wrapper.add(form)
        wrapper.add(Box.createHorizontalGlue())

        val outer = JPanel(BorderLayout())
        outer.isOpaque = false
        outer.border = BorderFactory.createEmptyBorder(24, 24, 24, 24)
        outer.add(wrapper, BorderLayout.NORTH)

        add(outer, BorderLayout.CENTER)
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
            error("Destination already exists and is non-empty: ${repoDir.absolutePath}"); return
        }
        if (!repoDir.exists() && !repoDir.mkdirs()) {
            error("Could not create directory: ${repoDir.absolutePath}"); return
        }

        val cmd = Git.Static.init(repoDir.absolutePath)
        if (!cmd.success) { error("git init failed:\n\n${cmd.output}"); return }

        with(addCloneTab.mainView) {
            val repo = RepoTab(repoDir.absolutePath, name)
            addTab(repo)
            selectTab(repo)
            closeTab(addCloneTab)
        }
    }

    private fun error(message: String) =
        JOptionPane.showMessageDialog(this, message, "Create", JOptionPane.ERROR_MESSAGE)
}

package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import org.json.JSONObject
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Files
import javax.swing.*

class AddTab(private val addCloneTab: AddCloneTab) : JPanel(GridBagLayout()) {

    /** Path comes from the shared field in AddCloneTab. */
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
        val runBtn = JButton("Open")
        runBtn.addActionListener { run() }
        add(runBtn, gbc)

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        add(JPanel().apply { isOpaque = false }, gbc)
    }

    fun run() {
        val pathText = addCloneTab.sharedPathField.text.trim()
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
            val repo = RepoTab(pathText, nameField.text.ifBlank { pathText.toPath().fileName?.toString() ?: pathText })
            addTab(repo)
            selectTab(repo)
            closeTab(addCloneTab)
        }
    }
}

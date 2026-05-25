package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Files
import javax.swing.*

class AddTab(private val addCloneTab: AddCloneTab) : JPanel(BorderLayout()) {

    val nameField = JTextField(28)

    init {
        isOpaque = false

        // centred form card — fixed width, vertically near-top
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

        // row 1: Open button — right-aligned, normal width
        gbc.gridx = 1; gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.EAST
        val openBtn = JButton("Open Repository")
        openBtn.addActionListener { run() }
        form.add(openBtn, gbc)

        // centre form horizontally with glue
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
            val name = nameField.text.ifBlank { pathText.toPath().fileName?.toString() ?: pathText }
            val repo = RepoTab(pathText, name)
            addTab(repo)
            selectTab(repo)
            closeTab(addCloneTab)
        }
    }
}

package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.views.AddCloneTab
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class CreateTab(private val addCloneTab: AddCloneTab) : JPanel(GridBagLayout()) {

    val pathField = JTextField(30)
    val nameField = JTextField(20)

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill   = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0
        add(JLabel("Path:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        add(pathField, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        add(JLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        add(nameField, gbc)

        gbc.gridx = 1; gbc.gridy = 2
        val runBtn = JButton("Create")
        runBtn.addActionListener { run() }
        add(runBtn, gbc)
    }

    fun run() {
        //TODO: implement git init in selected directory
    }
}

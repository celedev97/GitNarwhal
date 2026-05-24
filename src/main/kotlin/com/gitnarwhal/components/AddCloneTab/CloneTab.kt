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

class CloneTab(private val addCloneTab: AddCloneTab) : JPanel(GridBagLayout()) {

    val urlField  = JTextField(30)
    val destField = JTextField(30)

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill   = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0
        add(JLabel("URL:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        add(urlField, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        add(JLabel("Destination:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        add(destField, gbc)

        gbc.gridx = 1; gbc.gridy = 2
        val runBtn = JButton("Clone")
        runBtn.addActionListener { run() }
        add(runBtn, gbc)
    }

    fun run() {
        //TODO: implement git clone with progress dialog
    }
}

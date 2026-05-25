package com.gitnarwhal.views

import com.gitnarwhal.backend.Git
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.*

class TagDialog(
    private val git: Git,
    private val commitHash: String,   // pre-selected "specified commit"
    owner: Window?
) : JDialog(owner, "Tag", ModalityType.APPLICATION_MODAL) {

    companion object {
        private const val TAB_ADD    = "add"
        private const val TAB_REMOVE = "remove"
    }

    private val cards   = CardLayout()
    private val content = JPanel(cards)

    // ── Add Tab fields ────────────────────────────────────────────────────────
    private val tagNameField      = JTextField(28)
    private val radioHead         = JRadioButton("Working copy parent")
    private val radioCommit       = JRadioButton("Specified commit:")
    private val commitField       = JTextField(commitHash, 20).apply { isEditable = false }
    private val pushCheckBox      = JCheckBox("Push tag:")
    private val remoteCombo       = JComboBox<String>()
    private val forceCheckBox     = JCheckBox("Move existing tag")
    private val lightweightCheck  = JCheckBox("Lightweight tag (not recommended)")
    private val messageField      = JTextField(28)
    private val addBtn            = JButton("Add Tag")

    // ── Remove Tab fields ─────────────────────────────────────────────────────
    private val removeTagCombo    = JComboBox<String>()
    private val removeFromRemotes = JCheckBox("Remove tag from all remotes")
    private val removeBtn         = JButton("Remove Tag")

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        layout = BorderLayout()

        // ── Toggle bar (SourceTree style) ─────────────────────────────────────
        val addToggle    = JToggleButton("  Add Tag  ")
        val removeToggle = JToggleButton("  Remove Tag  ")
        val group        = ButtonGroup().also { it.add(addToggle); it.add(removeToggle) }
        addToggle.isSelected = true
        addToggle.addActionListener    { cards.show(content, TAB_ADD) }
        removeToggle.addActionListener { cards.show(content, TAB_REMOVE) }

        val toggleBar = JPanel(FlowLayout(FlowLayout.CENTER, 0, 6))
        toggleBar.add(addToggle); toggleBar.add(removeToggle)
        add(toggleBar, BorderLayout.NORTH)

        // ── Cards ─────────────────────────────────────────────────────────────
        content.add(buildAddPanel(),    TAB_ADD)
        content.add(buildRemovePanel(), TAB_REMOVE)
        add(content, BorderLayout.CENTER)

        loadData()
        updateAddControls()

        pack()
        minimumSize = Dimension(480, 340)
        setLocationRelativeTo(owner)
    }

    // ── Add Tag panel ─────────────────────────────────────────────────────────
    private fun buildAddPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        val gbc = GridBagConstraints().apply {
            insets  = Insets(3, 4, 3, 4)
            fill    = GridBagConstraints.HORIZONTAL
            anchor  = GridBagConstraints.WEST
        }

        // Tag Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.gridwidth = 1
        panel.add(JLabel("Tag Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 3
        panel.add(tagNameField, gbc)

        // Commit: radios
        val radioGroup = ButtonGroup().also { it.add(radioHead); it.add(radioCommit) }
        radioCommit.isSelected = true
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1
        panel.add(JLabel("Commit:"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 3
        panel.add(radioHead, gbc)

        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0
        panel.add(radioCommit, gbc)
        gbc.gridx = 2; gbc.weightx = 1.0
        panel.add(commitField, gbc)
        gbc.gridx = 3; gbc.weightx = 0.0
        val browseBtn = JButton("…").apply { preferredSize = Dimension(28, commitField.preferredSize.height) }
        browseBtn.addActionListener {
            val hash = JOptionPane.showInputDialog(this@TagDialog, "Commit hash:", commitHash)
                ?.trim()?.ifBlank { null } ?: return@addActionListener
            commitField.text = hash
            radioCommit.isSelected = true
        }
        panel.add(browseBtn, gbc)

        // Push tag
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0
        panel.add(JLabel(""), gbc)
        gbc.gridx = 1; gbc.gridwidth = 1
        panel.add(pushCheckBox, gbc)
        gbc.gridx = 2; gbc.weightx = 1.0; gbc.gridwidth = 2
        panel.add(remoteCombo, gbc)
        remoteCombo.isEnabled = false
        pushCheckBox.addActionListener { remoteCombo.isEnabled = pushCheckBox.isSelected }

        // Advanced Options border
        val advPanel = JPanel(GridBagLayout())
        advPanel.border = BorderFactory.createTitledBorder("Advanced Options")
        val agbc = GridBagConstraints().apply {
            insets = Insets(2, 6, 2, 6); fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST; gridx = 0; weightx = 1.0
        }
        agbc.gridy = 0; advPanel.add(forceCheckBox, agbc)
        agbc.gridy = 1; advPanel.add(lightweightCheck, agbc)

        val msgRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        val msgLabel = JLabel("Message:")
        msgRow.add(msgLabel); msgRow.add(messageField)
        agbc.gridy = 2; advPanel.add(msgRow, agbc)

        lightweightCheck.addActionListener { updateAddControls() }

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4; gbc.weightx = 1.0
        panel.add(advPanel, gbc)

        // Buttons
        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4))
        addBtn.addActionListener { doAddTag() }
        val cancelAdd = JButton("Cancel").apply { addActionListener { dispose() } }
        btnRow.add(addBtn); btnRow.add(cancelAdd)

        gbc.gridy = 5
        panel.add(btnRow, gbc)

        return panel
    }

    // ── Remove Tag panel ──────────────────────────────────────────────────────
    private fun buildRemovePanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill   = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.gridwidth = 1
        panel.add(JLabel("Tag to remove:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(removeTagCombo, gbc)

        gbc.gridx = 1; gbc.gridy = 1
        panel.add(removeFromRemotes, gbc)

        // Filler
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weighty = 1.0
        panel.add(Box.createVerticalGlue(), gbc)

        // Buttons
        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4))
        removeBtn.addActionListener { doRemoveTag() }
        val cancelRem = JButton("Cancel").apply { addActionListener { dispose() } }
        btnRow.add(removeBtn); btnRow.add(cancelRem)

        gbc.gridy = 3; gbc.weighty = 0.0
        panel.add(btnRow, gbc)

        return panel
    }

    // ── Load data ─────────────────────────────────────────────────────────────
    private fun loadData() {
        // Remotes
        val remotes = git.remoteList().output.lines().filter { it.isNotBlank() }
        remotes.forEach { remoteCombo.addItem(it) }
        if (remotes.isEmpty()) { pushCheckBox.isEnabled = false; remoteCombo.isEnabled = false }

        // Tags for Remove tab
        val tags = git.tags().output.lines().filter { it.isNotBlank() }.sorted()
        tags.forEach { removeTagCombo.addItem(it) }
        removeBtn.isEnabled = tags.isNotEmpty()
    }

    private fun updateAddControls() {
        val lightweight = lightweightCheck.isSelected
        messageField.isEnabled = !lightweight
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private fun doAddTag() {
        val name = tagNameField.text.trim()
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Tag name is required.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        val hash       = if (radioHead.isSelected) "HEAD" else commitField.text.trim()
        val message    = messageField.text.trim().ifBlank { null }
        val force      = forceCheckBox.isSelected
        val lightweight = lightweightCheck.isSelected

        val r = git.tagCreateAt(name, hash, message, force, lightweight)
        if (!r.success) {
            JOptionPane.showMessageDialog(this, r.output, "Tag failed", JOptionPane.ERROR_MESSAGE)
            return
        }

        if (pushCheckBox.isSelected) {
            val remote = remoteCombo.selectedItem as? String ?: "origin"
            val rp = git.pushTag(remote, name)
            if (!rp.success) {
                JOptionPane.showMessageDialog(this,
                    "Tag created locally but push failed:\n${rp.output}",
                    "Push failed", JOptionPane.WARNING_MESSAGE)
            }
        }
        dispose()
    }

    private fun doRemoveTag() {
        val name = removeTagCombo.selectedItem as? String ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this, "Delete tag '$name'?", "Remove Tag", JOptionPane.YES_NO_OPTION)
        if (confirm != JOptionPane.YES_OPTION) return

        val r = git.tagDelete(name)
        if (!r.success) {
            JOptionPane.showMessageDialog(this, r.output, "Delete failed", JOptionPane.ERROR_MESSAGE)
            return
        }

        if (removeFromRemotes.isSelected) {
            val remotes = git.remoteList().output.lines().filter { it.isNotBlank() }
            val errors  = remotes.mapNotNull { remote ->
                val rp = git.pushDeleteTag(remote, name)
                if (!rp.success) "$remote: ${rp.output}" else null
            }
            if (errors.isNotEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Tag removed locally. Remote errors:\n${errors.joinToString("\n")}",
                    "Partial success", JOptionPane.WARNING_MESSAGE)
                dispose(); return
            }
        }
        dispose()
    }
}

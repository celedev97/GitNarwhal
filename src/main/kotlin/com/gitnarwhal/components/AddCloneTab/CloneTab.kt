package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*

class CloneTab(private val addCloneTab: AddCloneTab) : JPanel(GridBagLayout()) {

    val urlField  = JTextField(30)
    val destField = JTextField(30)
    val nameField = JTextField(20)
    private val runBtn = JButton("Clone")

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill   = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0
        add(JLabel("URL:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        add(urlField, gbc)

        gbc.gridwidth = 1
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        add(JLabel("Destination:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        add(destField, gbc)
        gbc.gridx = 2; gbc.weightx = 0.0
        val browseBtn = JButton("Browse…")
        browseBtn.addActionListener { browseDest() }
        add(browseBtn, gbc)

        gbc.gridx = 0; gbc.gridy = 2
        add(JLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        add(nameField, gbc)
        gbc.gridwidth = 1

        gbc.gridx = 1; gbc.gridy = 3
        runBtn.addActionListener { run() }
        add(runBtn, gbc)

        // auto-fill name + dest folder from URL on edit
        urlField.document.addDocumentListener(SimpleDocListener {
            val derived = deriveNameFromUrl(urlField.text)
            if (derived.isNotEmpty() && nameField.text.isBlank()) nameField.text = derived
        })
    }

    private fun browseDest() {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            destField.text = chooser.selectedFile.absolutePath
        }
    }

    private fun deriveNameFromUrl(url: String): String =
        url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git")

    fun run() {
        val url  = urlField.text.trim()
        val dest = destField.text.trim()
        val name = nameField.text.trim().ifBlank { deriveNameFromUrl(url) }

        if (url.isBlank()) { error("URL is required"); return }
        if (dest.isBlank()) { error("Destination is required"); return }
        if (name.isBlank()) { error("Could not derive a repo name from URL — fill it in manually"); return }

        val destPath = dest.toPath().resolve(name).toAbsolutePath().toString()
        if (File(destPath).exists()) { error("Destination already exists: $destPath"); return }

        runBtn.isEnabled = false
        val dialog = ProgressDialog(SwingUtilities.getWindowAncestor(this), "Cloning $url")
        val worker = object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val cmd = Git.Static.clone(url, destPath)
                return cmd.success to cmd.output
            }
            override fun done() {
                dialog.dispose()
                runBtn.isEnabled = true
                val (success, output) = try { get() } catch (e: Exception) { false to (e.message ?: "unknown error") }
                if (success) {
                    with(addCloneTab.mainView) {
                        val repo = RepoTab(destPath, name)
                        addTab(repo)
                        selectTab(repo)
                        closeTab(addCloneTab)
                    }
                } else {
                    error("Clone failed:\n\n$output")
                }
            }
        }
        worker.execute()
        dialog.isVisible = true   // modal — blocks until dispose()
    }

    private fun error(message: String) {
        JOptionPane.showMessageDialog(this, message, "Clone", JOptionPane.ERROR_MESSAGE)
    }
}

/** Small indeterminate-progress modal used while a long-running git op runs. */
internal class ProgressDialog(parent: java.awt.Window?, title: String) :
    JDialog(parent, title, ModalityType.APPLICATION_MODAL) {
    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        val bar = JProgressBar().apply { isIndeterminate = true }
        val label = JLabel(title).apply { border = BorderFactory.createEmptyBorder(12, 16, 8, 16) }
        contentPane = JPanel(java.awt.BorderLayout()).also {
            it.add(label, java.awt.BorderLayout.NORTH)
            it.add(bar,   java.awt.BorderLayout.CENTER)
            it.border = BorderFactory.createEmptyBorder(8, 8, 16, 8)
        }
        pack()
        size = java.awt.Dimension(420, size.height)
        setLocationRelativeTo(parent)
    }
}

internal class SimpleDocListener(private val onChange: () -> Unit) : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?)  = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?)  = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
}

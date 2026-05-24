package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.ProgressDialog
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

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

        // filler row — keeps the form top-anchored
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        add(JPanel().apply { isOpaque = false }, gbc)
        gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.gridwidth = 1

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

        if (url.isBlank())  { error("URL is required"); return }
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
                runBtn.isEnabled = true
                val (success, output) = try { get() }
                    catch (e: Exception) { false to (e.message ?: "unknown error") }
                dialog.finish(output, success)
                if (success) {
                    with(addCloneTab.mainView) {
                        val repo = RepoTab(destPath, name)
                        addTab(repo)
                        selectTab(repo)
                        closeTab(addCloneTab)
                    }
                }
            }
        }
        worker.execute()
        dialog.isVisible = true   // modal
    }

    private fun error(message: String) {
        JOptionPane.showMessageDialog(this, message, "Clone", JOptionPane.ERROR_MESSAGE)
    }
}

internal class SimpleDocListener(private val onChange: () -> Unit) : DocumentListener {
    override fun insertUpdate(e: DocumentEvent?)  = onChange()
    override fun removeUpdate(e: DocumentEvent?)  = onChange()
    override fun changedUpdate(e: DocumentEvent?) = onChange()
}

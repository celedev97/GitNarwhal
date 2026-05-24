package com.gitnarwhal.views

import com.gitnarwhal.backend.Git
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.Window
import javax.swing.*

/**
 * Modal commit dialog: shows staged + unstaged files, lets the user stage/unstage,
 * captures a commit message, and invokes `git commit -m`. On success, calls
 * [onSuccess] (the host repo tab refreshes itself).
 */
class CommitDialog(
    owner: Window?,
    private val git: Git,
    private val onSuccess: () -> Unit
) : JDialog(owner, "Commit", ModalityType.APPLICATION_MODAL) {

    private data class FileEntry(val path: String, val stagedCode: Char, val unstagedCode: Char) {
        val staged: Boolean   get() = stagedCode != ' ' && stagedCode != '?'
        val unstaged: Boolean get() = unstagedCode != ' '
        override fun toString() = "[$stagedCode$unstagedCode] $path"
    }

    private val stagedModel   = DefaultListModel<FileEntry>()
    private val unstagedModel = DefaultListModel<FileEntry>()
    private val stagedList   = JList(stagedModel)
    private val unstagedList = JList(unstagedModel)
    private val messageArea = JTextArea(6, 60).apply { lineWrap = true; wrapStyleWord = true }
    private val commitBtn = JButton("Commit")
    private val amendCheck = JCheckBox("Amend previous commit")

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        layout = BorderLayout(8, 8)

        add(buildFilesPanel(), BorderLayout.CENTER)
        add(buildSouthPanel(),  BorderLayout.SOUTH)

        refresh()

        pack()
        minimumSize = Dimension(720, 540)
        setLocationRelativeTo(owner)
    }

    private fun buildFilesPanel(): JPanel {
        val panel = JPanel(GridLayout(1, 2, 8, 0))
        panel.border = BorderFactory.createEmptyBorder(8, 8, 0, 8)

        panel.add(filePane("Staged", stagedList, "Unstage selected") {
            stagedList.selectedValuesList.forEach { git.unstage(it.path) }
            refresh()
        })
        panel.add(filePane("Unstaged / Untracked", unstagedList, "Stage selected") {
            unstagedList.selectedValuesList.forEach { git.add(it.path) }
            refresh()
        })
        return panel
    }

    private fun filePane(title: String, list: JList<FileEntry>, btnText: String, action: () -> Unit): JPanel {
        val p = JPanel(BorderLayout(4, 4))
        p.border = BorderFactory.createTitledBorder(title)
        list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        p.add(JScrollPane(list), BorderLayout.CENTER)
        val btn = JButton(btnText).apply { addActionListener { action() } }
        val stageAllBtn = JButton(if (title.startsWith("Staged")) "Unstage all" else "Stage all").apply {
            addActionListener {
                val model = list.model as DefaultListModel<FileEntry>
                (0 until model.size).forEach { i ->
                    val e = model.get(i)
                    if (title.startsWith("Staged")) git.unstage(e.path) else git.add(e.path)
                }
                refresh()
            }
        }
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        bar.add(btn); bar.add(stageAllBtn)
        p.add(bar, BorderLayout.SOUTH)
        return p
    }

    private fun buildSouthPanel(): JPanel {
        val south = JPanel(BorderLayout(4, 4))
        south.border = BorderFactory.createEmptyBorder(0, 8, 8, 8)

        val msgPanel = JPanel(BorderLayout())
        msgPanel.border = BorderFactory.createTitledBorder("Commit message")
        msgPanel.add(JScrollPane(messageArea), BorderLayout.CENTER)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4))
        buttons.add(amendCheck)
        val cancel = JButton("Cancel").apply { addActionListener { dispose() } }
        commitBtn.addActionListener { doCommit() }
        buttons.add(cancel); buttons.add(commitBtn)

        south.add(msgPanel, BorderLayout.CENTER)
        south.add(buttons,  BorderLayout.SOUTH)
        return south
    }

    fun refresh() {
        val status = git.status()
        stagedModel.clear()
        unstagedModel.clear()
        if (!status.success) return

        for (raw in status.output.lines()) {
            if (raw.isBlank() || raw.startsWith("##")) continue
            if (raw.length < 3) continue
            val xy = raw.substring(0, 2)
            val path = raw.substring(3).substringBefore(" -> ").trim()
            val entry = FileEntry(path, xy[0], xy[1])
            if (entry.staged)   stagedModel.addElement(entry)
            if (entry.unstaged) unstagedModel.addElement(entry)
        }
        commitBtn.isEnabled = stagedModel.size() > 0 || amendCheck.isSelected
    }

    private fun doCommit() {
        val msg = messageArea.text.trim()
        if (msg.isBlank() && !amendCheck.isSelected) {
            JOptionPane.showMessageDialog(this, "Commit message is required.", "Commit", JOptionPane.WARNING_MESSAGE)
            return
        }
        val result = if (amendCheck.isSelected) {
            if (msg.isBlank()) git.commitAmend() else git.commitAmend(msg)
        } else {
            git.commit(msg)
        }
        if (result.success) {
            onSuccess()
            dispose()
        } else {
            JOptionPane.showMessageDialog(this, "Commit failed:\n\n${result.output}", "Commit", JOptionPane.ERROR_MESSAGE)
        }
    }
}

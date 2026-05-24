package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.BranchButton
import com.gitnarwhal.components.CommitDataPanel
import com.gitnarwhal.components.CommitGraphCell
import com.gitnarwhal.utils.OS
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class RepoTab(var path: String, val tabTitle: String) : JPanel(BorderLayout()) {

    val git: Git = Git(path)

    private val commits: LinkedHashMap<String, Commit> = LinkedHashMap()
    private val commitList: MutableList<Commit> = mutableListOf()

    private val commitTableModel = object : AbstractTableModel() {
        private val columnNames = arrayOf("Graph", "Description", "Date", "Committer", "Commit")
        override fun getRowCount(): Int = commitList.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(col: Int): String = columnNames[col]
        override fun getValueAt(row: Int, col: Int): Any {
            val c = commitList[row]
            return when (col) {
                0 -> c
                1 -> c.title
                2 -> c.committerDate
                3 -> c.committer
                4 -> c.hash
                else -> ""
            }
        }
        override fun getColumnClass(col: Int): Class<*> = if (col == 0) Commit::class.java else String::class.java
        override fun isCellEditable(row: Int, col: Int): Boolean = false
    }

    val commitTable: JTable = JTable(commitTableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        rowHeight = 22
        columnModel.getColumn(0).cellRenderer = CommitGraphCell()
        columnModel.getColumn(0).preferredWidth = 80
        columnModel.getColumn(1).preferredWidth = 400
        columnModel.getColumn(2).preferredWidth = 140
        columnModel.getColumn(3).preferredWidth = 160
        columnModel.getColumn(4).preferredWidth = 80
    }

    private val commitDataPanel = CommitDataPanel(this)

    private val localBranchesBox  = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val remoteBranchesBox = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    private val sideBar: JPanel
    private val sideBarSplit: JSplitPane
    private var sideBarOpen = true
    private var previousSideBarDivider = 240

    init {
        sideBar = buildSidebar()

        val rightSplit = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(commitTable),
            JScrollPane(commitDataPanel)
        ).apply {
            resizeWeight = 0.7
            dividerLocation = 400
        }

        sideBarSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sideBar, rightSplit).apply {
            dividerLocation = previousSideBarDivider
            resizeWeight = 0.0
        }

        add(buildToolbar(), BorderLayout.NORTH)
        add(sideBarSplit, BorderLayout.CENTER)

        commitTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = commitTable.selectedRow
                if (row in commitList.indices) commitDataPanel.showCommit(commitList[row])
            }
        }

        refresh()
    }

    //region Toolbar
    private fun buildToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))

        bar.add(toolBtn("Toggle Sidebar") { toggleSideBar() })
        bar.add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 24) })
        bar.add(toolBtn("Refresh") { refresh() })
        bar.add(toolBtn("Fetch")   { fetch() })
        bar.add(toolBtn("Pull")    { pull() })
        bar.add(toolBtn("Push")    { push() })
        bar.add(toolBtn("Commit")  { commit() })
        bar.add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 24) })
        bar.add(toolBtn("Terminal") { openTerminal() })
        bar.add(toolBtn("Explorer") { openExplorer() })
        bar.add(toolBtn("Remote")   { openRemote() })
        return bar
    }

    private fun toolBtn(text: String, action: () -> Unit) = JButton(text).apply {
        addActionListener { action() }
    }
    //endregion

    //region Sidebar
    private fun buildSidebar(): JPanel {
        val panel = JPanel(BorderLayout())

        val sections = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        sections.add(sectionPanel("Local Branches",  localBranchesBox))
        sections.add(sectionPanel("Remote Branches", remoteBranchesBox))

        panel.add(JScrollPane(sections), BorderLayout.CENTER)
        return panel
    }

    private fun sectionPanel(title: String, body: JComponent): JComponent {
        val wrapper = JPanel(BorderLayout())
        wrapper.border = BorderFactory.createTitledBorder(title)
        wrapper.add(body, BorderLayout.CENTER)
        wrapper.alignmentX = Component.LEFT_ALIGNMENT
        return wrapper
    }

    private fun toggleSideBar() {
        if (sideBarOpen) {
            previousSideBarDivider = sideBarSplit.dividerLocation
            sideBarSplit.dividerLocation = 0
        } else {
            sideBarSplit.dividerLocation = previousSideBarDivider
        }
        sideBarOpen = !sideBarOpen
    }
    //endregion

    fun commit() {
        val owner = SwingUtilities.getWindowAncestor(this)
        CommitDialog(owner, git, onSuccess = { refresh() }).isVisible = true
    }

    fun refresh() {
        refreshBranches()
        refreshCommits()
    }

    fun fetch() {
        runWithProgress("Fetching…") { git.fetch() }
    }

    fun pull() {
        runWithProgress("Pulling…") { git.pull() }
    }

    fun push() {
        runWithProgress("Pushing…") { git.push() }
    }

    /** Runs [op] off-EDT under an indeterminate progress dialog, refreshing on completion. */
    private fun runWithProgress(title: String, op: () -> com.gitnarwhal.utils.Command) {
        val owner = SwingUtilities.getWindowAncestor(this)
        val dialog = com.gitnarwhal.components.AddCloneTab.ProgressDialog(owner, title)
        val worker = object : SwingWorker<com.gitnarwhal.utils.Command, Void>() {
            override fun doInBackground() = op()
            override fun done() {
                dialog.dispose()
                val cmd = try { get() } catch (e: Exception) { null }
                if (cmd != null && !cmd.success) {
                    JOptionPane.showMessageDialog(this@RepoTab,
                        "$title failed:\n\n${cmd.output}", title, JOptionPane.ERROR_MESSAGE)
                }
                refresh()
            }
        }
        worker.execute()
        dialog.isVisible = true
    }

    fun refreshCommits() {
        val log = git.log()
        if (!log.success) return

        commits.clear()
        commitList.clear()

        for (hashes in log.output.lines().map { it.trim().split(" ").filter { s -> s.isNotEmpty() } }) {
            if (hashes.isEmpty()) continue
            hashes.forEach { h ->
                if (!commits.contains(h)) commits[h] = Commit(h, this)
            }
            val commit = commits[hashes[0]]!!
            val parents = if (hashes.size > 1) hashes.subList(1, hashes.size).map { commits[it]!! } else listOf()
            parents.forEach { parent ->
                parent.childs.add(commit)
                commit.parents.add(parent)
            }
        }

        var y = 0
        fun dfs(c: Commit) {
            if (!c.explored) {
                c.explored = true
                c.childs.forEach { dfs(it) }
                c.y = y
                y++
            }
        }
        commits.values.sortedBy { -it.committerTimeStamp.toLong() }.forEach { dfs(it) }

        commits.values.sortedBy { it.y }.forEach { commitList.add(it) }
        commitTableModel.fireTableDataChanged()
    }

    fun refreshBranches() {
        val gitBranches = git.branches()
        if (!gitBranches.success) return

        localBranchesBox.removeAll()
        remoteBranchesBox.removeAll()

        for (line in gitBranches.output.lines()) {
            if (line.isBlank()) continue
            val isActive = line.trim().startsWith("*")
            val branchParts = line.removePrefix("*").trim().replace("\\s+".toRegex(), " ").split(" ")
            val branchFullName  = branchParts[0]
            val branchShortName = branchFullName.substringAfter('/')

            val branchButton = BranchButton(branchShortName, this, isActive)

            if (branchShortName == branchFullName) {
                localBranchesBox.add(branchButton)
                if (branchParts.size > 2) {
                    branchButton.tracking = "^\\[(\\w\\w*\\/*\\w\\w*)\\]".toRegex().find(branchParts[2])?.groups?.get(1)?.value
                }
            } else {
                remoteBranchesBox.add(branchButton)
            }
        }
        localBranchesBox.revalidate();  localBranchesBox.repaint()
        remoteBranchesBox.revalidate(); remoteBranchesBox.repaint()
    }

    fun openTerminal() = Thread { OS.TERMINAL.execute(path) }.start()
    fun openExplorer() = Thread { (OS.EXPLORER + path).execute() }.start()
    fun openRemote()   = Thread { (OS.BROWSER  + git.remoteUrl().output).execute() }.start()

    fun selectCommit(hash: String) {
        val idx = commitList.indexOfFirst { it.hash == hash }
        if (idx >= 0) {
            commitTable.selectionModel.setSelectionInterval(idx, idx)
            commitTable.scrollRectToVisible(commitTable.getCellRect(idx, 0, true))
        }
    }
}

private class TextCellRenderer : DefaultTableCellRenderer()

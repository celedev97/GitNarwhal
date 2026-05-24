package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.CommitDataPanel
import com.gitnarwhal.components.CommitGraphCell
import com.gitnarwhal.components.ProgressDialog
import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.OS
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class RepoTab(var path: String, val tabTitle: String) : JPanel(BorderLayout()) {

    val git: Git = Git(path)

    // ── Commit table ──────────────────────────────────────────────────────────
    private val commitList: MutableList<Commit> = mutableListOf()

    private val commitTableModel = object : AbstractTableModel() {
        private val cols = arrayOf("Graph", "Description", "Date", "Committer", "Commit")
        override fun getRowCount()                   = commitList.size
        override fun getColumnCount()                = cols.size
        override fun getColumnName(col: Int)         = cols[col]
        override fun getValueAt(row: Int, col: Int): Any {
            val c = commitList[row]
            return when (col) {
                0 -> c; 1 -> c.title; 2 -> c.committerDate; 3 -> c.committer; 4 -> c.hash
                else -> ""
            }
        }
        override fun getColumnClass(col: Int) = if (col == 0) Commit::class.java else String::class.java
        override fun isCellEditable(row: Int, col: Int) = false
    }

    val commitTable: JTable = JTable(commitTableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        rowHeight = 22
        columnModel.getColumn(0).apply { cellRenderer = CommitGraphCell(); preferredWidth = 80 }
        columnModel.getColumn(1).preferredWidth = 400
        columnModel.getColumn(2).preferredWidth = 140
        columnModel.getColumn(3).preferredWidth = 160
        columnModel.getColumn(4).preferredWidth = 80
    }

    private val commitDataPanel = CommitDataPanel(this)

    // ── Branch tree ───────────────────────────────────────────────────────────

    /** Leaf data stored in branch tree nodes. */
    private data class BranchInfo(
        val fullName: String,   // name passed to git commands
        val leafName: String,   // last path segment — shown in tree
        val isActive: Boolean,
        val tracking: String? = null
    )

    private val branchRoot         = DefaultMutableTreeNode("root")
    private val localBranchesNode  = DefaultMutableTreeNode("LOCAL BRANCHES")
    private val remoteBranchesNode = DefaultMutableTreeNode("REMOTE BRANCHES")
    private val branchTreeModel    = DefaultTreeModel(branchRoot)
    private val branchTree: JTree

    // ── Stash list ────────────────────────────────────────────────────────────
    private val stashListModel = DefaultListModel<String>()
    private val stashList      = JList(stashListModel).apply {
        selectionMode      = ListSelectionModel.SINGLE_SELECTION
        componentPopupMenu = buildStashPopup()
    }

    private val sideBarSplit: JSplitPane
    private var sideBarOpen            = true
    private var previousSideBarDivider = 240

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        branchRoot.add(localBranchesNode)
        branchRoot.add(remoteBranchesNode)

        branchTree = JTree(branchTreeModel).apply {
            isRootVisible  = false
            showsRootHandles = true
            cellRenderer   = BranchCellRenderer()
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addMouseListener(buildBranchMouseAdapter())
        }

        val rightSplit = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(commitTable),
            JScrollPane(commitDataPanel)
        ).apply { resizeWeight = 0.7; dividerLocation = 400 }

        sideBarSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildSidebar(),
            rightSplit
        ).apply { dividerLocation = previousSideBarDivider; resizeWeight = 0.0 }

        add(buildToolbar(), BorderLayout.NORTH)
        add(sideBarSplit,  BorderLayout.CENTER)

        commitTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = commitTable.selectedRow
                if (row in commitList.indices) commitDataPanel.showCommit(commitList[row])
            }
        }

        installCommitContextMenu()
        refresh()   // async — returns immediately
    }

    // ── Branch tree renderer ──────────────────────────────────────────────────

    private inner class BranchCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            icon = null   // no icons for now
            val node = value as? DefaultMutableTreeNode ?: return this
            when (val obj = node.userObject) {
                is BranchInfo -> {
                    text = obj.leafName
                    font = font.deriveFont(if (obj.isActive) Font.BOLD else Font.PLAIN)
                }
                is String -> {
                    text = obj
                    // Category headers (direct children of hidden root) are bold + muted
                    if (node.parent == branchRoot) {
                        font       = font.deriveFont(Font.BOLD, (font.size - 1).toFloat())
                        foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
                    } else {
                        font = font.deriveFont(Font.PLAIN)
                    }
                }
            }
            return this
        }
    }

    // ── Branch tree mouse listener ────────────────────────────────────────────

    private fun buildBranchMouseAdapter() = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent)  = checkPopup(e)
        override fun mouseReleased(e: MouseEvent) = checkPopup(e)

        private fun checkPopup(e: MouseEvent) {
            if (!e.isPopupTrigger) return
            val tp = branchTree.getPathForLocation(e.x, e.y) ?: return
            branchTree.selectionPath = tp
            val bi = (tp.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? BranchInfo ?: return
            buildBranchPopup(bi.fullName).show(branchTree, e.x, e.y)
        }

        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                val tp = branchTree.getPathForLocation(e.x, e.y) ?: return
                val bi = (tp.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? BranchInfo ?: return
                checkoutBranch(bi.fullName)
            }
        }
    }

    private fun buildBranchPopup(branchFullName: String): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Checkout")  { checkoutBranch(branchFullName) })
        menu.add(menuItem("Merge into current") {
            if (confirm("Merge '$branchFullName' into current branch?")) {
                val r = git.merge(branchFullName)
                if (!r.success) showError("Merge failed", r.output)
                refresh()
            }
        })
        menu.add(menuItem("Rebase current onto '$branchFullName'") {
            if (confirm("Rebase current branch onto '$branchFullName'?")) {
                val r = git.rebase(branchFullName)
                if (!r.success) showError("Rebase failed", r.output)
                refresh()
            }
        })
        menu.addSeparator()
        menu.add(menuItem("Rename…") {
            val newName = JOptionPane.showInputDialog(this, "New name:", branchFullName)
                ?.takeIf { it.isNotBlank() } ?: return@menuItem
            val r = git.renameBranch(branchFullName, newName)
            if (!r.success) showError("Rename failed", r.output)
            refreshBranches()
        })
        menu.add(menuItem("Delete") {
            if (confirm("Delete branch '$branchFullName'? (refuses if not merged)")) {
                val r = git.deleteBranch(branchFullName, force = false)
                if (!r.success) {
                    if (confirm("Branch not merged. Force delete '$branchFullName'? (irreversible!)")) {
                        val rf = git.deleteBranch(branchFullName, force = true)
                        if (!rf.success) showError("Force delete failed", rf.output)
                    }
                }
                refreshBranches()
            }
        })
        return menu
    }

    private fun checkoutBranch(branchName: String) {
        val r = git.selectBranch(branchName)
        if (r.success) refreshBranches()
        else showError("Checkout failed", r.output)
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private fun buildSidebar(): JPanel {
        val sections = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        sections.add(sectionPanel("Branches", JScrollPane(branchTree).apply {
            preferredSize = Dimension(240, 200)
        }))
        sections.add(sectionPanel("Stashes", stashList))

        return JPanel(BorderLayout()).also {
            it.add(JScrollPane(sections), BorderLayout.CENTER)
        }
    }

    private fun sectionPanel(title: String, body: JComponent): JComponent {
        val wrapper = JPanel(BorderLayout())
        wrapper.border    = BorderFactory.createTitledBorder(title)
        wrapper.alignmentX = Component.LEFT_ALIGNMENT
        wrapper.add(body, BorderLayout.CENTER)
        return wrapper
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun buildToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        bar.add(toolBtn("Toggle Sidebar") { toggleSideBar() })
        bar.add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 24) })
        bar.add(toolBtn("Refresh") { refresh() })
        bar.add(toolBtn("Fetch")   { fetch() })
        bar.add(toolBtn("Pull")    { pull() })
        bar.add(toolBtn("Push")    { push() })
        bar.add(toolBtn("Commit")  { commit() })
        bar.add(toolBtn("Stash")   { stashCurrentChanges() })
        bar.add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 24) })
        bar.add(toolBtn("Terminal") { openTerminal() })
        bar.add(toolBtn("Explorer") { openExplorer() })
        bar.add(toolBtn("Remote")   { openRemote() })
        return bar
    }

    private fun toolBtn(text: String, action: () -> Unit) =
        JButton(text).apply { addActionListener { action() } }

    private fun toggleSideBar() {
        if (sideBarOpen) {
            previousSideBarDivider = sideBarSplit.dividerLocation
            sideBarSplit.dividerLocation = 0
        } else {
            sideBarSplit.dividerLocation = previousSideBarDivider
        }
        sideBarOpen = !sideBarOpen
    }

    // ── Commit context menu ───────────────────────────────────────────────────

    private fun installCommitContextMenu() {
        commitTable.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent)  = maybeShow(e)
            override fun mouseReleased(e: MouseEvent) = maybeShow(e)
            private fun maybeShow(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = commitTable.rowAtPoint(e.point)
                if (row < 0) return
                commitTable.selectionModel.setSelectionInterval(row, row)
                buildCommitPopup(commitList[row]).show(e.component, e.x, e.y)
            }
        })
    }

    private fun buildCommitPopup(commit: Commit): JPopupMenu {
        val menu = JPopupMenu(commit.shortHash)
        menu.add(menuItem("Copy hash")       { copyToClipboard(commit.hash) })
        menu.add(menuItem("Copy short hash") { copyToClipboard(commit.shortHash) })
        menu.addSeparator()
        menu.add(menuItem("Create branch from this…") {
            val name = JOptionPane.showInputDialog(
                this, "New branch name:", "Create branch", JOptionPane.QUESTION_MESSAGE
            )?.takeIf { it.isNotBlank() } ?: return@menuItem
            val r = git.createBranchFrom(name, commit.hash)
            if (!r.success) showError("Create branch failed", r.output)
            refresh()
        })
        menu.addSeparator()
        menu.add(menuItem("Cherry-pick") {
            if (confirm("Cherry-pick ${commit.shortHash}?")) {
                val r = git.cherryPick(commit.hash)
                if (!r.success) showError("Cherry-pick failed", r.output)
                refresh()
            }
        })
        menu.add(menuItem("Revert") {
            if (confirm("Revert ${commit.shortHash}? (creates a new commit)")) {
                val r = git.revert(commit.hash)
                if (!r.success) showError("Revert failed", r.output)
                refresh()
            }
        })
        menu.addSeparator()
        val resetMenu = JMenu("Reset current branch to here")
        resetMenu.add(menuItem("Soft (keep index + working tree)") {
            if (confirm("git reset --soft ${commit.shortHash}?")) {
                val r = git.reset(commit.hash, "soft")
                if (!r.success) showError("Reset failed", r.output); refresh()
            }
        })
        resetMenu.add(menuItem("Mixed (default; unstage changes)") {
            if (confirm("git reset --mixed ${commit.shortHash}?")) {
                val r = git.reset(commit.hash, "mixed")
                if (!r.success) showError("Reset failed", r.output); refresh()
            }
        })
        resetMenu.add(menuItem("Hard (DISCARD changes — destructive!)") {
            if (confirm("DESTRUCTIVE: git reset --hard ${commit.shortHash}\n\nAll uncommitted changes will be lost. Continue?")) {
                val r = git.reset(commit.hash, "hard")
                if (!r.success) showError("Reset failed", r.output); refresh()
            }
        })
        menu.add(resetMenu)
        return menu
    }

    // ── Stash ─────────────────────────────────────────────────────────────────

    private fun buildStashPopup(): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Apply") { stashAction { i -> git.stashApply(i) } })
        menu.add(menuItem("Pop")   { stashAction { i -> git.stashPop(i) } })
        menu.add(menuItem("Drop")  {
            val idx = stashList.selectedIndex.takeIf { it >= 0 } ?: return@menuItem
            if (confirm("Drop stash@{$idx}? (irreversible)")) {
                val r = git.stashDrop(idx)
                if (!r.success) showError("Drop failed", r.output)
                refreshStashes()
            }
        })
        return menu
    }

    private fun stashAction(op: (Int) -> Command) {
        val idx = stashList.selectedIndex.takeIf { it >= 0 } ?: return
        val r = op(idx)
        if (!r.success) showError("Stash op failed", r.output)
        refresh()
    }

    private fun stashCurrentChanges() {
        val msg = JOptionPane.showInputDialog(
            this, "Stash message (optional):", "Stash", JOptionPane.QUESTION_MESSAGE
        ) ?: return
        val r = if (msg.isBlank()) git.stashPush() else git.stashPush(msg)
        if (!r.success) showError("Stash failed", r.output)
        refresh()
    }

    // ── Async refresh helpers ─────────────────────────────────────────────────

    private data class RefreshSnapshot(
        val branchesOut: String, val branchOk: Boolean,
        val logOut: String,      val logOk: Boolean,
        val stashOut: String
    )

    /** Runs all git reads in one background thread, then applies updates on EDT. */
    fun refresh() {
        object : SwingWorker<RefreshSnapshot, Void>() {
            override fun doInBackground(): RefreshSnapshot {
                val b = git.branches()
                val l = git.log()
                val s = git.stashList()
                return RefreshSnapshot(b.output, b.success, l.output, l.success,
                    if (s.success) s.output else "")
            }
            override fun done() {
                val snap = try { get() } catch (e: Exception) { return }
                if (snap.branchOk) applyBranches(snap.branchesOut)
                if (snap.logOk)    applyCommits(snap.logOut)
                applyStashes(snap.stashOut)
            }
        }.execute()
    }

    /** Re-reads only branches (used after branch operations). */
    fun refreshBranches() {
        object : SwingWorker<String?, Void>() {
            override fun doInBackground(): String? {
                val r = git.branches()
                return if (r.success) r.output else null
            }
            override fun done() {
                val out = try { get() } catch (e: Exception) { null } ?: return
                applyBranches(out)
            }
        }.execute()
    }

    /** Re-reads only stashes (used after stash operations). */
    fun refreshStashes() {
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.stashList().output
            override fun done() {
                val out = try { get() } catch (e: Exception) { "" }
                applyStashes(out)
            }
        }.execute()
    }

    // ── Data application (EDT-only) ───────────────────────────────────────────

    /** Rebuilds the branch tree from raw `git branch --all -vv` output. */
    private fun applyBranches(output: String) {
        localBranchesNode.removeAllChildren()
        remoteBranchesNode.removeAllChildren()

        for (line in output.lines()) {
            if (line.isBlank()) continue
            val isActive = line.startsWith("*")
            val cleaned  = line.removePrefix("*").trim()
            val parts    = cleaned.replace("\\s+".toRegex(), " ").split(" ")
            val fullName = parts[0]

            if (cleaned.contains(" -> ")) continue    // skip HEAD pointer lines

            if (fullName.startsWith("remotes/")) {
                val withoutPrefix = fullName.removePrefix("remotes/")
                insertBranch(remoteBranchesNode, withoutPrefix, active = false, tracking = null)
            } else {
                val tracking = if (parts.size > 2)
                    "^\\[([^\\]:]+)".toRegex().find(parts.drop(2).joinToString(" "))
                        ?.groups?.get(1)?.value
                else null
                insertBranch(localBranchesNode, fullName, isActive, tracking)
            }
        }

        branchTreeModel.reload()
        SwingUtilities.invokeLater {
            for (i in 0 until branchTree.rowCount) branchTree.expandRow(i)
        }
    }

    /**
     * Inserts a branch path into [parent], creating intermediate String folder
     * nodes for each "/" segment, e.g. "feature/my-branch" →
     *   parent → "feature" → BranchInfo("feature/my-branch", "my-branch", …)
     */
    private fun insertBranch(
        parent: DefaultMutableTreeNode,
        path: String,
        active: Boolean,
        tracking: String?
    ) {
        val segments = path.split("/")
        var current  = parent
        for (i in 0 until segments.size - 1) {
            val seg = segments[i]
            current = current.children().asSequence()
                .filterIsInstance<DefaultMutableTreeNode>()
                .firstOrNull { it.userObject == seg }
                ?: DefaultMutableTreeNode(seg).also { current.add(it) }
        }
        current.add(DefaultMutableTreeNode(BranchInfo(path, segments.last(), active, tracking)))
    }

    /** Rebuilds the commit table from raw `git log --pretty=format:%H %P` output. */
    private fun applyCommits(logOutput: String) {
        val localMap  = LinkedHashMap<String, Commit>()
        val localList = mutableListOf<Commit>()

        for (line in logOutput.lines()) {
            val hashes = line.trim().split(" ").filter { it.isNotEmpty() }
            if (hashes.isEmpty()) continue
            hashes.forEach { h -> if (!localMap.containsKey(h)) localMap[h] = Commit(h, this) }
            val commit  = localMap[hashes[0]]!!
            val parents = if (hashes.size > 1) hashes.drop(1).mapNotNull { localMap[it] } else emptyList()
            parents.forEach { p -> p.childs.add(commit); commit.parents.add(p) }
        }

        var y = 0
        fun dfs(c: Commit) {
            if (!c.explored) { c.explored = true; c.childs.forEach { dfs(it) }; c.y = y++ }
        }
        localMap.values.sortedBy { -it.committerTimeStamp.toLong() }.forEach { dfs(it) }
        localMap.values.sortedBy { it.y }.forEach { localList.add(it) }

        commitList.clear()
        commitList.addAll(localList)
        commitTableModel.fireTableDataChanged()
    }

    private fun applyStashes(output: String) {
        stashListModel.clear()
        for (line in output.lines()) if (line.isNotBlank()) stashListModel.addElement(line)
    }

    // ── Toolbar actions (fetch / pull / push run off-EDT with progress dialog) ─

    fun commit() {
        val owner = SwingUtilities.getWindowAncestor(this)
        CommitDialog(owner, git, onSuccess = { refresh() }).isVisible = true
    }

    fun fetch() = runWithProgress("Fetching…")  { git.fetch() }
    fun pull()  = runWithProgress("Pulling…")   { git.pull() }
    fun push()  = runWithProgress("Pushing…")   { git.push() }

    /**
     * Runs [op] on a background thread under a modal [ProgressDialog].
     * On failure the dialog stays open showing git output.
     * On success the dialog auto-closes (unless user checked "Show output"),
     * then [refresh] is called.
     */
    private fun runWithProgress(title: String, op: () -> Command) {
        val owner  = SwingUtilities.getWindowAncestor(this)
        val dialog = ProgressDialog(owner, title)
        val worker = object : SwingWorker<Command, Void>() {
            override fun doInBackground() = op()
            override fun done() {
                val cmd = try { get() } catch (e: Exception) {
                    dialog.finish("Error: ${e.message}", false)
                    return
                }
                dialog.finish(cmd.output, cmd.success)
                refresh()   // async — safe to call even while dialog may still be visible
            }
        }
        worker.execute()
        dialog.isVisible = true   // modal — pumps its own event loop until disposed
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

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

    private fun menuItem(text: String, action: () -> Unit) =
        JMenuItem(text).apply { addActionListener { action() } }

    private fun confirm(message: String) =
        JOptionPane.showConfirmDialog(this, message, "Confirm",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION

    private fun showError(title: String, body: String) =
        JOptionPane.showMessageDialog(this, body.ifBlank { "(no output)" }, title, JOptionPane.ERROR_MESSAGE)

    private fun copyToClipboard(text: String) =
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(text), null)
}

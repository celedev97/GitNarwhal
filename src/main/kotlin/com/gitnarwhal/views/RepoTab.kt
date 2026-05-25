package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.CommitDataPanel
import com.gitnarwhal.components.CommitGraphCell
import com.gitnarwhal.components.ProgressDialog
import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.OS
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.awt.Color
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
        override fun getRowCount()           = commitList.size
        override fun getColumnCount()        = cols.size
        override fun getColumnName(col: Int) = cols[col]
        override fun getValueAt(row: Int, col: Int): Any {
            val c = commitList[row]
            // All fields are pre-populated from git log — no git.show() call on EDT.
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

    private data class BranchInfo(
        val fullName: String,
        val leafName: String,
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
            isRootVisible    = false
            showsRootHandles = true
            cellRenderer     = BranchCellRenderer()
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
        init {
            // Null out all default tree icons — branch tree shows text only.
            setLeafIcon(null)
            setOpenIcon(null)
            setClosedIcon(null)
        }

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            // Let super handle selection colours / background correctly.
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            // super uses the null icons set in init → JLabel icon stays null.
            val node = value as? DefaultMutableTreeNode ?: return this
            when (val obj = node.userObject) {
                is BranchInfo -> {
                    text = obj.leafName
                    font = font.deriveFont(if (obj.isActive) Font.BOLD else Font.PLAIN)
                    // Don't touch foreground — super already set correct selection/normal colour.
                }
                is String -> {
                    text = obj
                    // Category headers (direct children of hidden root) → bold only.
                    font = if (node.parent == branchRoot)
                        font.deriveFont(Font.BOLD)
                    else
                        font.deriveFont(Font.PLAIN)
                    // Never override foreground — on dark themes the disabled colour is invisible.
                }
            }
            return this
        }
    }

    // ── Branch tree mouse handling ────────────────────────────────────────────

    private fun buildBranchMouseAdapter() = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent)  = checkPopup(e)
        override fun mouseReleased(e: MouseEvent) = checkPopup(e)

        private fun checkPopup(e: MouseEvent) {
            if (!e.isPopupTrigger) return
            val tp = branchTree.getPathForLocation(e.x, e.y) ?: return
            branchTree.selectionPath = tp
            val bi = (tp.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? BranchInfo
                ?: return
            buildBranchPopup(bi.fullName).show(branchTree, e.x, e.y)
        }

        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                val tp = branchTree.getPathForLocation(e.x, e.y) ?: return
                val bi = (tp.lastPathComponent as? DefaultMutableTreeNode)?.userObject
                    as? BranchInfo ?: return
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
        if (r.success) refreshBranches() else showError("Checkout failed", r.output)
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private fun buildSidebar(): JPanel {
        val sections = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        sections.add(sectionPanel("Branches", JScrollPane(branchTree)))
        sections.add(sectionPanel("Stashes",  stashList))
        return JPanel(BorderLayout()).also { it.add(JScrollPane(sections), BorderLayout.CENTER) }
    }

    private fun sectionPanel(title: String, body: JComponent): JComponent {
        val w = JPanel(BorderLayout())
        w.border    = BorderFactory.createTitledBorder(title)
        w.alignmentX = Component.LEFT_ALIGNMENT
        w.add(body, BorderLayout.CENTER)
        return w
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun buildToolbar(): JToolBar {
        val bar = JToolBar().apply { isFloatable = false }

        bar.add(iconBtn(MaterialDesign.MDI_MENU,           "Toggle Sidebar") { toggleSideBar() })
        bar.addSeparator()
        bar.add(iconBtn(MaterialDesign.MDI_REFRESH,        "Refresh")  { refresh() })
        bar.add(iconBtn(MaterialDesign.MDI_CLOUD_DOWNLOAD, "Fetch")    { fetch() })
        bar.add(iconBtn(MaterialDesign.MDI_ARROW_DOWN_BOLD,"Pull")     { pull() })
        bar.add(iconBtn(MaterialDesign.MDI_ARROW_UP_BOLD,  "Push")     { push() })
        bar.add(iconBtn(MaterialDesign.MDI_GIT,            "Commit")   { commit() })
        bar.add(iconBtn(MaterialDesign.MDI_ARCHIVE,        "Stash")    { stashCurrentChanges() })
        bar.addSeparator()
        bar.add(iconBtn(MaterialDesign.MDI_CONSOLE,        "Terminal") { openTerminal() })
        bar.add(iconBtn(MaterialDesign.MDI_FOLDER,         "Explorer") { openExplorer() })
        bar.add(iconBtn(MaterialDesign.MDI_EARTH,          "Remote")   { openRemote() })
        return bar
    }

    private fun iconBtn(icon: org.kordamp.ikonli.Ikon, label: String, action: () -> Unit): JButton =
        JButton(label, FontIcon.of(icon, 18, UIManager.getColor("Label.foreground") ?: Color.DARK_GRAY)).apply {
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition   = SwingConstants.BOTTOM
            toolTipText            = label
            isFocusable            = false
            addActionListener { action() }
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

    // ── Async refresh ─────────────────────────────────────────────────────────

    private data class RefreshSnapshot(
        val branchesOut: String, val branchOk: Boolean,
        val logOut: String,      val logOk: Boolean,
        val stashOut: String
    )

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

    fun refreshStashes() {
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.stashList().output
            override fun done() {
                val out = try { get() } catch (e: Exception) { "" }
                applyStashes(out)
            }
        }.execute()
    }

    // ── Data application (EDT) ────────────────────────────────────────────────

    private fun applyBranches(output: String) {
        localBranchesNode.removeAllChildren()
        remoteBranchesNode.removeAllChildren()

        for (line in output.lines()) {
            if (line.isBlank()) continue
            val isActive = line.startsWith("*")
            val cleaned  = line.removePrefix("*").trim()
            val parts    = cleaned.replace("\\s+".toRegex(), " ").split(" ")
            val fullName = parts[0]

            if (cleaned.contains(" -> ")) continue   // skip HEAD pointer

            if (fullName.startsWith("remotes/")) {
                insertBranch(remoteBranchesNode, fullName.removePrefix("remotes/"), false, null)
            } else {
                val tracking = if (parts.size > 2)
                    "^\\[([^\\]:]+)".toRegex().find(parts.drop(2).joinToString(" "))
                        ?.groups?.get(1)?.value
                else null
                insertBranch(localBranchesNode, fullName, isActive, tracking)
            }
        }

        branchTreeModel.reload()
        for (i in 0 until branchTree.rowCount) branchTree.expandRow(i)
    }

    private fun insertBranch(
        parent: DefaultMutableTreeNode, path: String, active: Boolean, tracking: String?
    ) {
        val segs    = path.split("/")
        var current = parent
        for (i in 0 until segs.size - 1) {
            val seg = segs[i]
            current = current.children().asSequence()
                .filterIsInstance<DefaultMutableTreeNode>()
                .firstOrNull { it.userObject == seg }
                ?: DefaultMutableTreeNode(seg).also { current.add(it) }
        }
        current.add(DefaultMutableTreeNode(BranchInfo(path, segs.last(), active, tracking)))
    }

    /**
     * Parses the richer git log format (RS/US delimited) and pre-populates
     * every Commit with its metadata so the table can render without
     * triggering git-show on the EDT.
     *
     * Record separator :  (ASCII RS)
     * Field  separator :  (ASCII US)
     * Fields: hash | parentHashes | shortHash | author | authorDateUnix
     *             | committer | committerDateUnix | subject
     */
    private fun applyCommits(logOutput: String) {
        val localMap  = LinkedHashMap<String, Commit>()
        val localList = mutableListOf<Commit>()

        for (record in logOutput.split('')) {
            val f = record.split('')
            if (f.size < 8) continue
            val hash         = f[0].trim()
            if (hash.isBlank()) continue
            val parentHashes = f[1].split(" ").filter { it.isNotBlank() }
            val shortHash    = f[2]
            val author       = f[3]
            val authorDate   = f[4]
            val committer    = f[5]
            val committerDate = f[6]
            val title        = f[7]

            val commit = localMap.getOrPut(hash) { Commit(hash, this) }
            // Pre-populate: shortHash(0), author(1), authorDate(2),
            //               committer(3), committerDate(4), title(5)
            commit.prePopulate(listOf(shortHash, author, authorDate, committer, committerDate, title))

            val parents = parentHashes.mapNotNull { localMap[it] }
            parents.forEach { p -> p.childs.add(commit); commit.parents.add(p) }
        }

        // DFS topological sort for graph layout
        var y = 0
        fun dfs(c: Commit) {
            if (!c.explored) { c.explored = true; c.childs.forEach { dfs(it) }; c.y = y++ }
        }
        localMap.values.sortedBy { runCatching { -it.committerTimeStamp.toLong() }.getOrDefault(0L) }
            .forEach { dfs(it) }
        localMap.values.sortedBy { it.y }.forEach { localList.add(it) }

        commitList.clear()
        commitList.addAll(localList)
        commitTableModel.fireTableDataChanged()
    }

    private fun applyStashes(output: String) {
        stashListModel.clear()
        for (line in output.lines()) if (line.isNotBlank()) stashListModel.addElement(line)
    }

    // ── Toolbar git actions ───────────────────────────────────────────────────

    fun commit() {
        val owner = SwingUtilities.getWindowAncestor(this)
        CommitDialog(owner, git, onSuccess = { refresh() }).isVisible = true
    }

    fun fetch() = runWithProgress("Fetching…")  { git.fetch() }
    fun pull()  = runWithProgress("Pulling…")   { git.pull() }
    fun push()  = runWithProgress("Pushing…")   { git.push() }

    private fun runWithProgress(title: String, op: () -> Command) {
        val owner  = SwingUtilities.getWindowAncestor(this)
        val dialog = ProgressDialog(owner, title)
        val worker = object : SwingWorker<Command, Void>() {
            override fun doInBackground() = op()
            override fun done() {
                val cmd = try { get() } catch (e: Exception) {
                    dialog.finish("Error: ${e.message}", false); return
                }
                dialog.finish(cmd.output, cmd.success)
                refresh()
            }
        }
        worker.execute()
        dialog.isVisible = true
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
        JOptionPane.showMessageDialog(this, body.ifBlank { "(no output)" }, title,
            JOptionPane.ERROR_MESSAGE)

    private fun copyToClipboard(text: String) =
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(text), null)
}

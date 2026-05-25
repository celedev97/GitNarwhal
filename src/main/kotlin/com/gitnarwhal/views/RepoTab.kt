package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.CommitDataPanel
import com.gitnarwhal.components.CommitGraphCell
import com.gitnarwhal.components.ProgressDialog
import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.OS
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
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
        override fun getRowCount()                    = commitList.size
        override fun getColumnCount()                 = cols.size
        override fun getColumnName(col: Int)          = cols[col]
        override fun getColumnClass(col: Int)         = if (col == 0) Commit::class.java else String::class.java
        override fun isCellEditable(row: Int, col: Int) = false
        override fun getValueAt(row: Int, col: Int): Any {
            val c = commitList[row]
            return when (col) {
                0 -> c; 1 -> c.title; 2 -> c.committerDate; 3 -> c.committer; 4 -> c.hash
                else -> ""
            }
        }
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
        val fullName: String, val leafName: String,
        val isActive: Boolean, val tracking: String? = null
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

    // ── File Status models + widgets — MUST be declared before init ──────────
    private val stagedModel   = DefaultListModel<String>()
    private val unstagedModel = DefaultListModel<String>()

    private val stagedList   = JList(stagedModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }
    private val unstagedList = JList(unstagedModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }
    private val stagedHeaderLabel   = JLabel("Staged files (0 files)")
    private val unstagedHeaderLabel = JLabel("Unstaged files (0 files)")

    private val diffScrollPane   = JScrollPane()
    private val diffFileNameLabel = JLabel(" ", SwingConstants.LEFT)

    private val commitMsgField          = JTextArea(4, 40).apply { lineWrap = true; wrapStyleWord = true }
    private val amendCheckBox           = JCheckBox("Amend latest commit")
    private val pushImmediatelyCheckBox = JCheckBox("Push changes immediately to origin/main")

    // ── Badge toolbar buttons ─────────────────────────────────────────────────
    private val commitBtn = BadgeButton(MaterialDesign.MDI_CHECK_CIRCLE, "Commit") { showFileStatus() }
    private val pullBtn   = BadgeButton(MaterialDesign.MDI_ARROW_DOWN_BOLD, "Pull") { pull() }
    private val pushBtn   = BadgeButton(MaterialDesign.MDI_ARROW_UP_BOLD, "Push")  { push() }

    // ── Main content card layout (History / File Status) ──────────────────────
    private val mainCards     = CardLayout()
    private val mainContainer = JPanel(mainCards)

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

        // History view: commit table above, detail panel below
        val historyView = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(commitTable),
            JScrollPane(commitDataPanel)
        ).apply { resizeWeight = 0.7; dividerLocation = 400 }

        mainContainer.add(historyView,         CARD_HISTORY)
        mainContainer.add(buildFileStatusPanel(), CARD_FILE_STATUS)
        mainCards.show(mainContainer, CARD_HISTORY)

        sideBarSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildSidebar(),
            mainContainer
        ).apply { dividerLocation = previousSideBarDivider; resizeWeight = 0.0 }

        add(buildToolbar(), BorderLayout.NORTH)
        add(sideBarSplit,   BorderLayout.CENTER)

        commitTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = commitTable.selectedRow
                if (row in commitList.indices) commitDataPanel.showCommit(commitList[row])
            }
        }

        installCommitContextMenu()
        refresh()
    }

    // ── Card constants ────────────────────────────────────────────────────────
    companion object {
        private const val CARD_HISTORY     = "history"
        private const val CARD_FILE_STATUS = "fileStatus"
    }

    // ── File Status panel ─────────────────────────────────────────────────────

    private fun buildFileStatusPanel(): JComponent {
        // ── File selection → show diff ────────────────────────────────────────
        stagedList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && stagedList.selectedValue != null) {
                unstagedList.clearSelection()
                showFileDiff(stagedList.selectedValue.substring(2), staged = true)
            }
        }
        unstagedList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && unstagedList.selectedValue != null) {
                stagedList.clearSelection()
                showFileDiff(unstagedList.selectedValue.substring(2), staged = false)
            }
        }

        // ── Staged panel ──────────────────────────────────────────────────────
        val unstageAllBtn      = JButton("Unstage All")
        val unstageSelectedBtn = JButton("Unstage Selected")
        unstageAllBtn.addActionListener {
            val r = git.unstageAll()
            if (!r.success) showError("Unstage failed", r.output)
            refreshFileStatus()
        }
        unstageSelectedBtn.addActionListener {
            stagedList.selectedValuesList.forEach { git.unstage(it.substring(2)) }
            refreshFileStatus()
        }
        val stagedBtnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            isOpaque = false; add(unstageAllBtn); add(unstageSelectedBtn)
        }
        val stagedHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(4, 6, 2, 4)
            add(stagedHeaderLabel, BorderLayout.WEST)
            add(stagedBtnRow,      BorderLayout.EAST)
        }
        val stagedPanel = JPanel(BorderLayout()).apply {
            add(stagedHeader,            BorderLayout.NORTH)
            add(JScrollPane(stagedList), BorderLayout.CENTER)
        }

        // ── Unstaged panel ────────────────────────────────────────────────────
        val stageAllBtn      = JButton("Stage All")
        val stageSelectedBtn = JButton("Stage Selected")
        stageAllBtn.addActionListener {
            val r = git.addAll()
            if (!r.success) showError("Stage failed", r.output)
            refreshFileStatus()
        }
        stageSelectedBtn.addActionListener {
            unstagedList.selectedValuesList.forEach { git.add(it.substring(2)) }
            refreshFileStatus()
        }
        val unstagedBtnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            isOpaque = false; add(stageAllBtn); add(stageSelectedBtn)
        }
        val unstagedHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(4, 6, 2, 4)
            add(unstagedHeaderLabel, BorderLayout.WEST)
            add(unstagedBtnRow,      BorderLayout.EAST)
        }
        val unstagedPanel = JPanel(BorderLayout()).apply {
            add(unstagedHeader,            BorderLayout.NORTH)
            add(JScrollPane(unstagedList), BorderLayout.CENTER)
        }

        // Right-click context menus
        unstagedList.componentPopupMenu = JPopupMenu().apply {
            add(JMenuItem("Stage selected").apply {
                addActionListener {
                    unstagedList.selectedValuesList.forEach { git.add(it.substring(2)) }
                    refreshFileStatus()
                }
            })
        }
        stagedList.componentPopupMenu = JPopupMenu().apply {
            add(JMenuItem("Unstage selected").apply {
                addActionListener {
                    stagedList.selectedValuesList.forEach { git.unstage(it.substring(2)) }
                    refreshFileStatus()
                }
            })
        }

        // ── Diff panel (right) ────────────────────────────────────────────────
        diffFileNameLabel.apply {
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            font   = font.deriveFont(Font.BOLD)
        }
        val diffTopBar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground") ?: Color(0x44_44_44))
            add(diffFileNameLabel, BorderLayout.WEST)
        }
        val diffOuter = JPanel(BorderLayout()).apply {
            add(diffTopBar,    BorderLayout.NORTH)
            add(diffScrollPane, BorderLayout.CENTER)
        }

        // ── File list vertical split ──────────────────────────────────────────
        val fileListSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, stagedPanel, unstagedPanel).apply {
            resizeWeight     = 0.4
            dividerLocation  = 160
            minimumSize      = Dimension(200, 0)
        }

        // ── Top half: files | diff ────────────────────────────────────────────
        val topHalf = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileListSplit, diffOuter).apply {
            resizeWeight    = 0.0
            dividerLocation = 260
        }

        // ── Commit area (bottom) ──────────────────────────────────────────────
        val userName  = git.configGet("user.name").output.trim()
        val userEmail = git.configGet("user.email").output.trim()
        val authorStr = when {
            userName.isNotBlank()  -> "$userName <$userEmail>"
            userEmail.isNotBlank() -> userEmail
            else                   -> "Unknown author"
        }
        val authorLabel = JLabel(authorStr).apply {
            border     = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            font       = font.deriveFont(Font.PLAIN, 11f)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }

        commitMsgField.putClientProperty("JTextField.placeholderText", "Commit message")
        val commitBtnBottom = JButton("Commit").apply {
            putClientProperty("FlatLaf.style",
                "background: #1A6FBF; foreground: #FFFFFF; hoverBackground: #2078CC; pressedBackground: #155DA0")
            addActionListener { doCommit() }
        }
        val checkboxPanel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(pushImmediatelyCheckBox)
            add(amendCheckBox)
        }
        val bottomBar = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(4, 0, 0, 0)
            add(checkboxPanel,  BorderLayout.CENTER)
            add(commitBtnBottom, BorderLayout.EAST)
        }
        val commitArea = JPanel(BorderLayout(0, 4)).apply {
            border        = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            preferredSize = Dimension(0, 160)
            minimumSize   = Dimension(0, 120)
            add(authorLabel,                 BorderLayout.NORTH)
            add(JScrollPane(commitMsgField), BorderLayout.CENTER)
            add(bottomBar,                   BorderLayout.SOUTH)
        }

        return JSplitPane(JSplitPane.VERTICAL_SPLIT, topHalf, commitArea).apply {
            resizeWeight = 0.75
        }
    }

    private fun refreshFileStatus() {
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.status().output
            override fun done() {
                val out = try { get() } catch (e: Exception) { return }
                stagedModel.clear(); unstagedModel.clear()
                for (line in out.lines()) {
                    if (line.length < 3 || line.startsWith("##")) continue
                    val x  = line[0]; val y = line[1]; val file = line.substring(3)
                    if (x != ' ' && x != '?') stagedModel.addElement("$x $file")
                    if (y == 'M' || y == 'D' || y == '?') unstagedModel.addElement("$y $file")
                }
                stagedHeaderLabel.text   = "Staged files (${stagedModel.size()} files)"
                unstagedHeaderLabel.text = "Unstaged files (${unstagedModel.size()} files)"
            }
        }.execute()
    }

    // ── Diff display ──────────────────────────────────────────────────────────

    private fun showFileStatus() {
        mainCards.show(mainContainer, CARD_FILE_STATUS)
        refreshFileStatus()
    }

    private fun showFileDiff(file: String, staged: Boolean) {
        diffFileNameLabel.text = file
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String =
                (if (staged) git.diffStaged(file) else git.diff(file)).output
            override fun done() {
                val diffText = try { get() } catch (e: Exception) { return }
                diffScrollPane.setViewportView(buildDiffView(diffText, staged, file))
                diffScrollPane.revalidate()
            }
        }.execute()
    }

    private data class DiffHunk(val header: String, val lines: List<String>)
    private data class ParsedDiff(val fileHeader: List<String>, val hunks: List<DiffHunk>)

    private fun parseDiff(diffText: String): ParsedDiff {
        val allLines   = diffText.lines()
        val fileHeader = allLines.takeWhile { !it.startsWith("@@") }
        val hunks      = mutableListOf<DiffHunk>()
        var hunkHeader = ""
        var hunkLines  = mutableListOf<String>()
        for (line in allLines.drop(fileHeader.size)) {
            if (line.startsWith("@@")) {
                if (hunkHeader.isNotEmpty()) hunks.add(DiffHunk(hunkHeader, hunkLines.toList()))
                hunkHeader = line
                hunkLines  = mutableListOf(line)
            } else if (hunkHeader.isNotEmpty()) {
                hunkLines.add(line)
            }
        }
        if (hunkHeader.isNotEmpty()) hunks.add(DiffHunk(hunkHeader, hunkLines.toList()))
        return ParsedDiff(fileHeader, hunks)
    }

    private fun buildPatch(fileHeader: List<String>, hunk: DiffHunk) = buildString {
        fileHeader.forEach { appendLine(it) }
        hunk.lines.forEach { appendLine(it) }
    }

    private fun buildDiffView(diffText: String, staged: Boolean, file: String): JPanel {
        val container = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIManager.getColor("EditorPane.background") ?: Color(0x2B, 0x2B, 0x2B)
        }
        if (diffText.isBlank()) {
            container.add(JLabel(if (staged) "No staged changes" else "No unstaged changes").apply {
                border     = BorderFactory.createEmptyBorder(12, 12, 12, 12)
                foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
            return container
        }
        val parsed = parseDiff(diffText)
        parsed.hunks.forEachIndexed { idx, hunk ->
            val headerInfo = hunk.header.substringAfter("@@").substringBefore("@@").trim()
            val hunkLabel  = JLabel("Hunk ${idx + 1}:  $headerInfo").apply {
                border     = BorderFactory.createEmptyBorder(6, 8, 4, 8)
                font       = font.deriveFont(Font.BOLD, 11f)
                foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            }
            val stageHunkBtn   = JButton(if (staged) "Unstage hunk" else "Stage hunk")
            val discardHunkBtn = JButton("Discard hunk").apply { isVisible = !staged }

            stageHunkBtn.addActionListener {
                val patch = buildPatch(parsed.fileHeader, hunk)
                val r = if (staged) git.applyPatch(patch, cached = true, reverse = true)
                        else        git.applyPatch(patch, cached = true, reverse = false)
                if (!r.success) showError("Stage hunk failed", r.output)
                else { showFileDiff(file, staged); refreshFileStatus() }
            }
            discardHunkBtn.addActionListener {
                val patch = buildPatch(parsed.fileHeader, hunk)
                val r = git.applyPatch(patch, cached = false, reverse = true)
                if (!r.success) showError("Discard hunk failed", r.output)
                else { showFileDiff(file, staged); refreshFileStatus() }
            }

            val hunkBtnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply { isOpaque = false }
            if (!staged) hunkBtnRow.add(discardHunkBtn)
            hunkBtnRow.add(stageHunkBtn)

            val hunkHeaderRow = JPanel(BorderLayout()).apply {
                isOpaque    = false
                alignmentX  = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, 36)
                add(hunkLabel, BorderLayout.WEST)
                add(hunkBtnRow, BorderLayout.EAST)
            }
            container.add(hunkHeaderRow)
            container.add(buildHunkTextPane(hunk.lines).apply { alignmentX = Component.LEFT_ALIGNMENT })
            container.add(Box.createVerticalStrut(4))
        }
        container.add(Box.createVerticalGlue())
        return container
    }

    private fun buildHunkTextPane(lines: List<String>): JTextPane {
        val addAttr = SimpleAttributeSet().also {
            StyleConstants.setBackground(it, Color(0x1B, 0x3A, 0x27))
            StyleConstants.setForeground(it, UIManager.getColor("Label.foreground") ?: Color.WHITE)
        }
        val removeAttr = SimpleAttributeSet().also {
            StyleConstants.setBackground(it, Color(0x3A, 0x1B, 0x1B))
            StyleConstants.setForeground(it, UIManager.getColor("Label.foreground") ?: Color.WHITE)
        }
        val hunkAttr = SimpleAttributeSet().also {
            StyleConstants.setForeground(it, Color(0x4F, 0xC3, 0xF7))
        }
        val baseAttr = SimpleAttributeSet().also {
            StyleConstants.setForeground(it, UIManager.getColor("Label.foreground") ?: Color.WHITE)
        }
        return JTextPane().apply {
            isEditable = false
            font       = Font(Font.MONOSPACED, Font.PLAIN, 12)
            for (line in lines) {
                val attr = when {
                    line.startsWith("+") && !line.startsWith("+++") -> addAttr
                    line.startsWith("-") && !line.startsWith("---") -> removeAttr
                    line.startsWith("@@")                           -> hunkAttr
                    else                                            -> baseAttr
                }
                try { styledDocument.insertString(styledDocument.length, line + "\n", attr) }
                catch (_: Exception) {}
            }
        }
    }

    // ── Inline commit ─────────────────────────────────────────────────────────

    private fun doCommit() {
        val msg = commitMsgField.text.trim()
        if (msg.isEmpty() && !amendCheckBox.isSelected) {
            showError("Commit", "Commit message is required"); return
        }
        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val result = when {
                    amendCheckBox.isSelected && msg.isEmpty() -> git.commitAmend()
                    amendCheckBox.isSelected                  -> git.commitAmend(msg)
                    else                                      -> git.commit(msg)
                }
                if (!result.success) return false to result.output
                if (pushImmediatelyCheckBox.isSelected) {
                    val push = git.push()
                    return push.success to push.output
                }
                return true to ""
            }
            override fun done() {
                val (ok, out) = try { get() } catch (e: Exception) { false to (e.message ?: "") }
                if (!ok) { showError("Commit failed", out); return }
                commitMsgField.text            = ""
                amendCheckBox.isSelected       = false
                pushImmediatelyCheckBox.isSelected = false
                refresh()
                refreshFileStatus()
            }
        }.execute()
    }

    // ── BadgeButton ───────────────────────────────────────────────────────────

    private inner class BadgeButton(ikon: Ikon, label: String, action: () -> Unit) : JButton() {
        var badge = 0
            set(v) { field = v; repaint() }

        init {
            icon                   = FontIcon.of(ikon, 18, UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY)
            text                   = label
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition   = SwingConstants.BOTTOM
            isBorderPainted        = false
            isContentAreaFilled    = false
            isFocusPainted         = false
            toolTipText            = label
            addActionListener { action() }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (badge <= 0) return
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val txt = if (badge > 99) "99+" else badge.toString()
            g2.font = g2.font.deriveFont(Font.BOLD, 9f)
            val fm  = g2.fontMetrics
            val tw  = fm.stringWidth(txt)
            val bw  = maxOf(tw + 6, 14)
            val bh  = 12
            val bx  = width - bw - 1
            val by  = 3
            g2.color = UIManager.getColor("Component.accentColor") ?: Color(0x1A_6F_BF)
            g2.fillRoundRect(bx, by, bw, bh, bh, bh)
            g2.color = Color.WHITE
            g2.drawString(txt, bx + (bw - tw) / 2, by + fm.ascent - 1)
            g2.dispose()
        }
    }

    // ── Branch tree renderer ──────────────────────────────────────────────────

    private inner class BranchCellRenderer : DefaultTreeCellRenderer() {
        init { setLeafIcon(null); setOpenIcon(null); setClosedIcon(null) }

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode ?: return this
            when (val obj = node.userObject) {
                is BranchInfo -> {
                    text = obj.leafName
                    font = font.deriveFont(if (obj.isActive) Font.BOLD else Font.PLAIN)
                }
                is String -> {
                    text = obj
                    font = if (node.parent == branchRoot) font.deriveFont(Font.BOLD)
                           else font.deriveFont(Font.PLAIN)
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
        menu.add(menuItem("Checkout") { checkoutBranch(branchFullName) })
        menu.add(menuItem("Merge into current") {
            if (confirm("Merge '$branchFullName' into current branch?")) {
                val r = git.merge(branchFullName)
                if (!r.success) showError("Merge failed", r.output); refresh()
            }
        })
        menu.add(menuItem("Rebase current onto '$branchFullName'") {
            if (confirm("Rebase current branch onto '$branchFullName'?")) {
                val r = git.rebase(branchFullName)
                if (!r.success) showError("Rebase failed", r.output); refresh()
            }
        })
        menu.addSeparator()
        menu.add(menuItem("Rename…") {
            val newName = JOptionPane.showInputDialog(this, "New name:", branchFullName)
                ?.takeIf { it.isNotBlank() } ?: return@menuItem
            val r = git.renameBranch(branchFullName, newName)
            if (!r.success) showError("Rename failed", r.output); refreshBranches()
        })
        menu.add(menuItem("Delete") {
            if (confirm("Delete branch '$branchFullName'? (refuses if not merged)")) {
                val r = git.deleteBranch(branchFullName, force = false)
                if (!r.success) {
                    if (confirm("Branch not merged. Force delete '$branchFullName'?")) {
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
        val panel = JPanel(BorderLayout())

        // WORKSPACE section
        val workspacePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
        }
        workspacePanel.add(sidebarSectionHeader("WORKSPACE"))
        workspacePanel.add(workspaceItem("File Status", MaterialDesign.MDI_FILE_DOCUMENT) {
            showFileStatus()
        })
        workspacePanel.add(workspaceItem("History", MaterialDesign.MDI_HISTORY) {
            mainCards.show(mainContainer, CARD_HISTORY)
        })

        // BRANCHES section
        val branchesPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Branches")
        }
        branchesPanel.add(JScrollPane(branchTree), BorderLayout.CENTER)

        // STASHES section
        val stashesPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Stashes")
            preferredSize = Dimension(0, 120)
        }
        stashesPanel.add(stashList, BorderLayout.CENTER)

        // Compose: workspace at top, then branches + stashes in a scroll
        val sections = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        sections.add(workspacePanel)
        sections.add(branchesPanel.apply { alignmentX = Component.LEFT_ALIGNMENT })
        sections.add(stashesPanel.apply { alignmentX = Component.LEFT_ALIGNMENT })

        panel.add(JScrollPane(sections), BorderLayout.CENTER)
        return panel
    }

    private fun sidebarSectionHeader(title: String): JLabel =
        JLabel(title).apply {
            font   = font.deriveFont(Font.BOLD, 10f)
            border = BorderFactory.createEmptyBorder(4, 8, 2, 4)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

    private fun workspaceItem(label: String, ikon: Ikon, onClick: () -> Unit): JButton =
        JButton(label, FontIcon.of(ikon, 14, UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY)).apply {
            horizontalAlignment = SwingConstants.LEFT
            isBorderPainted     = false
            isContentAreaFilled = false
            isFocusPainted      = false
            border              = BorderFactory.createEmptyBorder(3, 12, 3, 4)
            alignmentX          = Component.LEFT_ALIGNMENT
            maximumSize         = Dimension(Int.MAX_VALUE, preferredSize.height + 4)
            addActionListener { onClick() }
        }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun buildToolbar(): JToolBar {
        val bar = JToolBar().apply { isFloatable = false }

        // Toggle sidebar (far left, separate)
        bar.add(iconBtn(MaterialDesign.MDI_MENU, "Toggle Sidebar") { toggleSideBar() })
        bar.addSeparator()

        // Primary actions — match SourceTree order
        bar.add(commitBtn)
        bar.add(pullBtn)
        bar.add(pushBtn)
        bar.add(iconBtn(MaterialDesign.MDI_CLOUD_DOWNLOAD, "Fetch")   { fetch() })
        bar.addSeparator()
        bar.add(iconBtn(MaterialDesign.MDI_SOURCE_BRANCH,  "Branch")  { newBranch() })
        bar.add(iconBtn(MaterialDesign.MDI_SOURCE_MERGE,    "Merge")   { mergeBranch() })
        bar.add(iconBtn(MaterialDesign.MDI_ARCHIVE,        "Stash")   { stashCurrentChanges() })
        bar.add(iconBtn(MaterialDesign.MDI_UNDO,           "Discard") { discardAll() })
        bar.add(iconBtn(MaterialDesign.MDI_REFRESH,        "Refresh") { refresh() })

        // Push remaining to right
        bar.add(Box.createHorizontalGlue())

        bar.add(iconBtn(MaterialDesign.MDI_EARTH,    "Remote")   { openRemote() })
        bar.add(iconBtn(MaterialDesign.MDI_CONSOLE,  "Terminal") { openTerminal() })
        bar.add(iconBtn(MaterialDesign.MDI_FOLDER,   "Explorer") { openExplorer() })
        bar.addSeparator()
        bar.add(iconBtn(MaterialDesign.MDI_SETTINGS, "Settings") {
            val win = SwingUtilities.getWindowAncestor(this)
            SettingsDialog(win as? java.awt.Frame).isVisible = true
        })
        return bar
    }

    private fun iconBtn(icon: Ikon, label: String, action: () -> Unit): JButton =
        JButton(label, FontIcon.of(icon, 18, UIManager.getColor("Label.foreground") ?: Color.DARK_GRAY)).apply {
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition   = SwingConstants.BOTTOM
            toolTipText            = label
            isBorderPainted        = false
            isContentAreaFilled    = false
            isFocusPainted         = false
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
            val name = JOptionPane.showInputDialog(this, "New branch name:", "Create branch",
                JOptionPane.QUESTION_MESSAGE)?.takeIf { it.isNotBlank() } ?: return@menuItem
            val r = git.createBranchFrom(name, commit.hash)
            if (!r.success) showError("Create branch failed", r.output); refresh()
        })
        menu.addSeparator()
        menu.add(menuItem("Cherry-pick") {
            if (confirm("Cherry-pick ${commit.shortHash}?")) {
                val r = git.cherryPick(commit.hash)
                if (!r.success) showError("Cherry-pick failed", r.output); refresh()
            }
        })
        menu.add(menuItem("Revert") {
            if (confirm("Revert ${commit.shortHash}? (creates a new commit)")) {
                val r = git.revert(commit.hash)
                if (!r.success) showError("Revert failed", r.output); refresh()
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
        resetMenu.add(menuItem("Mixed (unstage changes)") {
            if (confirm("git reset --mixed ${commit.shortHash}?")) {
                val r = git.reset(commit.hash, "mixed")
                if (!r.success) showError("Reset failed", r.output); refresh()
            }
        })
        resetMenu.add(menuItem("Hard (DISCARD changes — destructive!)") {
            if (confirm("DESTRUCTIVE: git reset --hard ${commit.shortHash}\n\nAll uncommitted changes will be lost.")) {
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
                if (!r.success) showError("Drop failed", r.output); refreshStashes()
            }
        })
        return menu
    }

    private fun stashAction(op: (Int) -> Command) {
        val idx = stashList.selectedIndex.takeIf { it >= 0 } ?: return
        val r = op(idx)
        if (!r.success) showError("Stash op failed", r.output); refresh()
    }

    private fun stashCurrentChanges() {
        val msg = JOptionPane.showInputDialog(this, "Stash message (optional):", "Stash",
            JOptionPane.QUESTION_MESSAGE) ?: return
        val r = if (msg.isBlank()) git.stashPush() else git.stashPush(msg)
        if (!r.success) showError("Stash failed", r.output); refresh()
    }

    // ── Toolbar branch/merge/discard ──────────────────────────────────────────

    private fun newBranch() {
        val name = JOptionPane.showInputDialog(this, "New branch name:", "Create Branch",
            JOptionPane.QUESTION_MESSAGE)?.takeIf { it.isNotBlank() } ?: return
        val r = git.createBranch(name)
        if (!r.success) showError("Create branch failed", r.output); refreshBranches()
    }

    private fun mergeBranch() {
        val name = JOptionPane.showInputDialog(this, "Branch to merge into current branch:",
            "Merge", JOptionPane.QUESTION_MESSAGE)?.takeIf { it.isNotBlank() } ?: return
        if (confirm("Merge '$name' into current branch?")) {
            val r = git.merge(name)
            if (!r.success) showError("Merge failed", r.output); refresh()
        }
    }

    private fun discardAll() {
        if (!confirm("Discard ALL local changes?\n\nThis will restore every modified file to its last committed state. Untracked files are NOT deleted."))
            return
        val r = git.restore(".")
        if (!r.success) showError("Discard failed", r.output); refresh()
    }

    // ── Async refresh ─────────────────────────────────────────────────────────

    private data class RefreshSnapshot(
        val branchesOut: String, val branchOk: Boolean,
        val logOut: String,      val logOk: Boolean,
        val stashOut: String,
        val modifiedCount: Int,
        val unpulledCount: Int,
        val unpushedCount: Int
    )

    fun refresh() {
        object : SwingWorker<RefreshSnapshot, Void>() {
            override fun doInBackground(): RefreshSnapshot {
                val b  = git.branches()
                val l  = git.log()
                val s  = git.stashList()
                val st = git.status()
                val modified = st.output.lines().count { it.length > 2 && !it.startsWith("##") }
                val unpulled = git.unpulledCount().output.trim().toIntOrNull() ?: 0
                val unpushed = git.unpushedCount().output.trim().toIntOrNull() ?: 0
                return RefreshSnapshot(
                    b.output, b.success, l.output, l.success,
                    if (s.success) s.output else "",
                    modified, unpulled, unpushed
                )
            }
            override fun done() {
                val snap = try { get() } catch (e: Exception) { return }
                if (snap.branchOk) applyBranches(snap.branchesOut)
                if (snap.logOk)    applyCommits(snap.logOut)
                applyStashes(snap.stashOut)
                commitBtn.badge = snap.modifiedCount
                pullBtn.badge   = snap.unpulledCount
                pushBtn.badge   = snap.unpushedCount
            }
        }.execute()
    }

    fun refreshBranches() {
        object : SwingWorker<String?, Void>() {
            override fun doInBackground() = git.branches().output.takeIf { git.branches().success }
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
            if (cleaned.contains(" -> ")) continue
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

    private fun insertBranch(parent: DefaultMutableTreeNode, path: String, active: Boolean, tracking: String?) {
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

    private fun applyCommits(logOutput: String) {
        val localMap  = LinkedHashMap<String, Commit>()
        val localList = mutableListOf<Commit>()
        for (record in logOutput.split('')) {
            val f = record.split('')
            if (f.size < 8) continue
            val hash = f[0].trim(); if (hash.isBlank()) continue
            val parentHashes  = f[1].split(" ").filter { it.isNotBlank() }
            val commit = localMap.getOrPut(hash) { Commit(hash, this) }
            commit.prePopulate(listOf(f[2], f[3], f[4], f[5], f[6], f[7]))
            parentHashes.mapNotNull { localMap[it] }
                .forEach { p -> p.childs.add(commit); commit.parents.add(p) }
        }
        var y = 0
        fun dfs(c: Commit) {
            if (!c.explored) { c.explored = true; c.childs.forEach { dfs(it) }; c.y = y++ }
        }
        localMap.values.sortedBy { runCatching { -it.committerTimeStamp.toLong() }.getOrDefault(0L) }
            .forEach { dfs(it) }
        localMap.values.sortedBy { it.y }.forEach { localList.add(it) }
        commitList.clear(); commitList.addAll(localList)
        commitTableModel.fireTableDataChanged()
    }

    private fun applyStashes(output: String) {
        stashListModel.clear()
        for (line in output.lines()) if (line.isNotBlank()) stashListModel.addElement(line)
    }

    // ── Toolbar git actions ───────────────────────────────────────────────────

    fun commit() {
        CommitDialog(SwingUtilities.getWindowAncestor(this), git, onSuccess = { refresh() }).isVisible = true
    }

    fun fetch() = runWithProgress("Fetching…")  { git.fetch() }
    fun pull()  = runWithProgress("Pulling…")   { git.pull() }
    fun push()  = runWithProgress("Pushing…")   { git.push() }

    private fun runWithProgress(title: String, op: () -> Command) {
        val owner  = SwingUtilities.getWindowAncestor(this)
        val dialog = ProgressDialog(owner, title)
        object : SwingWorker<Command, Void>() {
            override fun doInBackground() = op()
            override fun done() {
                val cmd = try { get() } catch (e: Exception) {
                    dialog.finish("Error: ${e.message}", false); return
                }
                dialog.finish(cmd.output, cmd.success)
                refresh()
            }
        }.execute()
        dialog.isVisible = true
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    fun openTerminal() = Thread { OS.TERMINAL.execute(path) }.start()
    fun openExplorer() = Thread { (OS.EXPLORER + path).execute() }.start()
    fun openRemote()   = Thread { (OS.BROWSER  + git.remoteUrl().output).execute() }.start()

    fun selectCommit(hash: String) {
        val idx = commitList.indexOfFirst { it.hash == hash }
        if (idx >= 0) {
            mainCards.show(mainContainer, CARD_HISTORY)
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

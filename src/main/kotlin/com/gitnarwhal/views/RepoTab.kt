package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.backend.RefInfo
import com.gitnarwhal.backend.RefType
import com.gitnarwhal.components.CommitDataPanel
import com.gitnarwhal.components.CommitDescriptionCell
import com.gitnarwhal.components.CommitGraphCell
import com.gitnarwhal.components.ProgressOverlay
import com.gitnarwhal.components.PullOverlay
import com.gitnarwhal.components.PushOverlay
import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.NativeFileChooser
import com.gitnarwhal.utils.OS
import com.gitnarwhal.utils.Settings
import org.json.JSONObject
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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
    private val commitList:     MutableList<Commit> = mutableListOf()
    private val filteredCommits: MutableList<Commit> = mutableListOf()

    private val commitTableModel = object : AbstractTableModel() {
        private val cols = arrayOf("Graph", "Description", "Date", "Committer", "Commit")
        override fun getRowCount()                    = filteredCommits.size
        override fun getColumnCount()                 = cols.size
        override fun getColumnName(col: Int)          = cols[col]
        override fun getColumnClass(col: Int)         = if (col <= 1) Commit::class.java else String::class.java
        override fun isCellEditable(row: Int, col: Int) = false
        override fun getValueAt(row: Int, col: Int): Any {
            val c = filteredCommits[row]
            return when (col) {
                0 -> c; 1 -> c; 2 -> c.committerDate; 3 -> c.committer; 4 -> c.hash
                else -> ""
            }
        }
    }

    val commitTable: JTable = JTable(commitTableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_OFF
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        rowHeight = 22
        columnModel.getColumn(0).cellRenderer = CommitGraphCell()
        columnModel.getColumn(1).cellRenderer = CommitDescriptionCell()
    }
    private val commitScrollPane = JScrollPane(commitTable)

    private val commitDataPanel = CommitDataPanel(this)

    // ── Commit file list + diff ───────────────────────────────────────────────
    private val commitFileModel  = DefaultListModel<String>()
    private val commitFileList   = JList(commitFileModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val commitDiffScroll = JScrollPane()
    private val commitFileLabel  = JLabel("0 files")
    private var currentCommit: Commit? = null

    // ── Branch tree ───────────────────────────────────────────────────────────
    private data class BranchInfo(
        val fullName: String, val leafName: String,
        val isActive: Boolean, val tracking: String? = null
    )
    private data class TagInfo(val name: String)
    private data class StashInfo(val index: Int, val description: String)
    private data class WorktreeInfo(
        val path: String,
        val branch: String?,   // null se bare / detached
        val head: String,
        val isMain: Boolean
    )
    private data class SubmoduleInfo(
        val path: String, val name: String, val hash: String,
        val isDirty: Boolean, val differentCommit: Boolean, val isUninitialized: Boolean
    ) {
        val needsAttention get() = isDirty || differentCommit
    }
    /** Marker for intermediate folder nodes in the submodule subtree. */
    private data class SubmoduleFolderNode(val name: String)

    private val branchRoot         = DefaultMutableTreeNode("root")
    private val localBranchesNode  = DefaultMutableTreeNode("BRANCHES")
    private val tagsNode           = DefaultMutableTreeNode("TAGS")
    private val remoteBranchesNode = DefaultMutableTreeNode("REMOTES")
    private val stashesNode        = DefaultMutableTreeNode("STASHES")
    private val submodulesNode     = DefaultMutableTreeNode("SUBMODULES")
    private val worktreesNode      = DefaultMutableTreeNode("WORKTREES")

    // ── Sidebar progress bar ──────────────────────────────────────────────────
    private fun miniBar() = JProgressBar().apply {
        isIndeterminate = false; value = 0; isStringPainted = false; border = null
        preferredSize = Dimension(0, 3); maximumSize = Dimension(Int.MAX_VALUE, 3)
    }
    private fun JProgressBar.setBusy(busy: Boolean) { isIndeterminate = busy; if (!busy) value = 0 }
    private val sidebarBar = miniBar()

    // ── Submodule state ───────────────────────────────────────────────────────
    private val allSubmodules = mutableListOf<SubmoduleInfo>()
    private val branchTreeModel    = DefaultTreeModel(branchRoot)
    private val branchTree: JTree

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

    private val authorLabel = JLabel(" ").apply {
        border     = BorderFactory.createEmptyBorder(0, 0, 4, 0)
        font       = font.deriveFont(Font.PLAIN, 11f)
        foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
    }

    private val commitMsgField          = JTextArea(4, 40).apply { lineWrap = true; wrapStyleWord = true }
    private val amendCheckBox           = JCheckBox("Amend latest commit")
    private val pushImmediatelyCheckBox = JCheckBox("Push changes immediately to origin/main")

    // ── Badge toolbar buttons ─────────────────────────────────────────────────
    private val commitBtn = BadgeButton(MaterialDesign.MDI_CHECK_CIRCLE, "Commit") { showFileStatus() }
    private val pullBtn   = BadgeButton(MaterialDesign.MDI_ARROW_DOWN_BOLD, "Pull") { pull() }
    private val pushBtn   = BadgeButton(MaterialDesign.MDI_ARROW_UP_BOLD, "Push")  { push() }

    // ── Commit search ─────────────────────────────────────────────────────────
    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholderText", "Search commits…")
    }

    // ── Blame button (in diff top bar) ────────────────────────────────────────
    private val blameBtn      = JButton("Blame").apply { isVisible = false }
    private var currentDiffFile   = ""
    private var currentDiffStaged = false
    private var showingBlame      = false

    // ── Conflict banner ───────────────────────────────────────────────────────
    private val conflictBannerLabel = JLabel()
    private val conflictContinueBtn = JButton("Continue")
    private val conflictAbortBtn    = JButton("Abort")
    private val conflictBanner      = JPanel()

    // ── Main content card layout (History / File Status) ──────────────────────
    private val mainCards     = CardLayout()
    private val mainContainer = JPanel(mainCards)

    private val sideBarSplit: JSplitPane
    private var sideBarOpen            = true
    private var previousSideBarDivider = 240

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        branchRoot.add(localBranchesNode)
        branchRoot.add(tagsNode)
        branchRoot.add(remoteBranchesNode)
        branchRoot.add(stashesNode)
        branchRoot.add(submodulesNode)
        branchRoot.add(worktreesNode)

        branchTree = JTree(branchTreeModel).apply {
            isRootVisible    = false
            showsRootHandles = true
            toggleClickCount = -1   // disable click-count toggle; expand/collapse only via arrow icon
            cellRenderer     = BranchCellRenderer()
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addMouseListener(buildBranchMouseAdapter())
            addTreeSelectionListener { e ->
                if (e.isAddedPath) {
                    val bi = (e.path.lastPathComponent as? DefaultMutableTreeNode)
                        ?.userObject as? BranchInfo ?: return@addTreeSelectionListener
                    scrollCommitTableToBranch(bi.fullName)
                }
            }
        }

        // History view: commit table above, detail panel below
        // Detail panel = [metadata+filelist | diff]
        val leftDetailSplit = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(commitDataPanel),
            buildCommitFileListPanel()
        ).apply { resizeWeight = 0.4; dividerLocation = 180 }

        val commitDetailSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            leftDetailSplit,
            commitDiffScroll
        ).apply { resizeWeight = 0.35 }

        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterCommits(searchField.text)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterCommits(searchField.text)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
        })
        val tableWithSearch = JPanel(BorderLayout()).apply {
            add(searchField,     BorderLayout.NORTH)
            add(commitScrollPane, BorderLayout.CENTER)
        }
        val historyView = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            tableWithSearch,
            commitDetailSplit
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

        loadColumnWidths()
        commitScrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = updateDescriptionColumnWidth()
        })
        commitTable.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) = saveColumnWidths()
        })

        commitTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = commitTable.selectedRow
                if (row in filteredCommits.indices) {
                    val c = filteredCommits[row]
                    if (c.hash == UNCOMMITTED_HASH) showFileStatus()
                    else {
                        currentCommit = c
                        commitDataPanel.showCommit(c)
                        loadCommitFiles(c)
                    }
                }
            }
        }

        installCommitContextMenu()
        refresh()
    }

    // ── Card constants + graph palette ───────────────────────────────────────
    companion object {
        private const val CARD_HISTORY     = "history"
        private const val CARD_FILE_STATUS = "fileStatus"

        const val UNCOMMITTED_HASH = "0000000000000000000000000000000000000000"

        val GRAPH_PALETTE = listOf(
            Color(0x4F, 0xC3, 0xF7),  // sky blue
            Color(0x81, 0xC7, 0x84),  // green
            Color(0xFF, 0xB7, 0x4D),  // amber
            Color(0xF0, 0x62, 0x92),  // pink
            Color(0xCE, 0x93, 0xD8),  // lavender
            Color(0x80, 0xCB, 0xC4),  // teal
            Color(0xFF, 0xF1, 0x76),  // yellow
            Color(0xA5, 0xD6, 0xA7),  // light green
            Color(0xFF, 0xCC, 0xBC),  // peach
            Color(0xB0, 0xBE, 0xC5),  // blue-grey
        )
    }

    // ── File Status panel ─────────────────────────────────────────────────────

    // ── Commit file list panel ────────────────────────────────────────────────

    private fun buildCommitFileListPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        // toolbar: file count label
        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(3, 8, 3, 8)
            isOpaque = false
        }
        commitFileLabel.font = commitFileLabel.font.deriveFont(Font.PLAIN, 11f)
        commitFileLabel.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        toolbar.add(commitFileLabel, BorderLayout.WEST)
        panel.add(toolbar, BorderLayout.NORTH)

        commitFileList.cellRenderer = FileStatusCellRenderer()
        commitFileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val line   = commitFileList.selectedValue ?: return@addListSelectionListener
                val commit = currentCommit            ?: return@addListSelectionListener
                showCommitFileDiff(commit, line)
            }
        }
        panel.add(JScrollPane(commitFileList), BorderLayout.CENTER)
        return panel
    }

    private fun loadCommitFiles(commit: Commit) {
        commitFileModel.clear()
        commitDiffScroll.setViewportView(null)
        commitFileLabel.text = "loading…"
        object : SwingWorker<List<String>, Void>() {
            override fun doInBackground(): List<String> {
                val r = git.commitFiles(commit.hash)
                if (!r.success) return emptyList()
                return r.output.lines().filter { it.isNotBlank() }.map { line ->
                    val tab = line.indexOf('\t')
                    if (tab < 0) line
                    else "${line[0]} ${line.substringAfterLast('\t').trim()}"
                }
            }
            override fun done() {
                val files = try { get() } catch (_: Exception) { return }
                files.forEach { commitFileModel.addElement(it) }
                commitFileLabel.text = "${files.size} file${if (files.size != 1) "s" else ""}"
                if (files.isNotEmpty()) commitFileList.selectedIndex = 0
            }
        }.execute()
    }

    private fun showCommitFileDiff(commit: Commit, fileLine: String) {
        val path = fileLine.substring(2).trim()
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.showFileDiff(commit.hash, path).output
            override fun done() {
                val diff = try { get() } catch (_: Exception) { return }
                commitDiffScroll.setViewportView(
                    buildDiffView(diff, staged = false, file = path, commitHash = commit.hash))
                commitDiffScroll.revalidate()
            }
        }.execute()
    }

    private fun buildFileStatusPanel(): JComponent {
        stagedList.cellRenderer   = FileStatusCellRenderer()
        unstagedList.cellRenderer = FileStatusCellRenderer()

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
                val entry = unstagedList.selectedValue
                showFileDiff(entry.substring(2), staged = false, isUntracked = entry.startsWith("?"))
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
                    unstagedList.selectedValuesList
                        .filter { !it.startsWith("! ") }
                        .forEach { git.add(it.substring(2)) }
                    refreshFileStatus()
                }
            })
            add(JMenuItem("Mark as resolved (git add)").apply {
                addActionListener {
                    unstagedList.selectedValuesList.forEach { git.add(it.substring(2)) }
                    refreshFileStatus()
                }
            })
            addSeparator()
            add(JMenuItem("Ignore…").apply {
                addActionListener {
                    val v = unstagedList.selectedValue ?: return@addActionListener
                    showIgnoreDialog(v.substring(2))
                }
            })
            add(JMenuItem("Blame").apply {
                addActionListener {
                    val v = unstagedList.selectedValue ?: return@addActionListener
                    val file = v.substring(2)
                    blameBtn.isVisible = true
                    showBlame(file, null)
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
        blameBtn.addActionListener {
            if (showingBlame) {
                showingBlame = false; blameBtn.text = "Blame"
                if (currentDiffFile.isNotBlank()) showFileDiff(currentDiffFile, currentDiffStaged)
            } else {
                showingBlame = true; blameBtn.text = "Diff"
                if (currentDiffFile.isNotBlank()) showBlame(currentDiffFile, null)
            }
        }
        val diffTopBar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground") ?: Color(0x44_44_44))
            add(diffFileNameLabel, BorderLayout.WEST)
            add(blameBtn.apply { border = BorderFactory.createEmptyBorder(2, 8, 2, 8) }, BorderLayout.EAST)
        }
        val diffOuter = JPanel(BorderLayout()).apply {
            add(diffTopBar,    BorderLayout.NORTH)
            add(diffScrollPane, BorderLayout.CENTER)
        }

        // ── Conflict banner ───────────────────────────────────────────────────
        conflictContinueBtn.addActionListener {
            val isRebase = java.io.File(git.repo, ".git/rebase-merge").exists() ||
                           java.io.File(git.repo, ".git/rebase-apply").exists()
            if (isRebase) runWithProgress("Continuing rebase…") { git.rebaseContinue() }
            else commitMsgField.requestFocus()
        }
        conflictAbortBtn.addActionListener {
            val isRebase = java.io.File(git.repo, ".git/rebase-merge").exists() ||
                           java.io.File(git.repo, ".git/rebase-apply").exists()
            if (isRebase) runWithProgress("Aborting rebase…")  { git.rebaseAbort()  }
            else          runWithProgress("Aborting merge…")   { git.mergeAbort()   }
            refresh()
        }
        conflictBanner.apply {
            isVisible  = false
            layout     = BorderLayout()
            background = Color(0x60, 0x38, 0x08)
            border     = BorderFactory.createEmptyBorder(5, 8, 5, 8)
            conflictBannerLabel.foreground = Color(0xFF, 0xCC, 0x80)
            add(conflictBannerLabel, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false; add(conflictContinueBtn); add(conflictAbortBtn)
            }, BorderLayout.EAST)
        }

        // ── File list vertical split ──────────────────────────────────────────
        val fileListSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, stagedPanel, unstagedPanel).apply {
            resizeWeight     = 0.4
            dividerLocation  = 160
            minimumSize      = Dimension(320, 0)
        }

        // ── Top half: files | diff ────────────────────────────────────────────
        val topHalf = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileListSplit, diffOuter).apply {
            resizeWeight    = 0.0
            dividerLocation = 340
        }

        // ── Commit area (bottom) ──────────────────────────────────────────────

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

        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, topHalf, commitArea).apply {
            resizeWeight = 0.75
        }
        return JPanel(BorderLayout()).apply {
            add(conflictBanner, BorderLayout.NORTH)
            add(mainSplit,      BorderLayout.CENTER)
        }
    }

    private fun refreshFileStatus() {
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.status().output
            override fun done() {
                val out = try { get() } catch (e: Exception) { return }
                applyFileStatus(out)
            }
        }.execute()
    }

    private fun applyFileStatus(out: String) {
        val prevFile   = currentDiffFile
        val prevStaged = currentDiffStaged

        stagedModel.clear(); unstagedModel.clear()
        val conflictCodes = setOf("UU","AA","DD","AU","UA","DU","UD")
        val conflictFiles = mutableListOf<String>()
        for (line in out.lines()) {
            if (line.length < 3 || line.startsWith("##")) continue
            val x    = line[0]; val y = line[1]; val file = line.substring(3)
            if (matchesIgnorePattern(file)) continue
            val code = "$x$y"
            if (code in conflictCodes) {
                conflictFiles.add(file)
                unstagedModel.addElement("! $file")
            } else {
                if (x != ' ' && x != '?') stagedModel.addElement("$x $file")
                if (y == 'M' || y == 'D' || y == '?') unstagedModel.addElement("$y $file")
            }
        }
        stagedHeaderLabel.text   = "Staged files (${stagedModel.size()} files)"
        unstagedHeaderLabel.text = "Unstaged files (${unstagedModel.size()} files)"

        // Restore previous selection if the file is still in any list; otherwise clear diff
        if (prevFile.isNotBlank()) {
            val sIdx = (0 until stagedModel.size())  .firstOrNull { stagedModel[it].substring(2)   == prevFile }
            val uIdx = (0 until unstagedModel.size()).firstOrNull { unstagedModel[it].substring(2) == prevFile }
            when {
                prevStaged  && sIdx != null -> stagedList.selectedIndex   = sIdx
                !prevStaged && uIdx != null -> unstagedList.selectedIndex = uIdx
                sIdx != null                -> stagedList.selectedIndex   = sIdx
                uIdx != null                -> unstagedList.selectedIndex = uIdx
                else -> { diffScrollPane.setViewportView(null); diffFileNameLabel.text = " "; currentDiffFile = "" }
            }
        } else {
            diffScrollPane.setViewportView(null); diffFileNameLabel.text = " "; currentDiffFile = ""
        }

        // Update conflict banner
        if (conflictFiles.isNotEmpty()) {
            val isRebase = java.io.File(git.repo, ".git/rebase-merge").exists() ||
                           java.io.File(git.repo, ".git/rebase-apply").exists()
            val op = if (isRebase) "Rebase" else "Merge"
            conflictBannerLabel.text = "⚠  $op in progress — ${conflictFiles.size} conflict(s)"
            conflictContinueBtn.text = if (isRebase) "Continue Rebase" else "Commit Merge"
            conflictBanner.isVisible = true
            if (commitMsgField.text.isBlank()) {
                val mergeMsg = java.io.File(git.repo, ".git/MERGE_MSG")
                if (mergeMsg.exists()) commitMsgField.text = mergeMsg.readText()
            }
        } else {
            conflictBanner.isVisible = false
        }
    }

    // ── Diff display ──────────────────────────────────────────────────────────

    private fun showFileStatus() {
        mainCards.show(mainContainer, CARD_FILE_STATUS)
        refreshAuthorLabel()
        refreshFileStatus()
    }

    private fun scrollCommitTableToBranch(branchFullName: String) {
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.revParse(branchFullName).output.trim()
            override fun done() {
                val hash = try { get() } catch (_: Exception) { return }
                if (hash.isBlank()) return
                val row = commitList.indexOfFirst { it.hash.startsWith(hash) || hash.startsWith(it.hash) }
                if (row < 0) return
                mainCards.show(mainContainer, CARD_HISTORY)
                commitTable.selectionModel.setSelectionInterval(row, row)
                commitTable.scrollRectToVisible(commitTable.getCellRect(row, 0, true))
            }
        }.execute()
    }

    private fun refreshAuthorLabel() {
        val name  = git.configGet("user.name").output.trim()
        val email = git.configGet("user.email").output.trim()
        authorLabel.text = when {
            name.isNotBlank()  -> "$name <$email>"
            email.isNotBlank() -> email
            else               -> "Unknown author"
        }
    }

    private fun showFileDiff(file: String, staged: Boolean, isUntracked: Boolean = false) {
        currentDiffFile   = file
        currentDiffStaged = staged
        showingBlame      = false
        blameBtn.text      = "Blame"
        blameBtn.isVisible = !isUntracked
        diffFileNameLabel.text = file
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String = when {
                staged      -> git.diffStaged(file).output
                isUntracked -> git.diffUntracked(file).output   // exit 1 is normal; always use .output
                else        -> git.diff(file).output
            }
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

    private fun buildDiffView(
        diffText: String, staged: Boolean, file: String,
        commitHash: String? = null
    ): JPanel {
        val bgColor   = UIManager.getColor("EditorPane.background") ?: Color(0x2B, 0x2B, 0x2B)
        val container = object : JPanel(), Scrollable {
            override fun getPreferredScrollableViewportSize() = preferredSize
            override fun getScrollableUnitIncrement(r: Rectangle, o: Int, d: Int) = 16
            override fun getScrollableBlockIncrement(r: Rectangle, o: Int, d: Int) = r.height
            override fun getScrollableTracksViewportWidth()  = true
            override fun getScrollableTracksViewportHeight() = false
        }.apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = bgColor
        }
        if (diffText.isBlank()) {
            val msg = when {
                commitHash != null -> "No changes in this file have been detected, or it is a binary file."
                staged             -> "No staged changes in this file."
                else               -> "No changes in this file have been detected, or it is a binary file\nor it is configured to be ignored by the file patterns."
            }
            container.add(object : JPanel(GridBagLayout()), Scrollable {
                override fun getPreferredScrollableViewportSize() = preferredSize
                override fun getScrollableUnitIncrement(r: Rectangle, o: Int, d: Int) = 16
                override fun getScrollableBlockIncrement(r: Rectangle, o: Int, d: Int) = r.height
                override fun getScrollableTracksViewportWidth()  = true
                override fun getScrollableTracksViewportHeight() = true
            }.apply {
                background = bgColor
                maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
                alignmentX = Component.LEFT_ALIGNMENT
                add(JLabel("<html><div style='text-align:center'>${ msg.replace("\n", "<br>") }</div></html>").apply {
                    foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                    font       = font.deriveFont(Font.PLAIN, 12f)
                })
            })
            return container
        }
        val parsed = parseDiff(diffText)
        val actionVerb = if (staged) "Unstage" else "Stage"

        parsed.hunks.forEachIndexed { idx, hunk ->
            val headerInfo = hunk.header.substringAfter("@@").substringBefore("@@").trim()
            val hunkLabel  = JLabel("Hunk ${idx + 1} : $headerInfo").apply {
                border     = BorderFactory.createEmptyBorder(6, 8, 4, 8)
                font       = font.deriveFont(Font.BOLD, 11f)
                foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            }

            val lineList = buildHunkLineList(hunk.lines, bgColor)

            val hunkBtnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply { isOpaque = false }

            if (commitHash != null) {
                // ── Commit view: Reverse hunk only ────────────────────────────
                val reverseBtn = JButton("Reverse hunk")
                reverseBtn.addActionListener {
                    val selIdx = lineList.selectedIndices.toSet()
                    val patch  = if (hasActionableSelection(lineList)) buildLinePatch(parsed.fileHeader, hunk, selIdx)
                                 else buildPatch(parsed.fileHeader, hunk)
                    val r = git.applyPatch(patch, cached = false, reverse = true)
                    if (!r.success) showError("Reverse failed", r.output)
                    else refreshFileStatus()
                }
                hunkBtnRow.add(reverseBtn)
            } else {
                // ── Staging view: Stage / Discard ─────────────────────────────
                val stageBtn   = JButton("$actionVerb hunk")
                val discardBtn = JButton("Discard hunk")

                lineList.selectionModel.addListSelectionListener {
                    val hasLines = hasActionableSelection(lineList)
                    stageBtn.text   = if (hasLines) "$actionVerb lines" else "$actionVerb hunk"
                    discardBtn.text = if (hasLines) "Discard lines"     else "Discard hunk"
                }
                stageBtn.addActionListener {
                    val selIdx = lineList.selectedIndices.toSet()
                    val patch  = if (hasActionableSelection(lineList)) buildLinePatch(parsed.fileHeader, hunk, selIdx)
                                 else buildPatch(parsed.fileHeader, hunk)
                    val r = if (staged) git.applyPatch(patch, cached = true, reverse = true)
                            else        git.applyPatch(patch, cached = true)
                    if (!r.success) showError("$actionVerb failed", r.output)
                    else { showFileDiff(file, staged); refreshFileStatus() }
                }
                discardBtn.addActionListener {
                    val selIdx = lineList.selectedIndices.toSet()
                    val patch  = if (hasActionableSelection(lineList)) buildLinePatch(parsed.fileHeader, hunk, selIdx)
                                 else buildPatch(parsed.fileHeader, hunk)
                    val r = git.applyPatch(patch, cached = false, reverse = true)
                    if (!r.success) showError("Discard failed", r.output)
                    else { showFileDiff(file, staged); refreshFileStatus() }
                }
                if (!staged) hunkBtnRow.add(discardBtn)
                hunkBtnRow.add(stageBtn)
            }
            val hunkHeaderRow = JPanel(BorderLayout()).apply {
                isOpaque    = false
                alignmentX  = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, 36)
                add(hunkLabel,  BorderLayout.WEST)
                add(hunkBtnRow, BorderLayout.EAST)
            }
            container.add(hunkHeaderRow)
            container.add(lineList.apply { alignmentX = Component.LEFT_ALIGNMENT })
            container.add(Box.createVerticalStrut(4))
        }
        container.add(Box.createVerticalGlue())
        return container
    }

    /** JList with per-line coloring and click-to-toggle selection. */
    private fun buildHunkLineList(lines: List<String>, bgColor: Color): JList<String> {
        val fgColor = UIManager.getColor("Label.foreground") ?: Color.WHITE
        val addBg   = Color(0x1B, 0x3A, 0x27)
        val remBg   = Color(0x3A, 0x1B, 0x1B)
        val hunkFg  = Color(0x4F, 0xC3, 0xF7)
        val monoFont = Font(com.gitnarwhal.utils.Settings.diffFontFamily, Font.PLAIN, com.gitnarwhal.utils.Settings.diffFontSize)

        val model = DefaultListModel<String>().apply { lines.forEach { addElement(it) } }

        val list = object : JList<String>(model) {
            override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

            private var wasSelected = false
            private var clickedRow  = -1

            override fun processMouseEvent(e: MouseEvent) {
                // Capture pre-click state before super dispatches to listeners
                if (e.id == MouseEvent.MOUSE_PRESSED && !e.isShiftDown && !e.isControlDown) {
                    val idx = locationToIndex(e.point)
                    clickedRow  = idx
                    wasSelected = idx >= 0 && isSelectedIndex(idx)
                }
                super.processMouseEvent(e)
                // After default processing: if row was already selected, toggle it off
                if (e.id == MouseEvent.MOUSE_RELEASED && !e.isShiftDown && !e.isControlDown) {
                    if (wasSelected && clickedRow >= 0) removeSelectionInterval(clickedRow, clickedRow)
                    wasSelected = false; clickedRow = -1
                }
            }
        }
        list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        list.background    = bgColor
        list.font          = monoFont

        list.setCellRenderer(object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val line  = value as? String ?: ""
                label.font = monoFont
                if (!isSelected) {
                    when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            { label.background = addBg; label.foreground = fgColor }
                        line.startsWith("-") && !line.startsWith("---") ->
                            { label.background = remBg; label.foreground = fgColor }
                        line.startsWith("@@") ->
                            { label.background = bgColor; label.foreground = hunkFg }
                        else ->
                            { label.background = bgColor; label.foreground = fgColor }
                    }
                }
                return label
            }
        })
        return list
    }

    /** True if any selected row in [list] is a diff +/- line (not context/header). */
    private fun hasActionableSelection(list: JList<String>): Boolean =
        list.selectedIndices.any { i ->
            val line = list.model.getElementAt(i)
            (line.startsWith("+") && !line.startsWith("+++")) ||
            (line.startsWith("-") && !line.startsWith("---"))
        }

    /**
     * Build a minimal valid unified diff from only the selected lines.
     * Unselected `-` lines become context (don't stage the removal).
     * Unselected `+` lines are dropped (don't stage the addition).
     */
    private fun buildLinePatch(fileHeader: List<String>, hunk: DiffHunk, selectedIndices: Set<Int>): String {
        val headerMatch = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""").find(hunk.header)
        val oldStart    = headerMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val newStart    = headerMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1

        val patchLines  = mutableListOf<String>()
        var newOldCount = 0
        var newNewCount = 0

        for ((i, line) in hunk.lines.withIndex()) {
            if (i == 0) continue  // skip original @@ header; rebuild below
            val selected = i in selectedIndices
            when {
                line.startsWith("+") && !line.startsWith("+++") -> {
                    if (selected) { patchLines += line; newNewCount++ }
                    // unselected + → omit
                }
                line.startsWith("-") && !line.startsWith("---") -> {
                    if (selected) { patchLines += line; newOldCount++ }
                    else          { patchLines += " ${line.drop(1)}"; newOldCount++; newNewCount++ }
                }
                else -> { patchLines += line; newOldCount++; newNewCount++ }
            }
        }

        return buildString {
            fileHeader.forEach { appendLine(it) }
            appendLine("@@ -$oldStart,$newOldCount +$newStart,$newNewCount @@")
            patchLines.forEach { appendLine(it) }
        }
    }

    // ── Inline commit ─────────────────────────────────────────────────────────

    private fun doCommit() {
        val msg = commitMsgField.text.trim()
        if (msg.isEmpty() && !amendCheckBox.isSelected) {
            showError("Commit", "Commit message is required"); return
        }
        val shouldPush = pushImmediatelyCheckBox.isSelected
        val progress = ProgressOverlay()
        progress.show(SwingUtilities.getRootPane(this), "Committing…")
        object : SwingWorker<Boolean, String>() {
            override fun doInBackground(): Boolean {
                val result = when {
                    amendCheckBox.isSelected && msg.isEmpty() -> git.commitAmendStream(onLine = { publish(it) })
                    amendCheckBox.isSelected                  -> git.commitAmendStream(msg,    onLine = { publish(it) })
                    else                                      -> git.commitStream(msg)               { publish(it) }
                }
                return result.success
            }
            override fun process(chunks: List<String>) { chunks.forEach { progress.appendOutput(it) } }
            override fun done() {
                val ok = try { get() } catch (e: Exception) { false }
                progress.finishStreaming(ok)
                if (ok) {
                    commitMsgField.text                = ""
                    amendCheckBox.isSelected           = false
                    pushImmediatelyCheckBox.isSelected = false
                    refresh()
                    refreshFileStatus()
                    if (shouldPush) push()
                }
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
            val fg = UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY
            when (val obj = node.userObject) {
                is BranchInfo -> {
                    text = obj.leafName
                    font = font.deriveFont(if (obj.isActive) Font.BOLD else Font.PLAIN)
                    icon = null
                }
                is TagInfo -> {
                    text = obj.name
                    font = font.deriveFont(Font.PLAIN)
                    icon = null
                }
                is StashInfo -> {
                    text = obj.description
                    font = font.deriveFont(Font.PLAIN)
                    icon = null
                }
                is WorktreeInfo -> {
                    text        = obj.branch?.removePrefix("refs/heads/") ?: "(bare)"
                    toolTipText = obj.path
                    font        = font.deriveFont(Font.PLAIN)
                    icon        = FontIcon.of(MaterialDesign.MDI_FOLDER_OUTLINE, 14, fg)
                }
                is SubmoduleInfo -> {
                    val dirtyColor  = Color(0xFF, 0xB3, 0x00)
                    val commitColor = Color(0x4F, 0xC3, 0xF7)
                    val mutedColor  = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                    text = obj.name
                    when {
                        obj.isDirty -> {
                            icon        = FontIcon.of(MaterialDesign.MDI_PENCIL, 14, dirtyColor)
                            foreground  = dirtyColor
                            font        = font.deriveFont(Font.BOLD)
                            toolTipText = "${obj.path} — modifiche non committate nel submodulo"
                        }
                        obj.differentCommit -> {
                            icon        = FontIcon.of(MaterialDesign.MDI_SOURCE_FORK, 14, commitColor)
                            foreground  = commitColor
                            font        = font.deriveFont(Font.PLAIN)
                            toolTipText = "${obj.path} — commit registrato diverso da quello locale"
                        }
                        obj.isUninitialized -> {
                            icon        = FontIcon.of(MaterialDesign.MDI_FOLDER_REMOVE, 14, mutedColor)
                            foreground  = mutedColor
                            font        = font.deriveFont(Font.ITALIC)
                            toolTipText = "${obj.path} — non inizializzato"
                        }
                        else -> {
                            icon        = FontIcon.of(MaterialDesign.MDI_FOLDER_OUTLINE, 14, mutedColor)
                            foreground  = mutedColor
                            font        = font.deriveFont(Font.PLAIN)
                            toolTipText = "${obj.path} — aggiornato"
                        }
                    }
                }
                is SubmoduleFolderNode -> {
                    val dirtyColor  = Color(0xFF, 0xB3, 0x00)
                    val commitColor = Color(0x4F, 0xC3, 0xF7)
                    text = obj.name
                    font = font.deriveFont(Font.PLAIN)
                    // breadthFirstEnumeration is consumed-once — collect to List first
                    val descendants = node.breadthFirstEnumeration().asSequence()
                        .mapNotNull { (it as? DefaultMutableTreeNode)?.userObject as? SubmoduleInfo }
                        .toList()
                    val hasDirty   = descendants.any { it.isDirty }
                    val hasCommit  = descendants.any { it.differentCommit }
                    val tint = when { hasDirty -> dirtyColor; hasCommit -> commitColor; else -> fg }
                    icon = FontIcon.of(MaterialDesign.MDI_FOLDER, 14, tint)
                    if (hasDirty)       { foreground = dirtyColor;  font = font.deriveFont(Font.BOLD) }
                    else if (hasCommit) { foreground = commitColor }
                }
                is String -> {
                    text = obj
                    icon = when (obj) {
                        "BRANCHES"   -> FontIcon.of(MaterialDesign.MDI_SOURCE_BRANCH,   14, fg)
                        "TAGS"       -> FontIcon.of(MaterialDesign.MDI_TAG_OUTLINE,     14, fg)
                        "REMOTES"    -> FontIcon.of(MaterialDesign.MDI_CLOUD_OUTLINE,   14, fg)
                        "STASHES"    -> FontIcon.of(MaterialDesign.MDI_PACKAGE_VARIANT, 14, fg)
                        "SUBMODULES" -> FontIcon.of(MaterialDesign.MDI_CUBE_OUTLINE,    14, fg)
                        "WORKTREES"  -> FontIcon.of(MaterialDesign.MDI_FOLDER_MULTIPLE, 14, fg)
                        else         -> null
                    }
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
            val node = tp.lastPathComponent as? DefaultMutableTreeNode ?: return
            when (val obj = node.userObject) {
                is BranchInfo -> {
                    val isLocal = tp.path.any { it === localBranchesNode }
                    buildBranchPopup(obj, isLocal).show(branchTree, e.x, e.y)
                }
                is TagInfo      -> buildTagPopup(obj.name).show(branchTree, e.x, e.y)
                is StashInfo    -> buildStashTreePopup(obj.index).show(branchTree, e.x, e.y)
                is SubmoduleInfo -> buildSubmodulePopup(obj).show(branchTree, e.x, e.y)
                is WorktreeInfo -> buildWorktreePopup(obj).show(branchTree, e.x, e.y)
                is String -> {
                    if (node === worktreesNode)  buildAddWorktreeMenu().show(branchTree, e.x, e.y)
                    else if (node === submodulesNode) buildSubmoduleHeaderMenu().show(branchTree, e.x, e.y)
                }
            }
        }
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                val tp   = branchTree.getPathForLocation(e.x, e.y) ?: return
                val node = tp.lastPathComponent as? DefaultMutableTreeNode ?: return
                when (val obj = node.userObject) {
                    is SubmoduleInfo -> openSubmoduleTab(obj)
                    is WorktreeInfo  -> openWorktreeAsTab(obj)
                    is BranchInfo    -> checkoutBranch(obj.fullName)
                }
            }
        }
    }

    private fun buildBranchPopup(bi: BranchInfo, isLocal: Boolean): JPopupMenu {
        val branchFullName = bi.fullName
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
        if (isLocal) {
            val tracking = bi.tracking
            if (tracking != null) {
                val trackParts  = tracking.split("/", limit = 2)
                val remote      = trackParts[0]
                val remoteBranch = if (trackParts.size > 1) trackParts[1] else branchFullName
                menu.add(menuItem("Push to $tracking") {
                    val rp = SwingUtilities.getRootPane(this) ?: return@menuItem
                    val progress = ProgressOverlay()
                    progress.show(rp, "Pushing…")
                    object : SwingWorker<Boolean, String>() {
                        override fun doInBackground(): Boolean {
                            val r = git.pushRefspecStream(remote, branchFullName, remoteBranch, false, false) { publish(it) }
                            return r.success
                        }
                        override fun process(chunks: List<String>) { chunks.forEach { progress.appendOutput(it) } }
                        override fun done() {
                            val ok = try { get() } catch (e: Exception) { false }
                            progress.finishStreaming(ok); if (ok) refresh()
                        }
                    }.execute()
                })
                menu.add(menuItem("Pull from $tracking") {
                    val rp = SwingUtilities.getRootPane(this) ?: return@menuItem
                    val progress = ProgressOverlay()
                    progress.show(rp, "Pulling…")
                    object : SwingWorker<Boolean, String>() {
                        override fun doInBackground(): Boolean {
                            val r = git.pullStream(remote, remoteBranch) { publish(it) }
                            return r.success
                        }
                        override fun process(chunks: List<String>) { chunks.forEach { progress.appendOutput(it) } }
                        override fun done() {
                            val ok = try { get() } catch (e: Exception) { false }
                            progress.finishStreaming(ok); if (ok) refresh()
                        }
                    }.execute()
                })
            } else {
                menu.add(menuItem("Push…") { push() })
            }
            menu.addSeparator()
        }
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

        panel.add(workspacePanel, BorderLayout.NORTH)
        panel.add(JScrollPane(branchTree), BorderLayout.CENTER)
        panel.add(sidebarBar, BorderLayout.SOUTH)
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
            RepoSettingsDialog(git, win).isVisible = true
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
        val isVirtual = commit.hash == UNCOMMITTED_HASH
        val menu = JPopupMenu()

        // ── Checkout ─────────────────────────────────────────────────────────
        menu.add(menuItem("Checkout…") {
            if (isVirtual) return@menuItem
            if (confirm("Checkout commit ${commit.shortHash}?\n\nThis puts the repo in detached HEAD state.")) {
                val r = git.selectBranch(commit.hash)
                if (!r.success) showError("Checkout failed", r.output) else refresh()
            }
        })

        // ── Merge ────────────────────────────────────────────────────────────
        menu.add(menuItem("Merge…") {
            if (isVirtual) return@menuItem
            if (confirm("Merge commit ${commit.shortHash} into the current branch?")) {
                val r = git.merge(commit.hash)
                if (!r.success) showError("Merge failed", r.output) else refresh()
            }
        })

        // ── Rebase ───────────────────────────────────────────────────────────
        menu.add(menuItem("Rebase…") {
            if (isVirtual) return@menuItem
            if (confirm("Rebase current branch onto ${commit.shortHash}?")) {
                val r = git.rebase(commit.hash)
                if (!r.success) showError("Rebase failed", r.output) else refresh()
            }
        })

        // ── Tag ──────────────────────────────────────────────────────────────
        menu.add(menuItem("Tag…") {
            val win = SwingUtilities.getWindowAncestor(this)
            val hash = if (isVirtual) "HEAD" else commit.hash
            TagDialog(git, hash, win).apply { isVisible = true }
            refresh()
        })

        menu.addSeparator()

        // ── Branch ───────────────────────────────────────────────────────────
        menu.add(menuItem("Create Branch…") {
            val name = JOptionPane.showInputDialog(this, "New branch name:", "Create Branch",
                JOptionPane.QUESTION_MESSAGE)?.trim()?.ifBlank { null } ?: return@menuItem
            val from = if (isVirtual) "HEAD" else commit.hash
            val r = git.createBranchFrom(name, from)
            if (!r.success) showError("Create branch failed", r.output) else refresh()
        })

        menu.addSeparator()

        // ── Cherry-pick ───────────────────────────────────────────────────────
        menu.add(menuItem("Cherry Pick") {
            if (isVirtual) return@menuItem
            if (confirm("Cherry-pick ${commit.shortHash}?")) {
                val r = git.cherryPick(commit.hash)
                if (!r.success) showError("Cherry-pick failed", r.output) else refresh()
            }
        }.also { it.isEnabled = !isVirtual })

        // ── Revert ───────────────────────────────────────────────────────────
        menu.add(menuItem("Reverse Commit…") {
            if (isVirtual) return@menuItem
            if (confirm("Revert ${commit.shortHash}? (creates a new revert commit)")) {
                val r = git.revert(commit.hash)
                if (!r.success) showError("Revert failed", r.output) else refresh()
            }
        }.also { it.isEnabled = !isVirtual })

        menu.addSeparator()

        // ── Reset ────────────────────────────────────────────────────────────
        val resetMenu = JMenu("Reset current branch to this commit")
        resetMenu.isEnabled = !isVirtual
        resetMenu.add(menuItem("Soft  — keep index and working tree") {
            if (confirm("git reset --soft ${commit.shortHash}?")) {
                val r = git.reset(commit.hash, "soft")
                if (!r.success) showError("Reset failed", r.output) else refresh()
            }
        })
        resetMenu.add(menuItem("Mixed  — unstage changes") {
            if (confirm("git reset --mixed ${commit.shortHash}?")) {
                val r = git.reset(commit.hash, "mixed")
                if (!r.success) showError("Reset failed", r.output) else refresh()
            }
        })
        resetMenu.add(menuItem("Hard  — DISCARD all changes (destructive!)") {
            if (confirm("DESTRUCTIVE: git reset --hard ${commit.shortHash}\n\nAll uncommitted changes will be lost.")) {
                val r = git.reset(commit.hash, "hard")
                if (!r.success) showError("Reset failed", r.output) else refresh()
            }
        })
        menu.add(resetMenu)

        menu.addSeparator()

        // ── Copy ─────────────────────────────────────────────────────────────
        menu.add(menuItem("Copy SHA to Clipboard")       { if (!isVirtual) copyToClipboard(commit.hash) })
        menu.add(menuItem("Copy short SHA to Clipboard") { if (!isVirtual) copyToClipboard(commit.shortHash) })

        return menu
    }

    // ── Stash ─────────────────────────────────────────────────────────────────

    private fun buildStashTreePopup(idx: Int): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Apply") {
            val r = git.stashApply(idx)
            if (!r.success) showError("Stash op failed", r.output); refresh()
        })
        menu.add(menuItem("Pop") {
            val r = git.stashPop(idx)
            if (!r.success) showError("Stash op failed", r.output); refresh()
        })
        menu.add(menuItem("Drop") {
            if (confirm("Drop stash@{$idx}? (irreversible)")) {
                val r = git.stashDrop(idx)
                if (!r.success) showError("Drop failed", r.output); refreshStashes()
            }
        })
        return menu
    }

    // ── Submodule ─────────────────────────────────────────────────────────────

    private fun buildSubmodulePopup(info: SubmoduleInfo): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Open as Tab") { openSubmoduleTab(info) })
        menu.addSeparator()
        menu.add(menuItem("Update Submodule") {
            val r = com.gitnarwhal.utils.Command(
                com.gitnarwhal.backend.Git.GIT,
                "submodule", "update", "--init", "--recursive", info.path,
                path = git.repo
            ).execute()
            if (!r.success) showError("Update failed", r.output) else refreshSubmodules()
        })
        return menu
    }

    private fun buildSubmoduleHeaderMenu(): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Refresh Submodules") { refreshSubmodules() })
        return menu
    }

    private fun openSubmoduleTab(info: SubmoduleInfo) {
        val mv      = mainView() ?: return
        val subPath = java.io.File(git.repo, info.path).canonicalPath
        val existing = (0 until mv.tabPane.tabCount)
            .mapNotNull { mv.tabPane.getComponentAt(it) as? RepoTab }
            .firstOrNull { java.io.File(it.path).canonicalPath == subPath }
        if (existing != null) mv.selectTab(existing)
        else mv.addTab(RepoTab(subPath, info.name))
    }

    /**
     * Parses `git submodule status` output. Probes each submodule's working tree
     * for uncommitted changes (one extra git-status per submodule). Runs on a
     * background thread.
     *
     * Flag meanings: ' ' clean · '+' different commit · '-' uninitialized · 'U' conflicts.
     */
    private fun parseSubmodules(output: String): List<SubmoduleInfo> {
        val subs = mutableListOf<SubmoduleInfo>()
        for (line in output.lines()) {
            if (line.isBlank()) continue
            val flag = line[0]
            val rest = line.drop(1).trim()
            val spaceIdx = rest.indexOf(' ')
            if (spaceIdx < 0) continue
            val hash      = rest.substring(0, spaceIdx)
            val remainder = rest.substring(spaceIdx + 1).trim()
            val subPath   = remainder.substringBefore(" (").substringBefore("\t").trim()
            if (subPath.isBlank()) continue
            val name = subPath.substringAfterLast("/").ifBlank { subPath }
            val uninitialized = flag == '-'
            subs += SubmoduleInfo(
                path            = subPath,
                name            = name,
                hash            = hash,
                isDirty         = if (uninitialized) false else git.submoduleDirty(subPath),
                differentCommit = flag == '+' || flag == 'U',
                isUninitialized = uninitialized
            )
        }
        return subs
    }

    private fun applySubmodules(subs: List<SubmoduleInfo>) {
        allSubmodules.clear()
        allSubmodules.addAll(subs)
        submodulesNode.removeAllChildren()
        for (info in allSubmodules) insertSubmoduleNode(info)
        branchTreeModel.nodeStructureChanged(submodulesNode)
        for (i in 0 until branchTree.rowCount) branchTree.expandRow(i)
    }

    /** Inserts a submodule leaf into the tree, creating intermediate folder nodes for path segments. */
    private fun insertSubmoduleNode(info: SubmoduleInfo) {
        val segs    = info.path.split("/")
        var current = submodulesNode
        for (i in 0 until segs.size - 1) {
            val seg = segs[i]
            current = current.children().asSequence()
                .filterIsInstance<DefaultMutableTreeNode>()
                .firstOrNull { it.userObject == SubmoduleFolderNode(seg) }
                ?: DefaultMutableTreeNode(SubmoduleFolderNode(seg)).also { current.add(it) }
        }
        current.add(DefaultMutableTreeNode(info))
    }

    fun refreshSubmodules() {
        sidebarBar.setBusy(true)
        object : SwingWorker<List<SubmoduleInfo>, Void>() {
            override fun doInBackground() = parseSubmodules(git.submoduleStatus().output)
            override fun done() {
                sidebarBar.setBusy(false)
                val subs = try { get() } catch (_: Exception) { return }
                applySubmodules(subs)
            }
        }.execute()
    }

    // ── Worktree ──────────────────────────────────────────────────────────────

    private fun buildWorktreePopup(wt: WorktreeInfo): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Open as Tab") { openWorktreeAsTab(wt) })
        if (!wt.isMain) {
            menu.addSeparator()
            menu.add(menuItem("Remove Worktree…") {
                if (confirm("Remove worktree at '${wt.path}'?")) {
                    val r = git.worktreeRemove(wt.path)
                    if (!r.success) showError("Remove failed", r.output)
                    refreshWorktrees()
                }
            })
        }
        return menu
    }

    private fun buildAddWorktreeMenu(): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Add Worktree…") { showAddWorktreeDialog() })
        return menu
    }

    private fun showAddWorktreeDialog() {
        val pathField   = JTextField(30)
        val browseBtn   = JButton("Browse…")
        val branchField = JTextField(20)

        browseBtn.addActionListener {
            val dir = NativeFileChooser.chooseDirectory(
                SwingUtilities.getWindowAncestor(this), "Worktree directory"
            ) ?: return@addActionListener
            pathField.text = dir.absolutePath
        }

        val panel = JPanel(GridBagLayout())
        val gbc   = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST }

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("Path:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(pathField, gbc)
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(browseBtn, gbc)

        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("Branch / commit:"), gbc)
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(branchField, gbc)

        val ok = JOptionPane.showConfirmDialog(
            this, panel, "Add Worktree",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (ok != JOptionPane.OK_OPTION) return
        val p = pathField.text.trim(); val b = branchField.text.trim()
        if (p.isBlank() || b.isBlank()) return
        val r = git.worktreeAdd(p, b)
        if (!r.success) showError("Add worktree failed", r.output)
        refreshWorktrees()
    }

    private fun mainView(): MainView? =
        SwingUtilities.getAncestorOfClass(MainView::class.java, this) as? MainView

    private fun openWorktreeAsTab(wt: WorktreeInfo) {
        val mv         = mainView() ?: return
        val branchName = wt.branch?.removePrefix("refs/heads/") ?: wt.head.take(7)
        mv.addTab(RepoTab(wt.path, branchName))
    }

    fun refreshWorktrees() {
        sidebarBar.setBusy(true)
        object : SwingWorker<List<Git.WorktreeEntry>, Void>() {
            override fun doInBackground() = git.worktreeList()
            override fun done() {
                sidebarBar.setBusy(false)
                val list = try { get() } catch (e: Exception) { emptyList() }
                applyWorktrees(list)
            }
        }.execute()
    }

    private fun applyWorktrees(entries: List<Git.WorktreeEntry>) {
        worktreesNode.removeAllChildren()
        for (e in entries) {
            worktreesNode.add(DefaultMutableTreeNode(
                WorktreeInfo(e.path, e.branch, e.head, e.isMain)
            ))
        }
        branchTreeModel.nodeStructureChanged(worktreesNode)
        for (i in 0 until branchTree.rowCount) branchTree.expandRow(i)
    }

    private fun buildTagPopup(tagName: String): JPopupMenu {
        val menu = JPopupMenu()
        menu.add(menuItem("Checkout") {
            val r = git.selectBranch(tagName)
            if (!r.success) showError("Checkout failed", r.output) else refresh()
        })
        menu.addSeparator()
        menu.add(menuItem("Delete") {
            if (confirm("Delete tag '$tagName'?")) {
                val r = git.tagDelete(tagName)
                if (!r.success) showError("Delete tag failed", r.output) else refreshBranches()
            }
        })
        return menu
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
        val tagsOut: String,
        val statusOut: String,
        val modifiedCount: Int,
        val unpulledCount: Int,
        val unpushedCount: Int,
        val worktreeEntries: List<Git.WorktreeEntry>
    )

    fun refresh() {
        sidebarBar.setBusy(true)
        object : SwingWorker<RefreshSnapshot, Void>() {
            override fun doInBackground(): RefreshSnapshot {
                val b  = git.branches()
                val l  = git.log()
                val s  = git.stashList()
                val t  = git.tags()
                val st = git.status()
                val modified = st.output.lines().count { it.length > 2 && !it.startsWith("##") }
                val unpulled = git.unpulledCount().output.trim().toIntOrNull() ?: 0
                val unpushed = git.unpushedCount().output.trim().toIntOrNull() ?: 0
                val wt = git.worktreeList()
                return RefreshSnapshot(
                    b.output, b.success, l.output, l.success,
                    if (s.success) s.output else "",
                    if (t.success) t.output else "",
                    st.output,
                    modified, unpulled, unpushed,
                    wt
                )
            }
            override fun done() {
                sidebarBar.setBusy(false)
                val snap = try { get() } catch (e: Exception) { sidebarBar.setBusy(false); return }
                if (snap.branchOk) applyBranches(snap.branchesOut)
                if (snap.logOk)    applyCommits(snap.logOut, snap.modifiedCount > 0)
                applyStashes(snap.stashOut)
                applyTags(snap.tagsOut)
                applyFileStatus(snap.statusOut)
                applyWorktrees(snap.worktreeEntries)
                commitBtn.badge = snap.modifiedCount
                pullBtn.badge   = snap.unpulledCount
                pushBtn.badge   = snap.unpushedCount
                // Submodule status can be slow (one git-status per submodule) — run separately
                refreshSubmodules()
            }
        }.execute()
    }

    fun refreshBranches() {
        sidebarBar.setBusy(true)
        object : SwingWorker<Pair<String?, String>, Void>() {
            override fun doInBackground(): Pair<String?, String> {
                val b = git.branches()
                val t = git.tags()
                return (b.output.takeIf { b.success }) to (if (t.success) t.output else "")
            }
            override fun done() {
                sidebarBar.setBusy(false)
                val (branchOut, tagsOut) = try { get() } catch (e: Exception) { sidebarBar.setBusy(false); return }
                if (branchOut != null) applyBranches(branchOut)
                applyTags(tagsOut)
            }
        }.execute()
    }

    fun refreshStashes() {
        sidebarBar.setBusy(true)
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.stashList().output
            override fun done() {
                sidebarBar.setBusy(false)
                val out = try { get() } catch (e: Exception) { sidebarBar.setBusy(false); "" }
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

    private fun applyCommits(logOutput: String, hasUncommitted: Boolean = false) {
        val localMap      = LinkedHashMap<String, Commit>()
        val localList     = mutableListOf<Commit>()
        val parentHashMap = mutableMapOf<String, List<String>>()  // hash → parent hashes (for pass 2)
        for (record in logOutput.split('')) {
            val f = record.split('')
            if (f.size < 8) continue
            val hash = f[0].trim(); if (hash.isBlank()) continue
            // Pass 1: populate commit data; parent linking deferred to pass 2
            val commit = localMap.getOrPut(hash) { Commit(hash, this) }
            commit.prePopulate(listOf(f[2], f[3], f[4], f[5], f[6], f[7]))
            val decoration = if (f.size > 8) f[8] else ""
            commit.refs = parseRefs(decoration)
            commit.isCurrentHead = decoration.split(", ").any { p ->
                val t = p.trim(); t.startsWith("HEAD -> ") || t == "HEAD"
            }
            parentHashMap[hash] = f[1].split(" ").filter { it.isNotBlank() }
        }
        // Pass 2: link parents/children — ALL commits now in localMap, lookups succeed
        for ((h, parentHashes) in parentHashMap) {
            val commit = localMap[h] ?: continue
            for (ph in parentHashes) {
                val p = localMap[ph] ?: continue
                if (p !in commit.parents) { commit.parents.add(p); p.childs.add(commit) }
            }
        }
        // ── Topological sort (post-order DFS, newest first → y=0 = top row) ──
        var y = 0
        fun dfs(c: Commit) {
            if (!c.explored) { c.explored = true; c.childs.forEach { dfs(it) }; c.y = y++ }
        }
        localMap.values.sortedBy { runCatching { -it.committerTimeStamp.toLong() }.getOrDefault(0L) }
            .forEach { dfs(it) }
        localMap.values.sortedBy { it.y }.forEach { localList.add(it) }

        // ── Lane (x) assignment ───────────────────────────────────────────────
        // laneHashes[i] = hash of next commit expected in lane i (null = free)
        val laneHashes = mutableListOf<String?>()
        val laneColors = mutableListOf<Color>()
        var colorIdx = 0

        fun nextColor() = GRAPH_PALETTE[colorIdx++ % GRAPH_PALETTE.size]

        fun allocateLane(hash: String, color: Color): Int {
            val free = laneHashes.indexOfFirst { it == null }
            return if (free >= 0) {
                laneHashes[free] = hash; laneColors[free] = color; free
            } else {
                laneHashes.add(hash); laneColors.add(color); laneHashes.size - 1
            }
        }

        for (commit in localList) {
            val topLines = laneHashes.indices
                .filter { laneHashes[it] != null }
                .map { it to laneColors[it] }
            val topSet = topLines.map { it.first }.toSet()  // lanes active before this row

            val laneIdx = laneHashes.indexOf(commit.hash)
            val col: Int
            if (laneIdx >= 0) {
                col = laneIdx; commit.color = laneColors[col]
            } else {
                val c = nextColor(); col = allocateLane(commit.hash, c); commit.color = c
            }
            commit.x = col

            val forkLines = mutableListOf<Pair<Int, Color>>()
            if (commit.parents.isEmpty()) {
                laneHashes[col] = null
            } else {
                val fp     = commit.parents[0]
                val fpLane = laneHashes.indexOf(fp.hash)
                when {
                    fpLane >= 0 && fpLane != col -> {
                        laneHashes[col] = null
                        forkLines.add(fpLane to commit.color)  // use the branch's own color, not the ancestor's
                    }
                    fpLane < 0 -> laneHashes[col] = fp.hash
                    // fpLane == col: already correct
                }
                for (parent in commit.parents.drop(1)) {
                    val pLane = laneHashes.indexOf(parent.hash)
                    if (pLane >= 0) {
                        forkLines.add(pLane to laneColors[pLane])
                    } else {
                        val c = nextColor(); val nl = allocateLane(parent.hash, c)
                        forkLines.add(nl to c)
                    }
                }
            }

            // Exclude newly-created fork target lanes: their vertical starts in the NEXT row.
            // The fork diagonal already connects the commit dot to them; a mid-starting
            // vertical in this row would create the phantom "stub" line.
            val newForkLanes = forkLines.map { it.first }.filter { it !in topSet }.toSet()
            val bottomLines = laneHashes.indices
                .filter { laneHashes[it] != null && it !in newForkLanes }
                .map { it to laneColors[it] }

            commit.graphTopLines    = topLines
            commit.graphBottomLines = bottomLines
            commit.graphForkLines   = forkLines
        }

        // ── Uncommitted-changes virtual node ─────────────────────────────────
        if (hasUncommitted && localList.isNotEmpty()) {
            val head = localList.first()
            val virtual = Commit(UNCOMMITTED_HASH, this).apply {
                prePopulate(listOf("", "", "", "", "", "Uncommitted changes"))
                x     = head.x
                y     = -1
                color = Color.GRAY
                refs  = head.refs                                    // move branch pills here
                graphTopLines    = emptyList()
                graphBottomLines = listOf(head.x to Color.GRAY)
                graphForkLines   = emptyList()
            }
            head.refs          = emptyList()                         // pills moved to virtual
            head.graphTopLines = head.graphTopLines + (head.x to Color.GRAY)
            localList.add(0, virtual)
        }

        // ── Apply to table ────────────────────────────────────────────────────
        val previousHash = filteredCommits.getOrNull(commitTable.selectedRow)?.hash
        commitList.clear(); commitList.addAll(localList)
        filterCommits(searchField.text)   // repopulates filteredCommits + fires tableDataChanged

        val maxLanes = (localList.maxOfOrNull { it.x } ?: 0) + 1
        val colW = (maxLanes * CommitGraphCell.LANE_W + CommitGraphCell.H_OFFSET * 2).coerceIn(60, 300)
        commitTable.columnModel.getColumn(0).apply { preferredWidth = colW; width = colW }
        updateDescriptionColumnWidth()

        // Restore previous selection; fall back to row 0 (HEAD / uncommitted)
        val targetRow = if (previousHash != null)
            filteredCommits.indexOfFirst { it.hash == previousHash }.takeIf { it >= 0 } ?: 0
        else 0
        if (filteredCommits.isNotEmpty()) {
            commitTable.selectionModel.setSelectionInterval(targetRow, targetRow)
            commitTable.scrollRectToVisible(commitTable.getCellRect(targetRow, 0, true))
        }
    }

    // ── Column width persistence ──────────────────────────────────────────────

    private fun loadColumnWidths() {
        val w = Settings.columnWidths
        listOf(0 to "graph", 2 to "date", 3 to "committer", 4 to "commit").forEach { (col, key) ->
            val saved = w.optInt(key, 0)
            if (saved > 0) commitTable.columnModel.getColumn(col).apply {
                preferredWidth = saved; width = saved
            }
        }
    }

    private fun saveColumnWidths() {
        val w = JSONObject()
        w.put("graph",     commitTable.columnModel.getColumn(0).width)
        w.put("date",      commitTable.columnModel.getColumn(2).width)
        w.put("committer", commitTable.columnModel.getColumn(3).width)
        w.put("commit",    commitTable.columnModel.getColumn(4).width)
        Settings.columnWidths = w
    }

    private fun updateDescriptionColumnWidth() {
        val viewW = commitScrollPane.viewport.width.takeIf { it > 0 } ?: return
        val fixedW = listOf(0, 2, 3, 4).sumOf { commitTable.columnModel.getColumn(it).width }
        val descW = (viewW - fixedW).coerceAtLeast(50)
        commitTable.columnModel.getColumn(1).apply { preferredWidth = descW; width = descW }
    }

    private fun parseRefs(decoration: String): List<RefInfo> {
        if (decoration.isBlank()) return emptyList()
        val result = mutableListOf<RefInfo>()
        for (rawPart in decoration.split(", ")) {
            val part = rawPart.trim()
            when {
                part.startsWith("HEAD -> ") -> {
                    val branch = part.removePrefix("HEAD -> ")
                    result.add(RefInfo(branch, if (branch.contains("/")) RefType.REMOTE_BRANCH else RefType.LOCAL_BRANCH))
                }
                part == "HEAD"           -> result.add(RefInfo("HEAD", RefType.HEAD))
                part.startsWith("tag: ") -> result.add(RefInfo(part.removePrefix("tag: "), RefType.TAG))
                part.contains(" -> ")    -> { /* remote HEAD alias e.g. origin/HEAD -> origin/main */ }
                part.contains("/")       -> result.add(RefInfo(part, RefType.REMOTE_BRANCH))
                part.isNotBlank()        -> result.add(RefInfo(part, RefType.LOCAL_BRANCH))
            }
        }
        return result
    }

    private fun applyStashes(output: String) {
        stashesNode.removeAllChildren()
        output.lines().forEachIndexed { idx, line ->
            if (line.isNotBlank()) stashesNode.add(DefaultMutableTreeNode(StashInfo(idx, line)))
        }
        branchTreeModel.nodeStructureChanged(stashesNode)
        branchTree.expandPath(javax.swing.tree.TreePath(arrayOf<Any>(branchRoot, stashesNode)))
    }

    private fun applyTags(output: String) {
        tagsNode.removeAllChildren()
        output.lines().filter { it.isNotBlank() }.forEach { tag ->
            tagsNode.add(DefaultMutableTreeNode(TagInfo(tag.trim())))
        }
        branchTreeModel.nodeStructureChanged(tagsNode)
        branchTree.expandPath(javax.swing.tree.TreePath(arrayOf<Any>(branchRoot, tagsNode)))
    }

    // ── Toolbar git actions ───────────────────────────────────────────────────

    fun commit() {
        CommitDialog(SwingUtilities.getWindowAncestor(this), git, onSuccess = { refresh() }).isVisible = true
    }

    fun fetch() = runWithProgress("Fetching…") { git.fetch() }
    fun pull()  = PullOverlay(git).show(SwingUtilities.getRootPane(this)) { refresh() }
    fun push()  = PushOverlay(git, tabTitle).show(SwingUtilities.getRootPane(this)) { refresh() }

    private fun runWithProgress(title: String, op: () -> Command) {
        val overlay = ProgressOverlay()
        val rp      = SwingUtilities.getRootPane(this)
        object : SwingWorker<Command, Void>() {
            override fun doInBackground() = op()
            override fun done() {
                val cmd = try { get() } catch (e: Exception) {
                    overlay.finish("Error: ${e.message}", false); return
                }
                overlay.finish(cmd.output, cmd.success)
                refresh()
            }
        }.execute()
        overlay.show(rp, title)
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    fun showHistoryView()    = mainCards.show(mainContainer, CARD_HISTORY)
    fun showFileStatusView() = showFileStatus()
    fun openSettings()       { RepoSettingsDialog(git, SwingUtilities.getWindowAncestor(this)).isVisible = true }

    fun openTerminal() = Thread {
        val custom = Settings.terminalCommand.trim()
        if (custom.isNotBlank()) {
            val expanded = custom.replace("\$REPO", path)
            Command(expanded).execute(path)
        } else {
            OS.TERMINAL.execute(path)
        }
    }.start()
    fun openExplorer() = Thread { (OS.EXPLORER + path).execute() }.start()
    fun openRemote()   = Thread { (OS.BROWSER  + git.remoteUrl().output).execute() }.start()

    fun selectCommit(hash: String) {
        val idx = filteredCommits.indexOfFirst { it.hash == hash }
        if (idx >= 0) {
            mainCards.show(mainContainer, CARD_HISTORY)
            commitTable.selectionModel.setSelectionInterval(idx, idx)
            commitTable.scrollRectToVisible(commitTable.getCellRect(idx, 0, true))
        }
    }

    private fun matchesIgnorePattern(filename: String): Boolean {
        val patterns = com.gitnarwhal.utils.Settings.diffIgnorePatterns
            .split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        if (patterns.isEmpty()) return false
        val leaf = java.nio.file.Path.of(filename.substringAfterLast("/"))
        return patterns.any { pat ->
            try { java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pat").matches(leaf) }
            catch (_: Exception) { false }
        }
    }

    // ── Commit search ─────────────────────────────────────────────────────────

    private fun filterCommits(query: String) {
        val q = query.trim().lowercase()
        filteredCommits.clear()
        filteredCommits.addAll(
            if (q.isEmpty()) commitList
            else commitList.filter { c ->
                runCatching {
                    c.title.lowercase().contains(q) ||
                    c.author.lowercase().contains(q) ||
                    c.committer.lowercase().contains(q) ||
                    c.hash.startsWith(q, ignoreCase = true) ||
                    c.shortHash.startsWith(q, ignoreCase = true)
                }.getOrDefault(false)
            }
        )
        commitTableModel.fireTableDataChanged()
    }

    // ── .gitignore quick-add ──────────────────────────────────────────────────

    private fun showIgnoreDialog(filePath: String) {
        val name   = filePath.substringAfterLast("/")
        val ext    = if ('.' in name) "*.${name.substringAfterLast('.')}" else null
        val folder = if ('/' in filePath) "${filePath.substringBeforeLast('/')}/" else null
        val opts   = listOfNotNull(name, ext, folder).toTypedArray()
        val combo  = JComboBox(opts)
        val result = JOptionPane.showConfirmDialog(this, combo, "Add to .gitignore",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result != JOptionPane.OK_OPTION) return
        java.io.File(git.repo, ".gitignore").appendText("\n${combo.selectedItem}\n")
        refreshFileStatus()
    }

    // ── File blame ────────────────────────────────────────────────────────────

    private fun showBlame(file: String, rev: String?) {
        diffFileNameLabel.text = "$file  (blame)"
        object : SwingWorker<String, Void>() {
            override fun doInBackground() = git.blame(file, rev).output
            override fun done() {
                val out = try { get() } catch (e: Exception) { return }
                diffScrollPane.setViewportView(buildBlameView(out))
                diffScrollPane.revalidate()
            }
        }.execute()
    }

    private fun buildBlameView(blameOutput: String): JTextArea =
        JTextArea(blameOutput).apply {
            isEditable = false
            font       = Font(com.gitnarwhal.utils.Settings.diffFontFamily, Font.PLAIN, com.gitnarwhal.utils.Settings.diffFontSize)
            background = UIManager.getColor("EditorPane.background") ?: Color(0x2B, 0x2B, 0x2B)
            foreground = UIManager.getColor("Label.foreground") ?: Color.WHITE
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

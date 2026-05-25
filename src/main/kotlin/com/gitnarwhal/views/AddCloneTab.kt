package com.gitnarwhal.views

import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.AddCloneTab.AddTab
import com.gitnarwhal.components.AddCloneTab.CloneTab
import com.gitnarwhal.components.AddCloneTab.CreateTab
import com.gitnarwhal.utils.NativeFileChooser
import com.gitnarwhal.utils.RecentReposService
import com.gitnarwhal.utils.RecentReposService.RecentRepo
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign.MaterialDesign
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class AddCloneTab(val mainView: MainView) : JPanel(BorderLayout()) {

    val tabTitle: String = "New Tab"

    private val cards     = CardLayout()
    private val container = JPanel(cards)

    val cloneTab  = CloneTab(this)
    val addTab    = AddTab(this)
    val createTab = CreateTab(this)

    private val localBtn  = NavTabButton("Local",  MaterialDesign.MDI_LAPTOP)
    private val remoteBtn = NavTabButton("Remote", MaterialDesign.MDI_CLOUD)
    private val cloneBtn  = ActionNavButton("Clone",  MaterialDesign.MDI_DOWNLOAD)
    private val addBtn    = ActionNavButton("Add",    MaterialDesign.MDI_FOLDER_PLUS)
    private val createBtn = ActionNavButton("Create", MaterialDesign.MDI_PLUS)

    val activateCloneTab:  JButton get() = cloneBtn
    val activateAddTab:    JButton get() = addBtn
    val activateCreateTab: JButton get() = createBtn

    // ── Local repos list ──────────────────────────────────────────────────────
    private val repoListModel  = DefaultListModel<RepoEntry>()
    private val repoList       = JList(repoListModel)
    private val searchField    = JTextField()
    private var allEntries     = listOf<RepoEntry>()

    data class RepoEntry(
        val repo: RecentRepo,
        var branch: String? = null,
        var modifiedCount: Int? = null,
        var missing: Boolean = false
    )

    init {
        container.add(buildLocalPanel(), CARD_LOCAL)
        container.add(JPanel().apply { add(JLabel("Remote not yet implemented")) }, CARD_REMOTE)
        container.add(cloneTab,  CARD_CLONE)
        container.add(addTab,    CARD_ADD)
        container.add(createTab, CARD_CREATE)

        // ── Nav bar ───────────────────────────────────────────────────────────
        val navBar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground") ?: Color(0x44_44_44))
        }
        navBar.add(Box.createHorizontalStrut(8))
        navBar.add(localBtn)
        navBar.add(remoteBtn)
        navBar.add(Box.createHorizontalStrut(12))

        // thin separator between Local/Remote and action buttons
        val sep = JSeparator(SwingConstants.VERTICAL).apply {
            maximumSize = Dimension(1, 40)
            preferredSize = Dimension(1, 40)
        }
        navBar.add(sep)
        navBar.add(Box.createHorizontalStrut(12))

        navBar.add(cloneBtn)
        navBar.add(addBtn)
        navBar.add(createBtn)
        navBar.add(Box.createHorizontalGlue())

        localBtn.addActionListener  { switchTo(CARD_LOCAL) }
        remoteBtn.addActionListener { switchTo(CARD_REMOTE) }
        cloneBtn.addActionListener  { switchTo(CARD_CLONE) }
        addBtn.addActionListener    { switchTo(CARD_ADD) }
        createBtn.addActionListener { switchTo(CARD_CREATE) }

        add(navBar,    BorderLayout.NORTH)
        add(container, BorderLayout.CENTER)

        switchTo(CARD_LOCAL)
        loadRecentRepos()
    }

    // ── Local panel ───────────────────────────────────────────────────────────

    private fun buildLocalPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 0))

        // Search bar
        val searchPanel = JPanel(BorderLayout(6, 0)).apply {
            border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        }
        searchField.toolTipText = "Search repositories"
        searchPanel.add(JLabel("Search:"), BorderLayout.WEST)
        searchPanel.add(searchField, BorderLayout.CENTER)
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filterList()
            override fun removeUpdate(e: DocumentEvent) = filterList()
            override fun changedUpdate(e: DocumentEvent) = filterList()
        })

        // Repo list
        repoList.cellRenderer = RepoListRenderer()
        repoList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        repoList.fixedCellHeight = 56
        repoList.border = BorderFactory.createEmptyBorder()
        repoList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelected()
            }
        })
        repoList.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) openSelected()
            }
        })

        val scroll = JScrollPane(repoList).apply {
            border = BorderFactory.createEmptyBorder()
        }

        // Bottom bar
        val bottomBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0,
                UIManager.getColor("Separator.foreground") ?: Color(0x44_44_44))
        }
        val refreshBtn = JButton("Refresh").apply {
            isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { loadRecentRepos() }
        }
        val terminalBtn = JButton("Open in Terminal").apply {
            isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { openSelectedInTerminal() }
        }
        bottomBar.add(refreshBtn)
        bottomBar.add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(1, 16) })
        bottomBar.add(terminalBtn)

        panel.add(searchPanel, BorderLayout.NORTH)
        panel.add(scroll,      BorderLayout.CENTER)
        panel.add(bottomBar,   BorderLayout.SOUTH)
        return panel
    }

    private fun loadRecentRepos() {
        allEntries = RecentReposService.getAll().map { repo ->
            RepoEntry(repo, missing = !File(repo.path).isDirectory)
        }
        filterList()
        // async: load branch + modified count for each entry
        allEntries.forEachIndexed { idx, entry ->
            if (!entry.missing) loadRepoMeta(entry)
        }
    }

    private fun filterList() {
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isEmpty()) allEntries
        else allEntries.filter { it.repo.name.lowercase().contains(query) || it.repo.path.lowercase().contains(query) }
        repoListModel.clear()
        filtered.forEach { repoListModel.addElement(it) }
    }

    private fun loadRepoMeta(entry: RepoEntry) {
        object : SwingWorker<Pair<String?, Int?>, Void>() {
            override fun doInBackground(): Pair<String?, Int?> {
                return try {
                    val git = Git(entry.repo.path)
                    val branchOut = git.currentBranch().output.trim().ifBlank { null }
                    val statusOut = git.status().output
                    val modified = statusOut.lines()
                        .filter { it.length >= 3 && !it.startsWith("##") }
                        .count { it[0] != '?' || it[1] != '?' }
                    branchOut to if (modified > 0) modified else null
                } catch (_: Exception) { null to null }
            }
            override fun done() {
                val (branch, count) = try { get() } catch (_: Exception) { null to null }
                entry.branch = branch
                entry.modifiedCount = count
                // repaint the visible cell
                val idx = repoListModel.indexOf(entry)
                if (idx >= 0) repoListModel.set(idx, entry)
            }
        }.execute()
    }

    private fun openSelected() {
        val entry = repoList.selectedValue ?: return
        if (entry.missing) return
        val repo = RepoTab(entry.repo.path, entry.repo.name)
        mainView.addTab(repo)
        mainView.selectTab(repo)
    }

    private fun openSelectedInTerminal() {
        val entry = repoList.selectedValue ?: return
        if (entry.missing) return
        try {
            val pb = ProcessBuilder("wt", "-d", entry.repo.path)
                .apply { redirectErrorStream(true) }
            pb.start()
        } catch (_: Exception) {
            try { ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "cd /d \"${entry.repo.path}\"").start() }
            catch (_: Exception) {}
        }
    }

    // ── Custom list cell renderer ─────────────────────────────────────────────

    private inner class RepoListRenderer : ListCellRenderer<RepoEntry> {
        private val nameLabel     = JLabel()
        private val pathLabel     = JLabel()
        private val branchLabel   = JLabel()
        private val badgeLabel    = JLabel()
        private val warningLabel  = JLabel("Repository Moved or Deleted")
        private val icon          = FontIcon.of(MaterialDesign.MDI_FOLDER, 28, Color.GRAY)
        private val arrowIcon     = FontIcon.of(MaterialDesign.MDI_ARROW_RIGHT, 16, Color(0x4F_C3_F7))

        private val cell = JPanel(BorderLayout(10, 0)).apply {
            border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
        }
        private val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
        }
        private val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }

        init {
            nameLabel.font   = nameLabel.font.deriveFont(Font.BOLD, 13f)
            pathLabel.font   = pathLabel.font.deriveFont(11f)
            branchLabel.font = branchLabel.font.deriveFont(11f)
            badgeLabel.font  = badgeLabel.font.deriveFont(Font.BOLD, 10f)
            warningLabel.font = warningLabel.font.deriveFont(11f)
            warningLabel.foreground = Color(0xE5_73_73)

            topRow.add(nameLabel)
            topRow.add(branchLabel)
            topRow.add(badgeLabel)
            textPanel.add(topRow)
            textPanel.add(warningLabel)
            textPanel.add(pathLabel)

            cell.add(JLabel(icon),   BorderLayout.WEST)
            cell.add(textPanel,      BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out RepoEntry>, value: RepoEntry,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val bg = if (isSelected) list.selectionBackground else list.background
            cell.background = bg
            cell.opaqueComponents(bg)
            topRow.background = bg

            val accent = UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)
            nameLabel.text      = value.repo.name
            nameLabel.foreground = if (value.missing) Color.GRAY else list.foreground

            pathLabel.text      = value.repo.path
            pathLabel.foreground = Color.GRAY

            if (value.branch != null) {
                branchLabel.text = "  ${value.branch}  "
                branchLabel.foreground = accent
                branchLabel.border = BorderFactory.createLineBorder(accent, 1, true)
                branchLabel.isVisible = true
            } else {
                branchLabel.isVisible = false
            }

            if (value.modifiedCount != null && value.modifiedCount!! > 0) {
                badgeLabel.text = " ${value.modifiedCount} "
                badgeLabel.foreground = Color.WHITE
                badgeLabel.background = Color(0xE5_A0_00)
                badgeLabel.isOpaque = true
                badgeLabel.border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
                badgeLabel.isVisible = true
            } else {
                badgeLabel.isVisible = false
            }

            warningLabel.isVisible = value.missing

            val iconColor = if (value.missing) Color.GRAY
                else if (isSelected) accent else Color(0x78_90_AB)
            icon.iconColor = iconColor

            return cell
        }

        private fun JPanel.opaqueComponents(bg: Color) {
            background = bg
            isOpaque = true
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun switchTab(buttonOrCard: Any) {
        switchTo(when (buttonOrCard) {
            cloneBtn  -> CARD_CLONE
            addBtn    -> CARD_ADD
            createBtn -> CARD_CREATE
            is String -> buttonOrCard
            else      -> CARD_LOCAL
        })
    }

    fun browseForPath(owner: Component, onPicked: (File) -> Unit = {}) {
        val win = SwingUtilities.getWindowAncestor(owner)
        object : SwingWorker<File?, Void>() {
            override fun doInBackground() = NativeFileChooser.chooseDirectory(win, "Select Folder")
            override fun done() {
                val dir = try { get() } catch (_: Exception) { return } ?: return
                addTab.pathField.text    = dir.absolutePath
                createTab.pathField.text = dir.absolutePath
                onPicked(dir)
            }
        }.execute()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun switchTo(card: String) {
        cards.show(container, card)
        val isLocal  = card == CARD_LOCAL
        val isRemote = card == CARD_REMOTE
        localBtn.setNavSelected(isLocal)
        remoteBtn.setNavSelected(isRemote)
        listOf(cloneBtn, addBtn, createBtn).forEach { it.setNavSelected(false) }
        when (card) {
            CARD_CLONE  -> cloneBtn.setNavSelected(true)
            CARD_ADD    -> addBtn.setNavSelected(true)
            CARD_CREATE -> createBtn.setNavSelected(true)
        }
        if (card == CARD_LOCAL) loadRecentRepos()
    }

    // ── Nav buttons ───────────────────────────────────────────────────────────

    private inner class NavTabButton(label: String, ikon: Ikon) : JButton() {
        private val fontIcon = FontIcon.of(ikon, 22, unselectedColor())
        init {
            text = label; icon = fontIcon
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition   = SwingConstants.BOTTOM
            isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
            preferredSize = Dimension(80, 58); maximumSize = Dimension(80, 58)
            font = font.deriveFont(11f); foreground = unselectedColor()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        fun setNavSelected(sel: Boolean) {
            val color = if (sel) accentColor() else unselectedColor()
            fontIcon.iconColor = color; foreground = color
            putClientProperty("selected", sel); repaint()
        }
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (getClientProperty("selected") == true) {
                g.color = accentColor()
                g.fillRect(0, height - 3, width, 3)
            }
        }
        private fun accentColor()     = UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)
        private fun unselectedColor() = UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY
    }

    private inner class ActionNavButton(label: String, ikon: Ikon) : JButton() {
        private val fontIcon = FontIcon.of(ikon, 20, unselectedColor())
        init {
            text = label; icon = fontIcon
            horizontalTextPosition = SwingConstants.CENTER
            verticalTextPosition   = SwingConstants.BOTTOM
            isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
            preferredSize = Dimension(70, 58); maximumSize = Dimension(70, 58)
            font = font.deriveFont(11f); foreground = unselectedColor()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        fun setNavSelected(sel: Boolean) {
            val color = if (sel) accentColor() else unselectedColor()
            fontIcon.iconColor = color; foreground = color
            putClientProperty("selected", sel); repaint()
        }
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (getClientProperty("selected") == true) {
                g.color = accentColor()
                g.fillRect(0, height - 3, width, 3)
            }
        }
        private fun accentColor()     = UIManager.getColor("Component.accentColor") ?: Color(0x4F_C3_F7)
        private fun unselectedColor() = UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY
    }

    companion object {
        const val CARD_LOCAL  = "local"
        const val CARD_REMOTE = "remote"
        const val CARD_CLONE  = "clone"
        const val CARD_ADD    = "add"
        const val CARD_CREATE = "create"
    }
}

package com.gitnarwhal.views

import com.gitnarwhal.utils.NativeFileChooser
import com.gitnarwhal.utils.RecentReposService
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import com.gitnarwhal.utils.toPath
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Files
import javax.swing.*

class MainView : JPanel(BorderLayout()) {

    val tabPane = JTabbedPane()

    /**
     * Guard against the ChangeListener firing re-entrantly.
     *
     * When we insert a new tab at the position of the selected "+" tab,
     * JTabbedPane auto-increments selectedIndex back to "+", which would fire
     * the listener again and spawn a second AddCloneTab.
     */
    @Volatile private var suppressTabChange = false

    init {
        add(tabPane, BorderLayout.CENTER)

        //region restore last-open tabs
        try {
            val toRemove = arrayListOf<JSONObject>()
            for (tab in if (Settings.reopenTabs) Settings.openTabs.map { it as JSONObject } else emptyList()) {
                val path = tab.getString("path").toPath()
                val name = tab.getString("name")
                if (Files.isDirectory(path)) {
                    addTab(RepoTab(path.toAbsolutePath().toString(), name), save = false)
                } else {
                    toRemove.add(tab)
                }
            }
            Settings.openTabs.removeAll { toRemove.contains(it as JSONObject) }
        } catch (ignored: Exception) {
            println("Open tabs loading error: ${ignored.message}")
            Settings.openTabs.removeAll { true }
        }
        Settings.save()
        //endregion

        addPlusTab()

        if (tabPane.tabCount == 1) {
            // Only the "+" — open a fresh Add/Clone/Create tab on first run
            addNewCloneTab()
        }

        tabPane.selectedIndex = 0

        tabPane.addChangeListener {
            if (suppressTabChange) return@addChangeListener
            val idx = tabPane.selectedIndex
            if (idx >= 0 && tabPane.getTitleAt(idx) == PLUS_TITLE) {
                suppressTabChange = true
                SwingUtilities.invokeLater {
                    try { addNewCloneTab() }
                    finally { suppressTabChange = false }
                }
            }
        }
    }

    private fun addPlusTab() {
        tabPane.addTab(PLUS_TITLE, JPanel())
    }

    fun addTab(panel: JPanel, title: String) {
        val plusIdx = (0 until tabPane.tabCount).indexOfFirst { tabPane.getTitleAt(it) == PLUS_TITLE }
        val insertIdx = if (plusIdx < 0) tabPane.tabCount else plusIdx
        tabPane.insertTab(title, null, panel, null, insertIdx)
        tabPane.setTabComponentAt(insertIdx, CloseableTabHeader(title, panel))
        tabPane.selectedComponent = panel
    }

    /** Custom tab header with an X close button. */
    private inner class CloseableTabHeader(title: String, panel: JPanel) :
        JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

            add(JLabel(title).apply { border = BorderFactory.createEmptyBorder(0, 0, 0, 2) })

            add(JButton("×").apply {
                preferredSize     = Dimension(16, 16)
                isBorderPainted   = false
                isContentAreaFilled = false
                isFocusPainted    = false
                toolTipText       = "Close tab"
                font              = font.deriveFont(12f)
                foreground        = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                addActionListener { closeTab(panel) }
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseEntered(e: java.awt.event.MouseEvent) {
                        foreground = UIManager.getColor("Label.foreground") ?: Color.WHITE
                    }
                    override fun mouseExited(e: java.awt.event.MouseEvent) {
                        foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
                    }
                })
            })
        }
    }

    fun addTab(repo: RepoTab, save: Boolean = true) {
        addTab(repo, repo.tabTitle)
        RecentReposService.record(repo.tabTitle, repo.path)
        if (save) {
            Settings.openTabs.put(JSONObject().apply {
                put("name", repo.tabTitle)
                put("path", repo.path)
            })
            Settings.save()
        }
    }

    fun closeTab(component: JPanel) {
        val idx = tabPane.indexOfComponent(component)
        if (idx >= 0) {
            tabPane.removeTabAt(idx)
            if (component is RepoTab) {
                Settings.openTabs.removeAll { (it as JSONObject).getString("path") == component.path }
                Settings.save()
            }
            // If no real tabs remain, open a fresh Add/Clone tab
            val realCount = (0 until tabPane.tabCount).count { tabPane.getTitleAt(it) != PLUS_TITLE }
            if (realCount == 0) addNewCloneTab()
        }
    }

    fun selectTab(component: JPanel) {
        val idx = tabPane.indexOfComponent(component)
        if (idx >= 0) tabPane.selectedIndex = idx
    }

    /** Opens an Add/Clone/Create tab (triggered by the "+" sentinel or New Tab shortcut). */
    fun addNewCloneTab(): AddCloneTab {
        val newTab = AddCloneTab(this)
        addTab(newTab, newTab.tabTitle)
        return newTab
    }

    /**
     * Shows a native directory picker and opens the chosen repo as a RepoTab.
     * Called from File > Open Repository…
     */
    fun openRepositoryFromPicker() {
        val win = SwingUtilities.getWindowAncestor(this)
        val dir = NativeFileChooser.chooseDirectory(win, "Open Repository") ?: return
        val repo = RepoTab(dir.absolutePath, dir.name)
        addTab(repo)
        selectTab(repo)
    }

    companion object {
        const val PLUS_TITLE = "+"
    }
}

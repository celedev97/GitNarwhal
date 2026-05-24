package com.gitnarwhal.views

import com.gitnarwhal.utils.NativeFileChooser
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import com.gitnarwhal.utils.toPath
import org.json.JSONObject
import java.awt.BorderLayout
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
        // FlatLaf close buttons on every tab.
        // Callback type MUST be IntConsumer (not BiConsumer) — FlatLaf checks instanceof.
        tabPane.putClientProperty("JTabbedPane.tabsClosable", true)
        tabPane.putClientProperty(
            "JTabbedPane.tabCloseCallback",
            java.util.function.IntConsumer { idx ->
                if (tabPane.getTitleAt(idx) == PLUS_TITLE) return@IntConsumer
                val comp = tabPane.getComponentAt(idx) as? JPanel ?: return@IntConsumer
                closeTab(comp)
            }
        )

        add(tabPane, BorderLayout.CENTER)

        //region restore last-open tabs
        try {
            val toRemove = arrayListOf<JSONObject>()
            for (tab in Settings.openTabs.map { it as JSONObject }) {
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
        if (plusIdx < 0) {
            tabPane.addTab(title, panel)
        } else {
            tabPane.insertTab(title, null, panel, null, plusIdx)
        }
        tabPane.selectedComponent = panel
    }

    fun addTab(repo: RepoTab, save: Boolean = true) {
        addTab(repo, repo.tabTitle)
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

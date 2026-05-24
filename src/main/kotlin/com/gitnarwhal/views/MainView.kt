package com.gitnarwhal.views

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import com.gitnarwhal.utils.toPath
import org.json.JSONObject
import java.awt.BorderLayout
import java.nio.file.Files
import javax.swing.*
import javax.swing.event.ChangeListener

class MainView : JPanel(BorderLayout()) {

    val tabPane = JTabbedPane()

    init {
        add(tabPane, BorderLayout.CENTER)

        //region loading last open tabs
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
            println("Open tabs loading error")
            Settings.openTabs.removeAll { true }
        }
        Settings.save()
        //endregion

        addPlusTab()

        if (tabPane.tabCount == 1) {
            // only the "+" tab — open a fresh Add/Clone tab
            addNewCloneTab()
        }

        tabPane.selectedIndex = 0

        tabPane.addChangeListener(ChangeListener {
            val idx = tabPane.selectedIndex
            if (idx >= 0 && tabPane.getTitleAt(idx) == PLUS_TITLE) {
                SwingUtilities.invokeLater { addNewCloneTab() }
            }
        })
    }

    private fun addPlusTab() {
        // sentinel "+" tab as the last tab; selecting it spawns a new AddCloneTab
        tabPane.addTab(PLUS_TITLE, JPanel())
    }

    fun addTab(panel: JPanel, title: String) {
        val insertAt = (tabPane.tabCount - 1).coerceAtLeast(0)
        // if "+" tab not yet present (init time), append at end
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
        }
    }

    fun selectTab(component: JPanel) {
        val idx = tabPane.indexOfComponent(component)
        if (idx >= 0) tabPane.selectedIndex = idx
    }

    fun addNewCloneTab(): AddCloneTab {
        val newTab = AddCloneTab(this)
        addTab(newTab, newTab.tabTitle)
        return newTab
    }

    fun addNewOpenTab() {
        val newTab = addNewCloneTab()
        newTab.switchTab(newTab.activateAddTab)
    }

    companion object {
        const val PLUS_TITLE = "+"
    }
}

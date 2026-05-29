package com.gitnarwhal

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.ThemeService
import com.gitnarwhal.utils.UpdateService
import com.gitnarwhal.views.MainView
import com.gitnarwhal.views.SettingsDialog
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

fun main() {
    // FlatLaf must be installed before any Swing component is created.
    // Register the custom defaults source first so FlatLaf.properties is picked up.
    ThemeService.registerDefaultsSource()
    ThemeService.applyFromSettings()

    SwingUtilities.invokeLater { startApp() }
}

private fun startApp() {
    println("GitNarwhal ${UpdateService.currentVersion}")
    println("Git location = \"${Git.GIT}\"")

    val frame = JFrame("GitNarwhal")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.preferredSize = Dimension(1280, 800)

    val iconStream = ClassLoader.getSystemResourceAsStream("icon.png")
    if (iconStream != null) frame.iconImage = ImageIcon(iconStream.readAllBytes()).image

    val mainView = MainView()
    frame.contentPane = mainView
    frame.jMenuBar = buildMenuBar(frame, mainView)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    // Auto-refresh the current repo tab when the app regains focus
    frame.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
        override fun windowGainedFocus(e: java.awt.event.WindowEvent) {
            (mainView.tabPane.selectedComponent as? com.gitnarwhal.views.RepoTab)?.refresh()
        }
    })

    UpdateService.checkForUpdates(frame)
}

private fun buildMenuBar(frame: JFrame, mainView: MainView): JMenuBar {
    val cmd   = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    val shift = InputEvent.SHIFT_DOWN_MASK
    val alt   = InputEvent.ALT_DOWN_MASK

    fun item(label: String, key: Int = 0, mods: Int = 0, action: () -> Unit) =
        JMenuItem(label).apply {
            if (key != 0) accelerator = KeyStroke.getKeyStroke(key, mods)
            addActionListener { action() }
        }

    fun currentRepo() = mainView.tabPane.selectedComponent as? com.gitnarwhal.views.RepoTab

    // ── File ──────────────────────────────────────────────────────────────────
    val fileMenu = JMenu("File").apply {
        add(item("Clone / New…",        KeyEvent.VK_N, cmd)  { mainView.addNewCloneTab() })
        add(item("Open Repository…",    KeyEvent.VK_O, cmd)  { mainView.openRepositoryFromPicker() })
        addSeparator()
        add(item("Exit",                KeyEvent.VK_F4, alt) {
            frame.dispatchEvent(java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING))
        })
    }

    // ── View ──────────────────────────────────────────────────────────────────
    val viewMenu = JMenu("View").apply {
        add(item("Refresh",             KeyEvent.VK_F5, 0)         { currentRepo()?.refresh() })
        addSeparator()
        add(JMenuItem("Next Tab").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, cmd)
            addActionListener {
                val tp = mainView.tabPane
                if (tp.tabCount > 1) tp.selectedIndex = (tp.selectedIndex + 1) % tp.tabCount
            }
        })
        add(JMenuItem("Previous Tab").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, cmd or shift)
            addActionListener {
                val tp = mainView.tabPane
                if (tp.tabCount > 1) tp.selectedIndex = (tp.selectedIndex - 1 + tp.tabCount) % tp.tabCount
            }
        })
        addSeparator()
        add(item("File Status View",    KeyEvent.VK_1, cmd) { currentRepo()?.showFileStatusView() })
        add(item("History View",        KeyEvent.VK_2, cmd) { currentRepo()?.showHistoryView()    })
    }

    // ── Repository ────────────────────────────────────────────────────────────
    val repoMenu = JMenu("Repository").apply {
        add(item("Repository Settings…", KeyEvent.VK_COMMA, cmd or shift) { currentRepo()?.openSettings()  })
        add(item("View Repository Online")                                 { currentRepo()?.openRemote()    })
        add(item("Refresh Remote Status", KeyEvent.VK_R, shift or alt)    { currentRepo()?.fetch()         })
        addSeparator()
        add(item("Push…",    KeyEvent.VK_P, cmd or shift) { currentRepo()?.push()  })
        add(item("Pull…",    KeyEvent.VK_L, cmd or shift) { currentRepo()?.pull()  })
        add(item("Fetch…",   KeyEvent.VK_F, cmd or shift) { currentRepo()?.fetch() })
        addSeparator()
        add(item("Open in Terminal",  KeyEvent.VK_T, shift or alt) { currentRepo()?.openTerminal() })
        add(item("Show in Explorer")                                { currentRepo()?.openExplorer() })
        addSeparator()
        add(buildToolsMenu(mainView))
    }

    // ── Edit ──────────────────────────────────────────────────────────────────
    val editMenu = JMenu("Edit").apply {
        add(item("Settings…") { SettingsDialog(frame).isVisible = true })
    }

    // ── Help ──────────────────────────────────────────────────────────────────
    val helpMenu = JMenu("Help").apply {
        add(item("Check for Updates…") { UpdateService.checkForUpdatesManual(frame) })
        addSeparator()
        add(item("About GitNarwhal…") {
            javax.swing.JOptionPane.showMessageDialog(
                frame,
                "GitNarwhal\nVersion: ${UpdateService.currentVersion}\n\nhttps://github.com/celedev97/GitNarwhal",
                "About GitNarwhal",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        })
    }

    return JMenuBar().apply {
        add(fileMenu); add(viewMenu); add(repoMenu); add(editMenu); add(helpMenu)
    }
}

private fun buildToolsMenu(mainView: MainView): JMenu {
    fun currentRepo() = mainView.tabPane.selectedComponent as? com.gitnarwhal.views.RepoTab
    return JMenu("Tools").apply {
        addMenuListener(object : javax.swing.event.MenuListener {
            override fun menuSelected(e: javax.swing.event.MenuEvent) {
                removeAll()
                val arr = Settings.customActions
                if (arr.length() == 0) {
                    add(JMenuItem("(No custom actions defined)").apply { isEnabled = false })
                } else {
                    for (i in 0 until arr.length()) {
                        val obj  = arr.getJSONObject(i)
                        val name = obj.optString("name", "Action $i")
                        val cmd  = obj.optString("command", "")
                        val prms = obj.optString("params", "")
                        add(JMenuItem(name).apply {
                            addActionListener {
                                val repo = currentRepo() ?: return@addActionListener
                                val resolved = prms.replace("\$REPO", repo.path)
                                val parts = (listOf(cmd) + resolved.split(" ").filter { it.isNotBlank() })
                                Thread { Command(*parts.toTypedArray()).execute() }.start()
                            }
                        })
                    }
                }
            }
            override fun menuDeselected(e: javax.swing.event.MenuEvent) {}
            override fun menuCanceled(e: javax.swing.event.MenuEvent)   {}
        })
    }
}

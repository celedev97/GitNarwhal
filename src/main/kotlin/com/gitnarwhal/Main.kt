package com.gitnarwhal

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.ThemeService
import com.gitnarwhal.utils.UpdateService
import com.gitnarwhal.views.MainView
import com.gitnarwhal.views.SettingsDialog
import java.awt.Dimension
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

    UpdateService.checkForUpdates(frame)
}

private fun buildMenuBar(frame: JFrame, mainView: MainView): JMenuBar {
    val bar = JMenuBar()

    val fileMenu = JMenu("File")
    val newTab = JMenuItem("New Tab").apply {
        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_T, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        addActionListener { mainView.addNewCloneTab() }
    }
    val openRepo = JMenuItem("Open Repository…").apply {
        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        addActionListener { mainView.openRepositoryFromPicker() }
    }
    val quit = JMenuItem("Quit").apply {
        accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Q, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        addActionListener { frame.dispatchEvent(java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)) }
    }
    fileMenu.add(newTab); fileMenu.add(openRepo); fileMenu.addSeparator(); fileMenu.add(quit)

    val editMenu = JMenu("Edit")
    val settings = JMenuItem("Settings…").apply {
        addActionListener { SettingsDialog(frame).isVisible = true }
    }
    editMenu.add(settings)

    val helpMenu = JMenu("Help")
    val checkUpdates = JMenuItem("Check for Updates…").apply {
        addActionListener { UpdateService.checkForUpdatesManual(frame) }
    }
    val about = JMenuItem("About GitNarwhal…").apply {
        addActionListener {
            javax.swing.JOptionPane.showMessageDialog(
                frame,
                "GitNarwhal\nVersion: ${UpdateService.currentVersion}\n\nhttps://github.com/celedev97/GitNarwhal",
                "About GitNarwhal",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
    helpMenu.add(checkUpdates); helpMenu.addSeparator(); helpMenu.add(about)

    bar.add(fileMenu); bar.add(editMenu); bar.add(helpMenu)
    return bar
}

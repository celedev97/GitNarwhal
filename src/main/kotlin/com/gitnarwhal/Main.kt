package com.gitnarwhal

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.ThemeService
import com.gitnarwhal.views.MainView
import java.awt.Dimension
import java.util.jar.Manifest
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // FlatLaf must be installed before any Swing component is created.
    // Register the custom defaults source first so FlatLaf.properties is picked up.
    ThemeService.registerDefaultsSource()
    ThemeService.applyFromSettings()

    SwingUtilities.invokeLater { startApp() }
}

private fun startApp() {
    if (Settings.autoUpdate) {
        try {
            val manifestStream = ClassLoader.getSystemResourceAsStream("META-INF/MANIFEST.MF")
            if (manifestStream != null) {
                val attrs = Manifest(manifestStream).mainAttributes
                val version = attrs.getValue("Specification-Version")
                println("Running GitNarwhal v$version")
            }
        } catch (e: Exception) {
            // not packaged as jar — fine
        }
    }

    println("Git location = \"${Git.GIT}\"")

    val frame = JFrame("GitNarwhal")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.preferredSize = Dimension(1280, 800)

    val iconStream = ClassLoader.getSystemResourceAsStream("icon.png")
    if (iconStream != null) frame.iconImage = ImageIcon(iconStream.readAllBytes()).image

    frame.contentPane = MainView()
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true
}

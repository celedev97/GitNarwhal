package com.gitnarwhal.ui

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.ThemeService
import com.gitnarwhal.utils.save
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.MainView
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Smoke test that builds the real Swing UI, paints it once, and captures a
 * screenshot to build/screenshots/. Skipped on headless environments
 * (no display available — typically CI).
 *
 * The screenshot is a useful manual-debug artifact and a stepping stone for
 * future golden-image diffing.
 */
class MainViewSmokeTest {

    @Test
    fun `MainView builds, paints, and is screenshotted`() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
            "Headless environment — skipping headed smoke test")

        // Isolate from the dev machine's persisted open tabs so the test
        // doesn't try to open arbitrary repos.
        Settings.openTabs = JSONArray()
        Settings.save()

        ThemeService.registerDefaultsSource()
        ThemeService.applyFromSettings()

        var frame: JFrame? = null
        var view: MainView? = null

        SwingUtilities.invokeAndWait {
            view = MainView()
            frame = JFrame("GitNarwhal — smoke test").apply {
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                contentPane = view
                size = Dimension(1280, 800)
                setLocationRelativeTo(null)
                // Force the test frame to the very top so the screenshot
                // doesn't accidentally capture whatever is behind it.
                isAlwaysOnTop = true
                isVisible = true
                toFront()
                requestFocus()
            }
        }

        // Give the EDT a cycle, then a real wall-clock pause so the WM
        // actually composes the window on top before Robot fires.
        SwingUtilities.invokeAndWait { /* flush */ }
        Thread.sleep(1500)

        try {
            val outDir = File("build/screenshots").apply { mkdirs() }
            val outFile = File(outDir, "main-view.png")
            val bounds = frame!!.bounds
            val img = Robot().createScreenCapture(bounds)
            ImageIO.write(img, "png", outFile)

            assertTrue(outFile.exists() && outFile.length() > 0,
                "Screenshot was not produced at ${outFile.absolutePath}")
            assertNotNull(view, "MainView was not built")
            // Should have at least one tab (the "+" sentinel plus the auto-created AddCloneTab)
            assertTrue(view!!.tabPane.tabCount >= 1, "Tabs were not populated")
        } finally {
            SwingUtilities.invokeAndWait { frame?.dispose() }
        }
    }

    @Test
    fun `AddCloneTab card switching works`() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
            "Headless environment — skipping headed smoke test")

        Settings.openTabs = JSONArray()
        Settings.save()

        var view: MainView? = null
        SwingUtilities.invokeAndWait {
            view = MainView()
        }
        // Find the AddCloneTab the MainView opened on startup
        val tab = (0 until view!!.tabPane.tabCount)
            .map { view!!.tabPane.getComponentAt(it) }
            .filterIsInstance<AddCloneTab>()
            .firstOrNull()
        assertNotNull(tab, "MainView should auto-open one AddCloneTab on first run")

        // Just exercise the switch methods — no exceptions = pass
        SwingUtilities.invokeAndWait {
            tab!!.switchTab(tab.activateAddTab)
            tab.switchTab(tab.activateCreateTab)
            tab.switchTab(tab.activateCloneTab)
        }
    }
}

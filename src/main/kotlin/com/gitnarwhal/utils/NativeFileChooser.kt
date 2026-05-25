package com.gitnarwhal.utils

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser

/**
 * Cross-platform native folder picker.
 *
 *  - macOS   → AWT FileDialog (native Finder sheet)
 *  - Windows → PowerShell FolderBrowserDialog subprocess (true Win32 dialog)
 *  - Linux   → JFileChooser fallback
 */
object NativeFileChooser {

    fun chooseDirectory(parent: Window?, title: String = "Select Folder"): File? {
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("mac") -> chooseMac(parent, title)
            os.contains("win") -> chooseWindows(title) ?: chooseSwing(parent, title)
            else               -> chooseSwing(parent, title)
        }
    }

    // ── macOS: native Finder sheet ────────────────────────────────────────────

    private fun chooseMac(parent: Window?, title: String): File? {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val fd = FileDialog(parent as? Frame, title, FileDialog.LOAD)
        fd.isVisible = true
        System.clearProperty("apple.awt.fileDialogForDirectories")
        return if (fd.file != null) File(fd.directory, fd.file) else null
    }

    // ── Windows: PowerShell FolderBrowserDialog ───────────────────────────────
    //
    // Spawns a hidden PowerShell process that shows the native Vista-style
    // folder picker (FolderBrowserDialog with UseDescriptionForTitle=true on
    // Win10+). Reads the selected path from stdout.
    // No JNA structs, no COM, no AWT FileDialog quirks.

    private fun chooseWindows(title: String): File? = runCatching {
        // Use OpenFileDialog as a folder picker — gives the modern Explorer-style
        // dialog. ValidateNames=false + CheckFileExists=false lets the user
        // "open" a folder by typing its name; the real selection is the directory
        // part of the returned path.
        val safeTitle = title.replace("'", "''")
        val script = """
            Add-Type -AssemblyName System.Windows.Forms
            ${'$'}d = New-Object System.Windows.Forms.OpenFileDialog
            ${'$'}d.Title = '$safeTitle'
            ${'$'}d.ValidateNames = ${'$'}false
            ${'$'}d.CheckFileExists = ${'$'}false
            ${'$'}d.CheckPathExists = ${'$'}true
            ${'$'}d.FileName = 'Folder Selection.'
            if (${'$'}d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
                Write-Output ([System.IO.Path]::GetDirectoryName(${'$'}d.FileName))
            }
        """.trimIndent()

        val proc = ProcessBuilder(
            "powershell.exe",
            "-NonInteractive",
            "-WindowStyle", "Hidden",
            "-Command", script
        )
            .redirectErrorStream(false)
            .start()

        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()

        if (output.isNotBlank()) File(output) else null
    }.getOrElse {
        System.err.println("NativeFileChooser Windows failed: ${it.message}")
        null
    }

    // ── Fallback: Swing JFileChooser ──────────────────────────────────────────

    private fun chooseSwing(parent: Window?, title: String): File? {
        val chooser = JFileChooser().apply {
            fileSelectionMode         = JFileChooser.DIRECTORIES_ONLY
            dialogTitle               = title
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
            chooser.selectedFile else null
    }
}

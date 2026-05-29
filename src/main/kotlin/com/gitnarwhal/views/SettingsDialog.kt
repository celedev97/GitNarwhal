package com.gitnarwhal.views

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.Theme
import com.gitnarwhal.utils.ThemeService
import com.gitnarwhal.utils.UpdateService
import org.json.JSONArray
import org.json.JSONObject
import java.awt.*
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.*
import javax.swing.table.AbstractTableModel

class SettingsDialog(owner: Window?) : JDialog(owner, "Settings", ModalityType.APPLICATION_MODAL) {

    // ── General ───────────────────────────────────────────────────────────────
    private val themeCombo       = JComboBox<Theme>()
    private val fullNameField    = JTextField()
    private val emailField       = JTextField()
    private val reopenTabsCk     = JCheckBox("Re-open repository tabs at startup")
    private val cloneFolderField = JTextField()
    private val terminalOptions: List<Pair<String, String>> = when (com.gitnarwhal.utils.OS.CURRENT) {
        com.gitnarwhal.utils.OS.WINDOWS -> listOf(
            "Auto-detect"        to "auto",
            "Git Bash"           to "gitbash",
            "PowerShell (pwsh)"  to "pwsh",
            "Windows PowerShell" to "powershell",
            "Windows Terminal"   to "wt",
            "Command Prompt"     to "cmd",
            "Custom…"            to "custom"
        )
        com.gitnarwhal.utils.OS.MAC -> listOf(
            "Auto-detect"  to "auto",
            "Terminal.app" to "terminal",
            "iTerm2"       to "iterm2",
            "Custom…"      to "custom"
        )
        else -> listOf(
            "Auto-detect"     to "auto",
            "GNOME Terminal"  to "gnome-terminal",
            "Konsole"         to "konsole",
            "xterm"           to "xterm",
            "Custom…"         to "custom"
        )
    }
    private val terminalPresetCombo = JComboBox<String>(terminalOptions.map { it.first }.toTypedArray())
    private val terminalField       = JTextField()
    private lateinit var terminalCustomRow: JPanel
    private val ideField            = JTextField()

    // ── Updates ───────────────────────────────────────────────────────────────
    private val autoUpdateCk = JCheckBox("Automatically notify me of available updates")

    // ── Diff ──────────────────────────────────────────────────────────────────
    private var diffFontFamily     = Settings.diffFontFamily
    private var diffFontSize       = Settings.diffFontSize
    private val diffFontLabel      = JLabel()
    private val ignoreWsCk         = JCheckBox("Ignore whitespace")
    private val ignorePatternsArea = JTextArea(3, 40).apply { lineWrap = true }

    // ── Git ───────────────────────────────────────────────────────────────────
    private val gitignoreField    = JTextField()
    private val pullBehaviorCombo = JComboBox<String>(arrayOf("Merge", "Rebase"))
    private val pruneCk           = JCheckBox("Prune tracking branches no longer present on remote(s)")
    private val autocrlfCombo     = JComboBox<String>(arrayOf("true", "false", "input"))
    private val enableForcePushCk = JCheckBox("Enable Force Push")
    private val safeForcePushCk   = JCheckBox("   Use Safe Force Push (--force-with-lease)")
    private val gitPathField      = JTextField()

    // ── Custom Actions ────────────────────────────────────────────────────────
    private val actions      = mutableListOf<CustomAction>()
    private val actionsModel = ActionsTableModel()
    private val actionsTable = JTable(actionsModel)

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        layout = BorderLayout()

        val tabs = JTabbedPane()
        tabs.addTab("General",        buildGeneralTab())
        tabs.addTab("Updates",        buildUpdatesTab())
        tabs.addTab("Diff",           buildDiffTab())
        tabs.addTab("Git",            buildGitTab())
        tabs.addTab("Custom Actions", buildCustomActionsTab())

        add(tabs, BorderLayout.CENTER)
        add(buildButtonBar(), BorderLayout.SOUTH)

        pack()
        minimumSize = Dimension(580, 520)
        setLocationRelativeTo(owner)

        // Load fast (Settings-only) values immediately, then fetch git global config in background
        loadSettingsValues()
        loadGitConfigAsync()
    }

    // ── Tab: General ──────────────────────────────────────────────────────────

    private fun buildGeneralTab(): JPanel {
        val p = tabPanel()

        p.add(section("Appearance",
            formRow("Theme:", themeCombo)
        ))
        p.add(vgap(6))
        p.add(section("Default User Information",
            formRow("Full Name:", fullNameField),
            formRow("Email:",     emailField)
        ))
        p.add(vgap(6))

        val cloneRow = JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(cloneFolderField, BorderLayout.CENTER)
            add(JButton("…").apply {
                preferredSize = Dimension(28, cloneFolderField.preferredSize.height)
                addActionListener {
                    val fc = JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        if (cloneFolderField.text.isNotBlank()) currentDirectory = File(cloneFolderField.text)
                    }
                    if (fc.showOpenDialog(this@SettingsDialog) == JFileChooser.APPROVE_OPTION)
                        cloneFolderField.text = fc.selectedFile.absolutePath
                }
            }, BorderLayout.EAST)
        }
        p.add(section("Startup",
            JPanel(BorderLayout()).apply { isOpaque = false; add(reopenTabsCk) },
            formRow("Default clone folder:", cloneRow),
            formRow("Terminal:", terminalPresetCombo),
            run {
                terminalCustomRow = formRow("Custom command:", terminalField).also {
                    terminalField.toolTipText = "Use \$REPO as a placeholder for the repo path."
                }
                terminalPresetCombo.addActionListener {
                    val isCustom = terminalOptions.getOrNull(terminalPresetCombo.selectedIndex)?.second == "custom"
                    terminalCustomRow.isVisible = isCustom
                    terminalCustomRow.parent?.revalidate()
                    terminalCustomRow.parent?.repaint()
                }
                terminalCustomRow
            },
            formRow("IDE command:", ideField).also {
                ideField.toolTipText = "Command to open the repo in an IDE. Use \$REPO as placeholder. Leave blank to auto-detect VS Code."
            }
        ))
        p.add(Box.createVerticalGlue())
        return p
    }

    // ── Tab: Updates ─────────────────────────────────────────────────────────

    private fun buildUpdatesTab(): JPanel {
        val p = tabPanel()
        p.add(JLabel("<html>GitNarwhal will automatically check for updates,<br>but you may also check manually.</html>").apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border     = BorderFactory.createEmptyBorder(8, 0, 16, 0)
        })
        p.add(JSeparator().also { it.alignmentX = Component.LEFT_ALIGNMENT; it.maximumSize = Dimension(Int.MAX_VALUE, 2) })
        p.add(vgap(12))
        p.add(JLabel("Version: ${UpdateService.currentVersion}").also { it.alignmentX = Component.LEFT_ALIGNMENT })
        p.add(vgap(8))
        p.add(JButton("Check For Updates").also { btn ->
            btn.alignmentX = Component.LEFT_ALIGNMENT
            btn.addActionListener { UpdateService.checkForUpdatesManual(owner) }
        })
        p.add(vgap(16))
        p.add(JPanel(BorderLayout()).apply { isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT; add(autoUpdateCk) })
        p.add(Box.createVerticalGlue())
        return p
    }

    // ── Tab: Diff ─────────────────────────────────────────────────────────────

    private fun buildDiffTab(): JPanel {
        val p = tabPanel()
        val fontRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(diffFontLabel, BorderLayout.CENTER)
            add(JButton("Change Font…").apply { addActionListener { showFontChooser() } }, BorderLayout.EAST)
        }
        p.add(section("Internal Diff View",
            formRow("Diff View Font:", fontRow),
            vgap(4),
            JPanel(BorderLayout()).apply { isOpaque = false; add(ignoreWsCk) },
            vgap(8),
            JLabel("Ignore File Patterns:").also { it.alignmentX = Component.LEFT_ALIGNMENT },
            vgap(2),
            JScrollPane(ignorePatternsArea).also { it.alignmentX = Component.LEFT_ALIGNMENT; it.maximumSize = Dimension(Int.MAX_VALUE, 80) },
            JLabel("""<html><small>Use "," or ";" to separate rules. Use "*" as wildcard.</small></html>""").also { it.alignmentX = Component.LEFT_ALIGNMENT }
        ))
        p.add(Box.createVerticalGlue())
        return p
    }

    // ── Tab: Git ─────────────────────────────────────────────────────────────

    private fun buildGitTab(): JPanel {
        val p = tabPanel()

        val gitignoreRow = JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(gitignoreField, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(JButton("…").apply {
                    preferredSize = Dimension(28, gitignoreField.preferredSize.height)
                    addActionListener {
                        val fc = JFileChooser().apply {
                            if (gitignoreField.text.isNotBlank()) currentDirectory = File(gitignoreField.text).parentFile
                        }
                        if (fc.showOpenDialog(this@SettingsDialog) == JFileChooser.APPROVE_OPTION)
                            gitignoreField.text = fc.selectedFile.absolutePath
                    }
                })
                add(JButton("Edit File").apply {
                    addActionListener {
                        val path = gitignoreField.text.trim()
                        if (path.isNotBlank()) {
                            val file = File(path)
                            if (!file.exists()) file.createNewFile()
                            try { Desktop.getDesktop().edit(file) }
                            catch (_: Exception) { Desktop.getDesktop().open(file) }
                        }
                    }
                })
            }, BorderLayout.EAST)
        }

        enableForcePushCk.addItemListener { e ->
            safeForcePushCk.isEnabled = e.stateChange == ItemEvent.SELECTED
        }

        val gitPathRow = JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(gitPathField, BorderLayout.CENTER)
            add(JButton("…").apply {
                preferredSize = Dimension(28, gitPathField.preferredSize.height)
                addActionListener {
                    val fc = JFileChooser()
                    if (fc.showOpenDialog(this@SettingsDialog) == JFileChooser.APPROVE_OPTION)
                        gitPathField.text = fc.selectedFile.absolutePath
                }
            }, BorderLayout.EAST)
        }

        p.add(section("Global Config",
            formRow("Global Ignore List:", gitignoreRow),
            formRow("Default pull behavior:", pullBehaviorCombo),
            JPanel(BorderLayout()).apply { isOpaque = false; add(pruneCk) },
            formRow("Line ending (core.autocrlf):", autocrlfCombo)
        ))
        p.add(vgap(6))
        p.add(section("Push",
            JPanel(BorderLayout()).apply { isOpaque = false; add(enableForcePushCk) },
            JPanel(BorderLayout()).apply { isOpaque = false; add(safeForcePushCk) }
        ))
        p.add(vgap(6))
        p.add(section("Git Version",
            formRow("Git executable:", gitPathRow),
            JLabel("<html><small>Currently using: ${Git.GIT}</small></html>").also {
                it.alignmentX = Component.LEFT_ALIGNMENT
                it.foreground = UIManager.getColor("Label.disabledForeground")
            },
            JLabel("<html><small>(Changes require restart)</small></html>").also {
                it.alignmentX = Component.LEFT_ALIGNMENT
                it.foreground = UIManager.getColor("Label.disabledForeground")
            }
        ))
        p.add(Box.createVerticalGlue())
        return p
    }

    // ── Tab: Custom Actions ───────────────────────────────────────────────────

    private fun buildCustomActionsTab(): JPanel {
        val p = JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        actionsTable.rowHeight     = 24
        actionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        val addBtn    = JButton("Add").apply    { addActionListener { onAddAction()    } }
        val editBtn   = JButton("Edit").apply   { addActionListener { onEditAction()   } }
        val deleteBtn = JButton("Delete").apply { addActionListener { onDeleteAction() } }
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false; add(addBtn); add(editBtn); add(deleteBtn)
        }

        p.add(JScrollPane(actionsTable), BorderLayout.CENTER)
        p.add(btnRow, BorderLayout.SOUTH)
        return p
    }

    // ── Load / Apply ──────────────────────────────────────────────────────────

    /** Fast: reads from in-memory Settings JSONObject only — safe to call on EDT. */
    private fun loadSettingsValues() {
        ThemeService.listThemes().let { themes ->
            themeCombo.removeAllItems()
            themes.forEach { themeCombo.addItem(it) }
            val cur = Settings.theme
            themes.indexOfFirst { it.path == cur }.takeIf { it >= 0 }?.let { themeCombo.selectedIndex = it }
        }
        reopenTabsCk.isSelected   = Settings.reopenTabs
        cloneFolderField.text     = Settings.defaultCloneFolder
        val presetKey = Settings.terminalPreset
        val presetIdx = terminalOptions.indexOfFirst { it.second == presetKey }.takeIf { it >= 0 } ?: 0
        terminalPresetCombo.selectedIndex = presetIdx
        terminalField.text        = Settings.terminalCommand
        terminalCustomRow.isVisible = terminalOptions.getOrNull(presetIdx)?.second == "custom"
        ideField.text             = Settings.ideCommand

        autoUpdateCk.isSelected = Settings.autoUpdate

        diffFontFamily = Settings.diffFontFamily
        diffFontSize   = Settings.diffFontSize
        updateDiffFontLabel()
        ignoreWsCk.isSelected   = Settings.diffIgnoreWhitespace
        ignorePatternsArea.text = Settings.diffIgnorePatterns

        enableForcePushCk.isSelected = Settings.enableForcePush
        safeForcePushCk.isEnabled    = Settings.enableForcePush
        safeForcePushCk.isSelected   = Settings.safeForcePush
        gitPathField.text            = Settings.gitPath.ifBlank { Git.GIT }

        actions.clear()
        val arr = Settings.customActions
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            actions.add(CustomAction(o.optString("name"), o.optString("command"), o.optString("params"), o.optString("shortcut")))
        }
        actionsModel.fireTableDataChanged()
    }

    /** Slow: spawns git subprocesses — runs in background, populates fields when done. */
    private fun loadGitConfigAsync() {
        object : javax.swing.SwingWorker<Array<String>, Void>() {
            override fun doInBackground() = arrayOf(
                Git.globalGet("user.name"),
                Git.globalGet("user.email"),
                Git.globalGet("core.excludesfile"),
                Git.globalGet("pull.rebase"),
                Git.globalGet("fetch.prune"),
                Git.globalGet("core.autocrlf")
            )
            override fun done() {
                val v = try { get() } catch (_: Exception) { return }
                fullNameField.text             = v[0]
                emailField.text               = v[1]
                gitignoreField.text           = v[2]
                pullBehaviorCombo.selectedItem = if (v[3] == "true") "Rebase" else "Merge"
                pruneCk.isSelected            = v[4] == "true"
                autocrlfCombo.selectedItem    = v[5].ifBlank { "false" }
            }
        }.execute()
    }

    private fun applyChanges() {
        val selTheme = themeCombo.selectedItem as? Theme
        if (selTheme != null && selTheme.path != Settings.theme) {
            ThemeService.setAndApply(selTheme.path)
            Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
        }

        val name = fullNameField.text.trim()
        val email = emailField.text.trim()
        if (name.isNotBlank())  Git.globalSet("user.name",  name)
        if (email.isNotBlank()) Git.globalSet("user.email", email)
        Settings.reopenTabs         = reopenTabsCk.isSelected
        Settings.defaultCloneFolder = cloneFolderField.text.trim()
        Settings.terminalPreset     = terminalOptions.getOrNull(terminalPresetCombo.selectedIndex)?.second ?: "auto"
        Settings.terminalCommand    = terminalField.text.trim()
        Settings.ideCommand         = ideField.text.trim()

        Settings.autoUpdate = autoUpdateCk.isSelected

        Settings.diffFontFamily       = diffFontFamily
        Settings.diffFontSize         = diffFontSize
        Settings.diffIgnoreWhitespace = ignoreWsCk.isSelected
        Settings.diffIgnorePatterns   = ignorePatternsArea.text.trim()

        val gi = gitignoreField.text.trim()
        if (gi.isNotBlank()) Git.globalSet("core.excludesfile", gi) else Git.globalUnset("core.excludesfile")
        Git.globalSet("pull.rebase",   if (pullBehaviorCombo.selectedItem == "Rebase") "true" else "false")
        Git.globalSet("fetch.prune",   if (pruneCk.isSelected) "true" else "false")
        Git.globalSet("core.autocrlf", autocrlfCombo.selectedItem as String)
        Settings.enableForcePush = enableForcePushCk.isSelected
        Settings.safeForcePush   = safeForcePushCk.isSelected
        val customGit = gitPathField.text.trim()
        Settings.gitPath = if (customGit == Git.GIT) "" else customGit

        val newArr = JSONArray()
        actions.forEach { a ->
            newArr.put(JSONObject().apply {
                put("name", a.name); put("command", a.command)
                put("params", a.params); put("shortcut", a.shortcut)
            })
        }
        Settings.customActions = newArr
    }

    // ── Font chooser ──────────────────────────────────────────────────────────

    private fun showFontChooser() {
        val families    = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
        val familyCombo = JComboBox(families).apply { selectedItem = diffFontFamily }
        val sizeSpinner = JSpinner(SpinnerNumberModel(diffFontSize, 8, 32, 1))
        val panel = JPanel(GridLayout(2, 2, 8, 6)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(JLabel("Font Family:")); add(familyCombo)
            add(JLabel("Size (pt):"));  add(sizeSpinner)
        }
        if (JOptionPane.showConfirmDialog(this, panel, "Diff Font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            diffFontFamily = familyCombo.selectedItem as String
            diffFontSize   = sizeSpinner.value as Int
            updateDiffFontLabel()
        }
    }

    private fun updateDiffFontLabel() { diffFontLabel.text = "$diffFontFamily, ${diffFontSize}pt" }

    // ── Custom Actions CRUD ───────────────────────────────────────────────────

    private fun onAddAction()    { val r = showActionDialog(null)           ?: return; actions.add(r);     actionsModel.fireTableDataChanged() }
    private fun onEditAction()   { val row = actionsTable.selectedRow.takeIf { it >= 0 } ?: return; val r = showActionDialog(actions[row]) ?: return; actions[row] = r; actionsModel.fireTableDataChanged() }
    private fun onDeleteAction() { val row = actionsTable.selectedRow.takeIf { it >= 0 } ?: return; actions.removeAt(row); actionsModel.fireTableDataChanged() }

    private fun showActionDialog(existing: CustomAction?): CustomAction? {
        val nameField    = JTextField(existing?.name    ?: "")
        val commandField = JTextField(existing?.command ?: "")
        val paramsField  = JTextField(existing?.params  ?: "")
        val panel = JPanel(GridLayout(3, 2, 8, 6)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 4, 8)
            add(JLabel("Name:"));       add(nameField)
            add(JLabel("Command:"));    add(commandField)
            add(JLabel("Parameters:")); add(paramsField)
        }
        val hint  = JLabel("<html><small>Use \$REPO as a placeholder for the repository path.</small></html>").apply { border = BorderFactory.createEmptyBorder(0, 8, 8, 8) }
        val outer = JPanel(BorderLayout(0, 4)).apply { add(panel, BorderLayout.CENTER); add(hint, BorderLayout.SOUTH) }
        val r = JOptionPane.showConfirmDialog(this, outer, if (existing == null) "Add Action" else "Edit Action", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (r != JOptionPane.OK_OPTION) return null
        val name = nameField.text.trim(); val cmd = commandField.text.trim()
        if (name.isBlank() || cmd.isBlank()) return null
        return CustomAction(name, cmd, paramsField.text.trim())
    }

    // ── Button bar ────────────────────────────────────────────────────────────

    private fun buildButtonBar(): JPanel {
        val bar   = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8))
        val okBtn = JButton("OK").apply { addActionListener { applyChanges(); dispose() } }
        bar.add(JButton("Apply").apply  { addActionListener { applyChanges() } })
        bar.add(JButton("Cancel").apply { addActionListener { dispose() } })
        bar.add(okBtn)
        // defaultButton must be set after the dialog is in the Swing hierarchy (post-pack)
        SwingUtilities.invokeLater { getRootPane()?.defaultButton = okBtn }
        return bar
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private fun tabPanel() = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
    }

    private fun section(title: String, vararg rows: JComponent): JPanel =
        JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)
            )
            rows.forEach { r -> r.alignmentX = Component.LEFT_ALIGNMENT; add(r); add(Box.createVerticalStrut(3)) }
        }

    private fun formRow(label: String, comp: JComponent): JPanel =
        JPanel(BorderLayout(8, 0)).apply {
            isOpaque    = false
            maximumSize = Dimension(Int.MAX_VALUE, comp.preferredSize.height.coerceAtLeast(26))
            add(JLabel(label).apply { preferredSize = Dimension(170, 24) }, BorderLayout.WEST)
            add(comp, BorderLayout.CENTER)
        }

    private fun vgap(h: Int): JComponent =
        Box.createVerticalStrut(h).also { (it as JComponent).alignmentX = Component.LEFT_ALIGNMENT } as JComponent

    // ── Inner types ───────────────────────────────────────────────────────────

    private data class CustomAction(var name: String, var command: String, var params: String, var shortcut: String = "")

    private inner class ActionsTableModel : AbstractTableModel() {
        private val cols = arrayOf("Name", "Command", "Parameters", "Shortcut")
        override fun getRowCount()            = actions.size
        override fun getColumnCount()         = 4
        override fun getColumnName(col: Int)  = cols[col]
        override fun isCellEditable(r: Int, c: Int) = false
        override fun getValueAt(r: Int, c: Int): Any = when (c) {
            0 -> actions[r].name; 1 -> actions[r].command
            2 -> actions[r].params; else -> actions[r].shortcut
        }
    }
}

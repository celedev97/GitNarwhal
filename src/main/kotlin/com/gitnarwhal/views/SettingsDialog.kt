package com.gitnarwhal.views

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.Theme
import com.gitnarwhal.utils.ThemeService
import com.gitnarwhal.utils.save
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.*

class SettingsDialog(owner: Window?) : JDialog(owner, "Settings", ModalityType.APPLICATION_MODAL) {

    private val themeCombo = JComboBox<Theme>()
    private val autoUpdateCheck = JCheckBox("Check for updates on startup")

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        layout = BorderLayout()

        add(buildForm(), BorderLayout.CENTER)
        add(buildButtonBar(), BorderLayout.SOUTH)

        loadValues()

        pack()
        minimumSize = Dimension(420, size.height)
        setLocationRelativeTo(owner)
    }

    private fun buildForm(): JPanel {
        val form = JPanel(GridBagLayout())
        form.border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        form.add(JLabel("Theme:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        form.add(themeCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2
        form.add(autoUpdateCheck, gbc)

        return form
    }

    private fun buildButtonBar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8))
        val apply  = JButton("Apply").apply { addActionListener { applyChanges() } }
        val ok     = JButton("OK").apply    { addActionListener { applyChanges(); dispose() } }
        val cancel = JButton("Cancel").apply{ addActionListener { dispose() } }
        bar.add(apply); bar.add(cancel); bar.add(ok)
        getRootPane().defaultButton = ok
        return bar
    }

    private fun loadValues() {
        themeCombo.removeAllItems()
        val themes = ThemeService.listThemes()
        themes.forEach { themeCombo.addItem(it) }
        val currentPath = Settings.theme
        themes.indexOfFirst { it.path == currentPath }.takeIf { it >= 0 }?.let {
            themeCombo.selectedIndex = it
        }

        autoUpdateCheck.isSelected = Settings.autoUpdate
    }

    private fun applyChanges() {
        val selected = themeCombo.selectedItem as? Theme
        if (selected != null && selected.path != Settings.theme) {
            ThemeService.setAndApply(selected.path)
            // walk every open window so live components re-skin
            Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
        }
        if (autoUpdateCheck.isSelected != Settings.autoUpdate) {
            Settings.autoUpdate = autoUpdateCheck.isSelected
            Settings.save()
        }
    }
}

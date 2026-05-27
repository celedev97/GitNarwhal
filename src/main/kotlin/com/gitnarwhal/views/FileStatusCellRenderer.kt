package com.gitnarwhal.views

import java.awt.Color
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList

/** Renders a "X path" string (status char at index 0, space, then path) with a coloured status letter. */
internal class FileStatusCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>, value: Any?, index: Int,
        isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        val line = value?.toString() ?: ""
        if (line.length < 2) return label
        val (color, letter) = colorFor(line[0])
        val path = line.substring(2)
        label.text = "<html><font color='${colorHex(color)}'><b>$letter</b></font>&nbsp;&nbsp;$path</html>"
        return label
    }

    companion object {
        fun colorFor(c: Char): Pair<Color, String> = when (c) {
            'A'  -> Color(0x81, 0xC7, 0x84) to "A"
            'D'  -> Color(0xE5, 0x73, 0x73) to "D"
            'R'  -> Color(0x4F, 0xC3, 0xF7) to "R"
            'C'  -> Color(0xFF, 0xB7, 0x4D) to "C"
            '?'  -> Color(0xA0, 0xA0, 0xA0) to "?"
            else -> Color(0xFF, 0xB7, 0x4D) to "$c"
        }

        fun colorHex(c: Color) = "#%02X%02X%02X".format(c.red, c.green, c.blue)
    }
}

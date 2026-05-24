package com.gitnarwhal.views

import com.gitnarwhal.components.AddCloneTab.AddTab
import com.gitnarwhal.components.AddCloneTab.CloneTab
import com.gitnarwhal.components.AddCloneTab.CreateTab
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class AddCloneTab(val mainView: MainView) : JPanel(BorderLayout()) {

    val tabTitle: String = "New Tab"

    private val cards = CardLayout()
    private val container = JPanel(cards)

    val cloneTab  = CloneTab(this)
    val addTab    = AddTab(this)
    val createTab = CreateTab(this)

    val activateCloneTab  = JButton("Clone")
    val activateAddTab    = JButton("Add")
    val activateCreateTab = JButton("Create")

    init {
        container.add(cloneTab,  CARD_CLONE)
        container.add(addTab,    CARD_ADD)
        container.add(createTab, CARD_CREATE)

        val header = JPanel(FlowLayout(FlowLayout.CENTER, 8, 8))
        header.add(activateCloneTab)
        header.add(activateAddTab)
        header.add(activateCreateTab)

        activateCloneTab.addActionListener  { switchTab(CARD_CLONE) }
        activateAddTab.addActionListener    { switchTab(CARD_ADD) }
        activateCreateTab.addActionListener { switchTab(CARD_CREATE) }

        add(header, BorderLayout.NORTH)
        add(container, BorderLayout.CENTER)

        switchTab(CARD_CLONE)
    }

    fun switchTab(buttonOrCard: Any) {
        val card = when (buttonOrCard) {
            activateCloneTab  -> CARD_CLONE
            activateAddTab    -> CARD_ADD
            activateCreateTab -> CARD_CREATE
            is String         -> buttonOrCard
            else              -> CARD_CLONE
        }
        cards.show(container, card)
    }

    companion object {
        const val CARD_CLONE  = "clone"
        const val CARD_ADD    = "add"
        const val CARD_CREATE = "create"
    }
}

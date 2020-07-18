package com.gitnarwhal.views

import com.gitnarwhal.components.AddCloneTab.*
import javafx.event.ActionEvent
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Tab
import tornadofx.*

class AddCloneTab(val mainView: MainView): View() {
    override val root:Parent by fxml(null as String?, true)

    private val container by fxid<Parent>();

    val tab by lazy{
        val tab = Tab("New Tab")
        tab.content = root
        tab
    }

    //region "Tab" buttons and contents
    val activateCloneTab by fxid<Button>()
    val activateAddTab by fxid<Button>()
    val activateCreateTab by fxid<Button>()

    private val tabsMap = hashMapOf(
            activateCloneTab to CloneTab(this).root,
            activateAddTab to AddTab(this).root,
            activateCreateTab to CreateTab(this).root
    )
    //endregion

    init {
        switchTab(activateCloneTab)
    }


    fun switchTab(event: ActionEvent) = switchTab(event.source as Button)

    fun switchTab(tabButton:Button) {
        //switching the active status
        tabsMap.keys.forEach{it.removeClass("active")}
        tabButton.addClass("active")

        //replacing the container content
        container.replaceChildren(tabsMap[tabButton]!!)
    }
}
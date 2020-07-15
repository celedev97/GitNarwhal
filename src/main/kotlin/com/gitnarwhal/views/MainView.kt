package com.gitnarwhal.views


import com.gitnarwhal.components.RepoTab
import javafx.event.Event
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import tornadofx.*
import java.net.URL
import java.util.*

class MainView : View() {
    override val root:Parent by fxml(null as String?, true)
    val tabPane :TabPane by fxid()

    private val plusTab = with(Tab("+")){
        addClass("addTab")
        isClosable = false
        whenSelected { addNewTab() }
        this
    }

    init {
        tabPane.tabs.add(RepoTab("./").tab)
        tabPane.tabs.add(plusTab)
    }

    fun addNewTab(){
        val newTab = AddCloneTab().tab

        tabPane.tabs.add(tabPane.tabs.size-1, newTab)
        tabPane.selectionModel.select(newTab)
    }

}
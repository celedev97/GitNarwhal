package com.gitnarwhal.views


import com.gitnarwhal.components.RepoTab
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

    init {
        tabPane.tabs.add(RepoTab("./").tab)
    }

}
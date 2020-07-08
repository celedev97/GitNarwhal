package com.gitnarwhal.views


import com.gitnarwhal.components.RepoTab
import com.gitnarwhal.views.View.Companion.fxml
import javafx.fxml.FXML
import javafx.scene.Parent
import javafx.scene.control.TabPane
import java.net.URL
import java.util.*

class MainView : View {
    val root:Parent = fxml(this);

    @FXML lateinit var tabPane : TabPane


    override fun initialize(location: URL?, resources: ResourceBundle?) {
        tabPane.tabs.add(RepoTab("D:\\Desktop\\repos\\architecture-helper"))
    }

}
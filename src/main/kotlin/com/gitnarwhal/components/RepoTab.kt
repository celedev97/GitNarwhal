package com.gitnarwhal.components

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.layout.VBox
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.*

class RepoTab(path: String) : Tab("")  {
    var path:String;

    init {
        this.path = path
        var fxmlLoader = FXMLLoader(javaClass.getResource("/components/RepoTab.fxml"))
        fxmlLoader.setController(this)

        content = fxmlLoader.load<VBox>()

        text = path.split('\\','/').last()
    }

    @FXML
    fun commit(){
        println("COMMIT!!!: $path")
    }

}
package com.gitnarwhal.views

import com.gitnarwhal.GitNarwhal
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.Parent
import java.net.URL
import java.util.*

interface View : Initializable {
    override fun initialize(location: URL?, resources: ResourceBundle?) {

    }

    companion object{
        @JvmStatic
        fun <T> fxml(controller: Initializable): T =
                GitNarwhal.fxml<T>("/views/" + (controller.javaClass.simpleName) + ".fxml", controller);
    }

}
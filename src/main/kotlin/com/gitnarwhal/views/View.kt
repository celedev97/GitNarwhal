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
        fun <T> fxml(controller: Any): T {
            println("INITIALIZATING: " + controller)

            //loading Component from FXML
            val fxmlLoader = FXMLLoader(GitNarwhal::class.java.getResource("/views/" + (controller.javaClass.simpleName) + ".fxml"))
            fxmlLoader.setControllerFactory { controller }

            fxmlLoader.load<Any>()
            val component = fxmlLoader.getController<Initializable>()

            return fxmlLoader.getRoot<T>()
        }
    }

}
package com.gitnarwhal.components

import com.gitnarwhal.GitNarwhal
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import java.net.URL
import java.util.*

interface Component : Initializable {
    override fun initialize(location: URL?, resources: ResourceBundle?) {

    }

    companion object{
        @JvmStatic
        fun <T> fxml(controller: Any): T {
            println("INITIALIZATING: " + controller)

            //loading Component from FXML
            val fxmlLoader = FXMLLoader(GitNarwhal::class.java.getResource("/components/" + (controller.javaClass.simpleName) + ".fxml"))
            fxmlLoader.setControllerFactory {  controller }

            fxmlLoader.load<Any>()

            return fxmlLoader.getRoot<T>()
        }
    }

}
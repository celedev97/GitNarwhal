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
        fun <T> fxml(controller: Initializable): T =
                GitNarwhal.fxml("/components/" + (controller.javaClass.simpleName) + ".fxml", controller)
    }

}
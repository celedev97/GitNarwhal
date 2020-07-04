package com.gitnarwhal

import com.gitnarwhal.views.MainView
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import java.util.*
import java.util.jar.Manifest


fun main(){
    var manifestAttributes = Manifest(GitNarwhal::class.java.classLoader.getResource("META-INF/MANIFEST.MF").openStream()).getMainAttributes();
    var version = manifestAttributes.getValue("Specification-Version")
    println("RUNNING GITNARWHAL "+version)
    Application.launch(GitNarwhal::class.java)
}

class GitNarwhal() : Application() {

    override fun start(primaryStage: Stage) {
        primaryStage.scene = Scene(fxmlView<Parent>("MainView"))
        primaryStage.show();
    }

    companion object{
        //region fxml helpers
        fun <T> fxmlView(url: String = "", clazz: Class<*>? = null): T {
            return fxml("/views/" + (clazz?.simpleName ?: url) + ".fxml")
        }

        fun <T> fxmlComponent(url: String = "", clazz: Class<*>? = null): T {
            return fxml("/components/" + (clazz?.simpleName ?: url) + ".fxml")
        }

        fun <T> fxml(absoluteUrl: String): T {
            return FXMLLoader.load<T>(GitNarwhal::class.java.getResource(absoluteUrl))
        }
        //endregion
    }
}

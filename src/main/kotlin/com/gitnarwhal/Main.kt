package com.gitnarwhal

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.views.MainView
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage

import java.util.jar.Manifest

fun main(){
    Application.launch(GitNarwhal::class.java)
}

class GitNarwhal() : Application() {
    override fun start(primaryStage: Stage) {
        primaryStage.scene = Scene(MainView().root)
        primaryStage.icons.add(Image(GitNarwhal::class.java.getResourceAsStream("/icon.png")));
        primaryStage.show()

        //hackish stuff to make the window pop on top since it doesn't do that when the IDE starts it
        primaryStage.isAlwaysOnTop = true;
        primaryStage.isAlwaysOnTop = false;

        //checking updates
        if(Settings.autoUpdate){
            val manifestAttributes = Manifest(GitNarwhal::class.java.classLoader?.getResource("META-INF/MANIFEST.MF")?.openStream()).mainAttributes;
            val version = manifestAttributes.getValue("Specification-Version")
            println("Running GitNarwhal v$version")
        }

        //ensuring Git Presence
        println("Git location = \"${Git.GIT}\"")

        //loading settings
    }

    companion object{
        fun <T> fxml(path: String, controller:Initializable?):T{
            val fxmlLoader = FXMLLoader(GitNarwhal::class.java.getResource(path))
            if (controller != null)
                fxmlLoader.setControllerFactory {controller}

            fxmlLoader.load<Any>()

            return fxmlLoader.getRoot<T>()
        }
    }

}

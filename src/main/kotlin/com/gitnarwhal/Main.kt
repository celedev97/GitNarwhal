package com.gitnarwhal

import com.gitnarwhal.utils.GitDownloader
import com.gitnarwhal.views.MainView
import javafx.application.Application
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage

import java.util.jar.Manifest

fun main(){
    //checking updates
    var manifestAttributes = Manifest(GitNarwhal::class.java.classLoader.getResource("META-INF/MANIFEST.MF").openStream()).mainAttributes;
    var version = manifestAttributes.getValue("Specification-Version")
    println("RUNNING GITNARWHAL "+version)

    //ensuring git presence
    println(Git.GIT)


    Application.launch(GitNarwhal::class.java)
}

class GitNarwhal() : Application() {
    override fun start(primaryStage: Stage) {
        primaryStage.scene = Scene(MainView().root)
        primaryStage.icons.add(Image(GitNarwhal::class.java.getResourceAsStream("/icon.png")));
        primaryStage.show();
    }
}

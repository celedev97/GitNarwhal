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
import tornadofx.App

import java.util.jar.Manifest

fun main(){
    Application.launch(GitNarwhal::class.java)
}

class GitNarwhal : App(MainView::class) {
    override fun start(primaryStage: Stage) {

        primaryStage.icons.add(Image(GitNarwhal::class.java.getResourceAsStream("/icon.png")));

        //checking updates
        if(Settings.autoUpdate){
            val manifestAttributes = Manifest(GitNarwhal::class.java.classLoader?.getResource("META-INF/MANIFEST.MF")?.openStream()).mainAttributes;
            val version = manifestAttributes.getValue("Specification-Version")
            println("Running GitNarwhal v$version")
        }

        //ensuring Git Presence
        println("Git location = \"${Git.GIT}\"")

        super.start(primaryStage)
    }
}

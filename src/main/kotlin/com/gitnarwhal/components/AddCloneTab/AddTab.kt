package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.toPath
import com.gitnarwhal.views.AddCloneTab
import com.gitnarwhal.views.RepoTab
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.control.TextField
import javafx.stage.DirectoryChooser
import org.json.JSONObject
import tornadofx.*
import java.nio.file.Files

class AddTab(private val addCloneTab: AddCloneTab) : Fragment() {
    override val root:Parent by fxml(null as String?, true)

    val path:TextField by fxid()
    val name:TextField by fxid()

    private val directoryChooser = DirectoryChooser()

    fun run() {
        if(!Files.isDirectory(path.text.toPath())){
            Alert(Alert.AlertType.ERROR,"Error: The specified path is not a directory").show()
            return
        }

        var openTabsPaths = Settings.openTabs.map { (it as JSONObject).getString("path") }
        if(openTabsPaths.contains(path.text)){
            //TODO: handle this in a better way, like... switch the tab :v
            Alert(Alert.AlertType.ERROR,"This repository is already open in another tab").showAndWait()
            return
        }

        with(addCloneTab.mainView){
            var repo = RepoTab(path.text, name.text)
            addTab(repo)
            selectTab(repo)
            closeTab(addCloneTab.tab)
        }
    }

    fun browse(){
        path.text = directoryChooser.showDialog(currentStage)?.absolutePath?.toString() ?: path.text
        name.text = path.text.toPath().last().toString()
    }

}
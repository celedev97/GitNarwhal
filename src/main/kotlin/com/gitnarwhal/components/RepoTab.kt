package com.gitnarwhal.components

import com.gitnarwhal.Git
import com.gitnarwhal.components.Component.Companion.fxml
import javafx.scene.control.Tab

class RepoTab(path: String) : Tab(""), Component {
    //path of this repo, setting it also sets the title of the tab
    var path:String = ""
        set(value){
            field = value;
            text = value.split("\\","/").last()
        }

    init {
        this.path = path
        this.content = fxml(this)
    }

    fun commit(){
        println("COMMIT!!!: $path")
        println("THIS: $this")
    }

    fun refresh(){

    }

    fun fetch(){
        println(Git(path))
    }

}
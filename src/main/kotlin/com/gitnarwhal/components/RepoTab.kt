package com.gitnarwhal.components

import com.gitnarwhal.Git
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.control.Tab
import javafx.scene.layout.VBox
import java.net.URL
import java.util.*

class RepoTab : Tab(""), Initializable  {

    //region FIELDS
    var path:String = ""
        set(value){
            field = value;
            text = value.split("\\","/").last()
        }
    //endregion
    //region FXML FIELDS


    //endregion


    override fun initialize(location: URL?, resources: ResourceBundle?) {

    }

    @FXML
    fun commit(){
        println("COMMIT!!!: $path")
    }

    fun refresh(){

    }

    fun fetch(){
        println(Git("/usr/bin/git").git)
    }



    companion object{
        fun create(path: String) : RepoTab{
            //loading RepoTab from FXML
            var fxmlLoader = FXMLLoader(RepoTab::class.java.getResource("/components/RepoTab.fxml"))
            var content = fxmlLoader.load<VBox>()
            var repoTab = fxmlLoader.getController<RepoTab>()

            //assigning RepoTab data
            repoTab.content = content
            repoTab.path = path

            return repoTab
        }
    }

}
package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import javafx.collections.FXCollections
import javafx.collections.ObservableListBase
import javafx.fxml.FXML
import javafx.scene.control.Tab
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import org.json.JSONObject
import java.net.URL
import java.nio.file.Path
import java.util.*

class RepoTab(path: String) : Tab(""), Component {

    //region class Fields
    var path:String = ""
        set(value){
            field = Path.of(value).toAbsolutePath().toString();
            text = field.split("\\","/").last()
        }

    var git = Git(this.path)
    //endregion

    //region FXML components
    @FXML lateinit var commitTable : TableView<Commit>
    //endregion

    init {
        this.path = path
        this.content = Component.fxml(this)
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {

    }


    fun commit(){
        println("COMMIT!!!: $path")
        println("THIS: $this")
    }

    fun refresh(){
        var log = git.log()
        if(log.success){
            for (line in log.output.lines().asReversed()){
                //if the line contains * then it's a commit
                if(line.contains('*')){
                    var commitJSON = JSONObject(line.substring(line.indexOf('{')))
                    var graphInfo  = line.substring(line.indexOf('*'))

                    commitTable.items.add(Commit(commitJSON))
                }
            }
        }

        println(Git(path).log().output)
    }

    fun fetch(){
        git.fetch();
        refresh()
    }

}
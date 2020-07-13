package com.gitnarwhal.components

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TableView
import org.json.JSONObject
import tornadofx.*
import java.net.URL
import java.nio.file.Paths
import java.util.*

class RepoTab(path: String) : Fragment() {
    //region class Fields
    val tab = Tab()

    var path:String = ""
        set(value){
            field = Paths.get(value).toAbsolutePath().toString();
            val pieces = field.split("\\","/")
            tab.text = if(pieces.last() != ".") pieces.last() else pieces[pieces.size-2]
        }

    var git = Git(this.path)
    //endregion

    //region FXML components
    override val root:Parent by fxml(null as String?, true)

    val commitTable:TableView<Commit> by fxid()
    //endregion


    init {
        this.path = path
        this.tab.content = root
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
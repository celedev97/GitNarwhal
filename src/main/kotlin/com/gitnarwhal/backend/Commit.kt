package com.gitnarwhal.backend

import com.gitnarwhal.views.RepoTab
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import org.json.JSONObject
import tornadofx.View
import tornadofx.add
import tornadofx.hbox
import java.lang.Exception
import kotlin.reflect.KProperty

class Commit(var hash: String, val repoTab: RepoTab){

    //data for drawing commit
    var explored: Boolean = false
    var y = -1;
    var x = -1;

    //region Git show parameters
    private val show = GitShow()

    val shortHash       by show

    var author          by show
    var authorDate      by show

    var committer       by show
    var committerDate   by show

    var title           by show

    val message         by show
    //endregion

    val childs = arrayListOf<Commit>()
    val parents = arrayListOf<Commit>()

    var graph = run {
        val node = HBox()
        for (i in 1..3){
            node.add(Circle(10.0, Color.gray(.0)))
        }
        node
    }

    override fun toString(): String {
        return hash
    }
}

open class GitShow(){
    var commitShowData: List<String>? = null

    val linePositions = arrayOf(
            "shortHash",

            "author",
            "authorDate",

            "committer",
            "committerDate",

            "title"
    )

    operator fun getValue(commit: Commit, property: KProperty<*>): String {
        if(commitShowData == null) {
            with(commit.repoTab.git.show(commit)) {
                if (!success)
                    throw Exception("can't get data for commit: ${commit.hash}")
                commitShowData = output.lines()
            }
        }
        return if(property.name == "message"){
            commitShowData!!
                    .filterIndexed { index, _ -> index >= linePositions.count() }
                    .joinToString("\n")
        }else{
            commitShowData!![linePositions.indexOf(property.name)]
        }
    }

    operator fun setValue(commit: Commit, property: KProperty<*>, any: Any) {
        TODO("Not yet implemented")
    }
}
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

class Commit(hash:String, val repoTab: RepoTab){
    var hash      :String = hash

    val show = GitShow()

    var title by show
    var date by show
    var author by show

    val childs = arrayListOf<Commit>()
    val parents = arrayListOf<Commit>()

    var graph = run {
        val node = HBox()
        for (i in 1..3){
            node.add(Circle(10.0, Color.gray(.0)))
        }
        node
    }

    var data: List<String>? = null
}

open class GitShow(){
    operator fun getValue(commit: Commit, property: KProperty<*>): Any {
        if(commit.data == null)
            with(commit.repoTab.git.show(commit)){
                if (!success)
                    throw Exception("can't get data for commit: ${commit.hash}")
                commit.data = output.lines()
            }

        return when(property.name) {
            "title" -> commit.data!![4].trim()
            "date" -> commit.data!![2].substringAfter(':').trim()
            "author" -> commit.data!![1].substringAfter(':').trim()
            else -> throw Exception("Don't know how to parse property: ${property.name}")
        }
    }

    operator fun setValue(commit: Commit, property: KProperty<*>, any: Any) {
        TODO("Not yet implemented")
    }
}
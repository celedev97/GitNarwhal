package com.gitnarwhal.backend

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

class Commit(json:JSONObject, graph:String){
    var title       :String = json.getString("description").lines().first()
    var date        :String = json.getString("date")
    var author      :String = json.getString("author")
    var commit      :String = json.getString("commit")

    var graph       :Node = {
        val node = HBox()
        for (i in 1..3){
            node.add(Circle(10.0, Color.gray(.0)))
        }
        node
    }.invoke()
}
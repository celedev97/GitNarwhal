package com.gitnarwhal.components

import com.gitnarwhal.views.RepoTab
import javafx.scene.control.Label
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import tornadofx.*

class BranchButton(name:String ,repo:RepoTab, selected: Boolean = false) : Label(name) {

    var selected:Boolean = false
        get(){
            return field
        }
        set(value){
            field = value
            font = Font.font(font.family, if(value) FontWeight.BOLD else FontWeight.NORMAL, font.size)
        }

    var tracking: String? = null

    init {
        this.selected = selected
        addClass("branch-button")
        onDoubleClick {
            if(repo.git.selectBranch(name).success){
                println("CHECKOUT: $name")
                repo.refreshBranches()
            }
        }
    }

}
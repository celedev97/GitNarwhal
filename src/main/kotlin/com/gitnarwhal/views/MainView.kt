package com.gitnarwhal.views


import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.skin.TabPaneSkin
import tornadofx.*
import java.nio.file.Files
import java.nio.file.Path

class MainView : View() {
    override val root:Parent by fxml(null as String?, true)
    val tabPane :TabPane by fxid()

    private var moving = false;

    private val plusTab = with(Tab("+")){
        addClass("addTab")
        isClosable = false
        whenSelected { addNewCloneTab() }
        this
    }

    init {
        for(tab in Settings.openTabs.map { it as String }){
            tabPane.tabs.add(RepoTab("./"), false)
        }

        if(Settings.openTabs.isEmpty){
            tabPane.tabs.add(RepoTab("./"))//TODO: remove this later and replace it with a clone tab
        }

        tabPane.tabs.add(plusTab)

        tabPane.tabs.addListener(ListChangeListener{
            if(!moving && tabPane.tabs.last() != plusTab){
                moving = true
                tabPane.tabs.remove(plusTab)
                tabPane.tabs.add(plusTab)
                moving = false
            }
        })
    }

    fun addNewCloneTab() : AddCloneTab{
        val newTab = AddCloneTab()

        tabPane.tabs.add(tabPane.tabs.size-1, newTab.tab)
        tabPane.selectionModel.select(newTab.tab)

        return newTab
    }

    fun addNewOpenTab() {
        var newTab = addNewCloneTab()
        newTab.switchTab(newTab.activateAddTab)
    }


    private fun ObservableList<Tab>.add(repo: RepoTab, save:Boolean = true) {
        tabPane.tabs.add(repo.tab)

        if(save){
            Settings.openTabs.put(repo.path)
            Settings.save()
        }
    }

}


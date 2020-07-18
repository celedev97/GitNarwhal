package com.gitnarwhal.views


import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import com.gitnarwhal.utils.toPath
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.skin.TabPaneSkin
import org.json.JSONObject
import tornadofx.*
import java.lang.Exception
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
        try{
            val toRemove = arrayListOf<JSONObject>();
            for(tab in Settings.openTabs.map { it as JSONObject }){
                val path = tab.getString("path").toPath()
                val name = tab.getString("name")
                if(Files.isDirectory(path)){
                    tabPane.tabs.add(RepoTab(path.toAbsolutePath().toString(), name), false)
                }else{
                    toRemove.add(tab)
                }
            }
            Settings.openTabs.removeAll { toRemove.contains(it as JSONObject) }
        }catch (ignored:Exception){
            println("Open tabs loading error")
            Settings.openTabs.removeAll { true }
        }
        Settings.save()


        if(Settings.openTabs.isEmpty){
            tabPane.tabs.add(AddCloneTab().tab)
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
            Settings.openTabs.put(
                    with(JSONObject()){
                        put("name",  repo.tab.text)
                        put("path", repo.path)
                        this
                    }
            )
            Settings.save()
        }
    }

}


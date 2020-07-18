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

    val plusTab:Tab by fxid()

    init {
        plusTab.whenSelected { addNewCloneTab() }

        //region loading last open tabs
        try{
            val toRemove = arrayListOf<JSONObject>();
            for(tab in Settings.openTabs.map { it as JSONObject }){
                val path = tab.getString("path").toPath()
                val name = tab.getString("name")
                if(Files.isDirectory(path)){
                    addTab(RepoTab(path.toAbsolutePath().toString(), name), false)
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
        //endregion

        if(Settings.openTabs.isEmpty){
            addNewCloneTab()
        }

        selectTab(tabPane.tabs.first())

    }

    fun addTab(tab:Tab) =  tabPane.tabs.add(tabPane.tabs.size-1, tab)
    fun closeTab(tab: Tab) = tabPane.tabs.remove(tab)
    fun selectTab(tab: Tab) = tabPane.selectionModel.select(tab)
    fun selectTab(repo: RepoTab) = tabPane.selectionModel.select(repo.tab)

    fun addTab(repo:RepoTab, save:Boolean = true){
        addTab(repo.tab)

        repo.tab.setOnClosed {
            Settings.openTabs.removeAll { (it as JSONObject).getString("path") == repo.path }
            Settings.save()
        }

        if(save){
            Settings.openTabs.put(
                    with(JSONObject()){
                        put("name", repo.tab.text)
                        put("path", repo.path)
                        this
                    }
            )
            Settings.save()
        }
    }

    fun addNewCloneTab() : AddCloneTab{
        val newTab = AddCloneTab(this)

        addTab(newTab.tab)
        selectTab(newTab.tab)

        return newTab
    }

    fun addNewOpenTab() {
        val newTab = addNewCloneTab()
        newTab.switchTab(newTab.activateAddTab)
    }

}


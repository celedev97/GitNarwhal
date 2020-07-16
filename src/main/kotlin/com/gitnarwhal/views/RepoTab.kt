package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TableView
import tornadofx.*
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap

class RepoTab(path: String) : Fragment() {
    //region GUI components
    override val root:Parent by fxml(null as String?, true)

    val commitTable:TableView<Commit> by fxid()

    val tab by lazy{
        val tab = Tab()
        tab.content = root
        tab
    }
    //endregion


    //region class Fields
    var path:String = ""
        set(value){
            field = Paths.get(value).toAbsolutePath().toString();
            val pieces = field.split("\\","/")
            tab.text = if(pieces.last() != ".") pieces.last() else pieces[pieces.size-2]
        }

    var git = Git(this.path)
    //endregion



    init {
        this.path = path

        tab.setOnClosed {
            for (i in 0..Settings.openTabs.count()){
                if((Settings.openTabs[i] as String) == this.path){
                    Settings.openTabs.remove(i)
                    break
                }
            }
            Settings.save()
        }

        commitTable.columns.clear()

        commitTable.column("Graph",         Commit::graph).cellFormat {
            graphic = item
        }
        commitTable.column("Description",   Commit::title)
        commitTable.column("Date",          Commit::date)
        commitTable.column("Author",        Commit::author)
        commitTable.column("Commit",        Commit::hash)

        refresh()
    }

    fun commit(){
        println("COMMIT!!!: $path")
        println("THIS: $this")
    }

    fun refresh(){
        Commit.commits.clear()

        var commitIndentation = HashMap<String, Int>()
        var parentChilds = HashMap<String, Int>()

        var log = git.log()
        if(log.success){
            for (line in log.output.lines().map { it.replace('\'',' ').trim() }){
                //splitting the commit line
                val split = line.split(" ")

                //dividing commit from parents
                val hash = split[0]
                val parents = if(split.size>1) split.subList(1,split.size) else listOf()

                //finding the parent minimum indentation
                var indentation = parents.map { commitIndentation[it] as Int }.min() ?: 0

                //the real indentation is that one + the the number of commits that i've added as childs to that parent
                parents.forEach{
                    indentation += parentChilds[it] ?: 0
                    parentChilds[it] = (parentChilds[it] ?: 0) +1
                }

                //adding the found indentation to the comments indentations for next commits
                commitIndentation[hash] = indentation

                //creating the commit
                val commit = Commit(hash, indentation, parents.map{Commit.commits[it]} as List<Commit>)

                //adding it to the commit hashmap and to the commit table
                Commit.commits[hash] = commit
                commitTable.items.add(0, commit)
            }
        }

        println(Git(path).log().output)
    }

    fun fetch(){
        git.fetch();
        refresh()
    }

}
package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.BranchButton
import com.gitnarwhal.utils.OS
import com.gitnarwhal.utils.Settings
import com.gitnarwhal.utils.save
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TableView
import javafx.scene.layout.VBox
import tornadofx.*
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap

class RepoTab(var path: String, tabName: String) : Fragment() {
    //region GUI components
    override val root:Parent by fxml(null as String?, true)

    val commitTable:TableView<Commit> by fxid()

    val tab by lazy{
        val tab = Tab()
        tab.content = root
        tab
    }

    val localBranchesBox:VBox by fxid()
    val remoteBranchesBox:VBox by fxid()
    //endregion

    var git: Git
    //endregion



    init {
        tab.text = tabName
        this.git = Git(this.path)

        commitTable.columns.clear()

        commitTable.column("Graph",         Commit::graph).cellFormat {graphic = item}
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
        refreshBranches()
        refreshCommits()
    }

    fun fetch(){
        git.fetch();
        refresh()
    }

    fun refreshCommits(){
        val log = git.log()
        if(!log.success)
            return

        commitTable.items.clear()
        var commits = hashMapOf<String,Commit>()
        for (hashes in log.output.lines().map { it.replace("".toRegex(),"").trim().split(" ") }){
            //creating commits for hashes found if they doesn't exists
            hashes.forEach {
                if(!commits.contains(it))
                    commits[it] = Commit(it, this)
            }

            //separating parents from commit
            val commit = commits[hashes[0]]!!
            val parents = if(hashes.size>1) hashes.subList(1,hashes.size).map { commits[it]!! } else listOf()

            //linking parents to child and child to parents
            parents.forEach { parent->
                parent.childs.add(commit)
                commit.parents.add(parent)
            }

            //TODO: commit.column = helpmeplease
        }

        commits.forEach{ (_, commit) ->
            commitTable.items.add(commit)
        }

    }

    fun refreshBranches() {
        var gitBranches = git.branches()
        if(!gitBranches.success)
            return

        localBranchesBox.children.clear()
        remoteBranchesBox.children.clear()

        for (line in gitBranches.output.lines()){
            val branchParts = line.removePrefix("*").trim().replace("\\s+".toRegex(), " ").split(" ")
            val branchFullName  = branchParts[0]
            val branchShortName = branchFullName.substringAfter('/')

            val branchButton = BranchButton(branchShortName, this,line[0] == '*')

            if(branchShortName == branchFullName){
                //LOCAL BRANCH
                localBranchesBox.children.add(branchButton)
                branchButton.tracking = "^\\[(\\w\\w*\\/*\\w\\w*)\\]".toRegex().find(branchParts[2])?.groups?.get(1)?.value

                println()

            }else{
                //REMOTE BRANCH
                remoteBranchesBox.children.add(branchButton)
            }
        }

    }

    fun openTerminal() = runAsync {  OS.TERMINAL.execute(path) }
    fun openExplorer() = runAsync { (OS.EXPLORER + path).execute() }
    fun openRemote() = runAsync { (OS.BROWSER + git.remoteUrl().output).execute() }

}
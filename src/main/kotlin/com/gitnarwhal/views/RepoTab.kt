package com.gitnarwhal.views

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.components.BranchButton
import com.gitnarwhal.components.CommitDataPanel
import com.gitnarwhal.utils.CollapsibleTabPaneHelper.Companion.collapsible
import com.gitnarwhal.utils.OS
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.layout.VBox
import tornadofx.*

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

    val sideBarOpenButton:Button by fxid()
    val sideBarCloseButton:Button by fxid()

    val sideBarSplit:SplitPane by fxid()
    val sideBar:VBox by fxid()


    val collapsible:TabPane by fxid()

    val commitDataTab:Parent by fxid()
    //endregion

    var git: Git
    private var commits: HashMap<String, Commit> = hashMapOf()
    //endregion



    init {
        tab.text = tabName
        this.git = Git(this.path)

        val commitDataPanel = CommitDataPanel(this)
        commitDataTab.addChildIfPossible(commitDataPanel.root)

        commitTable.columns.clear()

        commitTable.column("Graph",         Commit::graph).cellFormat {graphic = item}
        commitTable.column("Description",   Commit::title)
        commitTable.column("Date",          Commit::committerDate)
        commitTable.column("Committer",     Commit::committer)
        commitTable.column("Commit",        Commit::hash)

        commitTable.columns.forEach { it.isSortable = false }


        commitTable.onSelectionChange {
            if(it != null) {
                commitDataPanel.getInfosFromHash(it)
            }
        }



        initSideBar()

        refresh()

        collapsible.collapsible()
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
        commits.clear()

        //region Getting commits base structure
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
        //endregion

        //region Assigning y coordinate to commits based on dfs and date
        var y = 0
        fun dfs(commit: Commit){
            if (!commit.explored){
                commit.explored = true;
                commit.childs.forEach { child ->
                    dfs(child)
                }
                commit.y = y
                y++
            }
        }

        commits.values.sortedBy { -it.committerDate.toInt() }.forEach {
            dfs(it)
        }
        //endregion

        //Drawing commits... ?
        commits.values.sortedBy { it.y }.forEach{
            commitTable.items.add(it)
        }

    }

    fun refreshBranches() {
        val gitBranches = git.branches()
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


    //region Sidebar stuff
    private val sideBarPanesOpened = hashMapOf<TitledPane, Boolean>()

    private var previousSideBarWidth = 0.3;
    private var originalSideBarMaxWidth = 0.0;
    private var originalSideBarMinWidth = 0.0;

    private fun initSideBar(){
        //adding listener that open the sidebar if a titledPane is opened with the sidebar closed
        for(pane in sideBar.children.filterIsInstance<TitledPane>()){
            pane.expandedProperty().addListener { _, _, newValue ->
                if(newValue && sideBarOpenButton.isVisible){
                    openSideBar()
                    pane.isExpanded = true
                }
            }
        }
    }

    fun openSideBar(){
        if(!sideBarOpenButton.isVisible)
            return;

        sideBar.removeClass("closed")

        //switching the buttons
        sideBarOpenButton.hide()
        sideBarCloseButton.show()
        sideBarCloseButton.requestFocus()

        //reopening old panes
        sideBarPanesOpened.forEach { tab, expanded ->
            tab.isExpanded = expanded
        }

        //opening sidebar
        sideBar.minWidth = originalSideBarMinWidth
        sideBar.maxWidth = originalSideBarMaxWidth
        sideBarSplit.setDividerPosition(0,  previousSideBarWidth)
    }
    fun closeSideBar(){
        if(!sideBarCloseButton.isVisible)
            return;

        sideBar.addClass("closed")

        //switching the buttons
        sideBarCloseButton.hide()
        sideBarOpenButton.show()
        sideBarOpenButton.requestFocus()

        //saving last opened panes
        sideBar.children.filterIsInstance<TitledPane>().forEach {
            sideBarPanesOpened[it] = it.isExpanded
        }

        //saving sidebar data
        previousSideBarWidth = sideBarSplit.dividerPositions[0]
        originalSideBarMinWidth = sideBar.minWidth
        originalSideBarMaxWidth = sideBar.maxWidth

        //closing all the panes
        sideBarPanesOpened.keys.forEach {
            it.isExpanded = false
        }

        //closing sidebar
        sideBar.minWidth = sideBarOpenButton.width
        sideBar.maxWidth = sideBarOpenButton.width
    }

    fun selectCommit(hash: String) {
        commitTable.selectionModel.select(commits[hash])
    }
    //endregion



}
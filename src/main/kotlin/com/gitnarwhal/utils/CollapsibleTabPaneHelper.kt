package com.gitnarwhal.utils

import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.skin.TabPaneSkin
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import tornadofx.*

class CollapsibleTabPaneHelper(val tabPane: TabPane) {

    private var selected: Tab? = tabPane.selectionModel.selectedItem

    private var headerHeight = -1.0
        get() {
            if (field == -1.0){
                val tabHeaderArea = tabPane.lookupAll(".tab-container").first().parent.parent.parent as StackPane
                field = tabHeaderArea.height
                println("size: $field")
            }

            return field
        }

    private var preCollapseHeight = -1.0;
    private var preCollapseMinHeight = -1.0;
    private var preCollapseMaxHeight = -1.0;

    private var preCollapseSplit: Double? = null;

    private val parentSplit : SplitPane? by lazy{
        if(tabPane.parent?.parent?.javaClass == SplitPane::class.java)
            (tabPane.parent!!.parent!! as SplitPane)
        else
            null
    }


    var collapsed = false
        set(collapse) {
            if(collapse != field){

                print("height: "+tabPane.prefHeight)
                if(collapse){
                    //hide
                    preCollapseHeight = tabPane.height
                    preCollapseMinHeight = tabPane.minHeight
                    preCollapseMaxHeight = tabPane.maxHeight

                    preCollapseSplit = parentSplit?.dividerPositions!![0]

                    tabPane.prefHeight = headerHeight
                    tabPane.maxHeight = Region.USE_PREF_SIZE
                    tabPane.minHeight = Region.USE_PREF_SIZE
                }else{
                    //show
                    tabPane.prefHeight = preCollapseHeight
                    tabPane.minHeight = preCollapseMinHeight
                    tabPane.maxHeight = preCollapseMaxHeight

                    parentSplit?.setDividerPosition(0, (preCollapseSplit ?: 0.5))
                }

                println("new: "+tabPane.prefHeight)
            }
            field = collapse
        }

    init {
        tabPane.skin = tabPane.skin ?: TabPaneSkin(tabPane)
        tabPane.tabs.onChange {
            setHooks()
            selected = tabPane.selectionModel.selectedItem
        }

        setHooks()
    }

    private fun setHooks() {
        tabPane.lookupAll(".tab-container").forEach { node ->
            val parent = node.parent
            val tab = parent.properties[Tab::class.java] as Tab
            parent.setOnMouseClicked {
                println("click, collapsed($collapsed), sameSelected(${selected == tab})")
                if(collapsed || selected == tab){
                    collapsed = !collapsed
                    println("Switched collapsed to: $collapsed")
                }
                selected = tab
            }
        }
    }

    companion object{
        var type = CollapsibleTabPaneHelper::class.java

        fun TabPane.collapsible(): CollapsibleTabPaneHelper {
            if (!this.properties.containsKey(type)){
                this.properties[type] = CollapsibleTabPaneHelper(this)
            }
            return this.properties[type] as CollapsibleTabPaneHelper
        }

    }

}


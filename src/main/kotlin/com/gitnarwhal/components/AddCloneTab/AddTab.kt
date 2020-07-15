package com.gitnarwhal.components.AddCloneTab

import com.gitnarwhal.backend.Commit
import com.gitnarwhal.backend.Git
import com.gitnarwhal.views.AddCloneTab
import javafx.scene.Parent
import javafx.scene.control.Tab
import javafx.scene.control.TableView
import tornadofx.*
import java.nio.file.Paths
import kotlin.collections.HashMap

class AddTab: Fragment() {
    override val root:Parent by fxml(null as String?, true)
}
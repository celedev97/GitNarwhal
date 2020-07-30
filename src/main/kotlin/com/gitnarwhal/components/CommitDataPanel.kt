package com.gitnarwhal.components


import com.gitnarwhal.backend.Commit
import com.gitnarwhal.utils.Command
import com.gitnarwhal.views.RepoTab
import javafx.scene.control.TextArea
import javafx.scene.layout.VBox
import tornadofx.*
import java.lang.Exception
import java.util.*

class CommitDataPanel(val repo:RepoTab) : Fragment() {
    override val root: VBox by fxml(null as String?, true)

    init {
        root.spacing = 5.0
    }

    fun getInfosFromHash(commit: Commit) {
        with(root){
            children.clear()

            //commit
            textflow {
                label("Commit: "){addClass("bold")}
                label ("${commit.hash} [")
                hyperlink(commit.shortHash){
                    onLeftClick { repo.selectCommit(commit.hash) }
                }
                label("]")
            }

            //parents
            textflow {
                label("Parents: "){addClass("bold")}
                commit.parents.forEach { parentCommit ->
                    hyperlink(parentCommit.shortHash){
                        onLeftClick { repo.selectCommit(parentCommit.hash) }
                    }
                }
            }

            //author
            if(commit.committerDate != commit.authorDate) {
                root.vbox {
                    textflow {
                        label("Author: ") { addClass("bold") }
                        label(commit.author)
                    }
                    textflow {
                        label("Author Date: ") { addClass("bold") }
                        label(commit.authorDate)
                    }
                }
            }

            //committer
            vbox {
                textflow {
                    label("Committer: "){addClass("bold")}
                    label(commit.committer)
                }
                textflow {
                    label("Committer Date: "){addClass("bold")}
                    label(commit.committerDate)
                }
            }

            //commit title and message
            vbox {
                spacing = root.spacing
                label(commit.title){addClass("bold"); isWrapText = true}
                label(commit.message){isWrapText = true}
            }

        }
    }

}
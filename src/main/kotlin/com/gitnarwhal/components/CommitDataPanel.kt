package com.gitnarwhal.components


import com.gitnarwhal.backend.Commit
import com.gitnarwhal.utils.Command
import javafx.scene.control.TextArea
import tornadofx.*
import java.lang.Exception
import java.util.*

class CommitDataPanel : Fragment() {
    override val root: TextArea by fxml(null as String?, true)

    init {
    }

    fun getInfosFromHash(commit: Commit): String {
        with(commit){
            root.text = """
                        Commit: $hash [$shortHash]
                        Parents: ${parents.joinToString(", ")}
                        
                        Author: $author
                        Author Date: $authorDate
                        
                        Committer: $committer
                        Committer Date: $committerDate

                        $title
                        $message
                        """.trimIndent()
        }
        return root.text
    }

}
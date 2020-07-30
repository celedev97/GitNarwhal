package com.gitnarwhal.views


import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Command
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
import java.util.*

class CommitDataPanel : Fragment() {
    override val root:Parent by fxml(null as String?, true)
    val tabPane :TabPane by fxid()

    fun getInfosFromHash(hash:String): String {
        // check if we are missing to pass something to the function
        if(hash.equals("")) {
            throw Exception("Hash cant be null!")
        }

        // *** NOTES ON GIT FORMAT ***
        // Commit, short commit, short parents, author name, autor email, author date,
        // commit date, committer name,commit message
        // flags: %H, %h, %p, %an, %ae, %ad, %cn, %s
        var response = Command("git log " + hash + " -n 1 --format=\"%H|%h|%p|%an|%ae|%ad|%cn|%s\"").execute().output

        // check if the commit passed is an invalid commit
        if(response.contains("unknown")) {
            throw Exception("This hash does not exist!")
        }

        val parsedResponse = response.split("|").toTypedArray()

        // *** DEBUG PRINT ***
        print("[DEBUG][getInfosFromHash]:")
        for (i in response) { println(i) }

        // parsing parents?
        val parents = parsedResponse[2].split("|").toTypedArray()

        // composing the string
        var bigFinalString = "Commit: " + parsedResponse[0] + "[" + parsedResponse[1] + "]\n" +
                "Parents: " + Arrays.stream(parents).forEach(System.out::println) + "\n" + // TODO: does it work, right?
                "Author:" + parsedResponse[3] + " " + "<"+ parsedResponse[4] +">\n" +
                "Date: " + parsedResponse[5] + "\n" +
                "Committer: " + parsedResponse[6]

        print("[DEBUG][getInfosFromHash]:" + bigFinalString)

        return bigFinalString
    }
}
package com.gitnarwhal.utils

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties

//TODO: discover if you can write this in a better way, the fact that you have a class constant outside of the class it's horrible
private val CONFIG = System.getProperty("user.home") + "/.gitnarwhal.json"
private val settingsJSON = if(File(CONFIG).exists()) Files.readString(Path.of(CONFIG)) else "{}";

object Settings : JSONObject(settingsJSON) {
    val FILE = CONFIG

    //TODO: you could add an optional callback to JSONSettings, so editing a property immediately call the callback that applies it.

    var autoUpdate  by JSONSetting(true)
    var theme       by JSONSetting("jar://stylesheets/main.css")

    var openTabs    by JSONSetting(JSONArray())

}

fun Settings.save() {
    Thread{
        synchronized(this){
            with(FileWriter(Settings.FILE)){
                Settings.write(this, 4,0)
                this.close()
            }
        }
    }.start()
}


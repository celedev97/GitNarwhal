package com.gitnarwhal.utils

import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties


private const val CONFIG = "./gitnarwhal.json"
private val CONFIG = System.getProperty("user.home") + "/.gitnarwhal.json"
private val settingsJSON = if(File(CONFIG).exists()) Files.readString(Path.of(CONFIG)) else "{}";

object Settings : JSONObject(settingsJSON) {
    val FILE = CONFIG;

    var autoUpdate by JSON(true)
    var theme by JSON("jar://stylesheets/main.css")

    fun write() {
        //forcing a read on every declared property, this should set the default value for everything that doesn't have a value
        Settings.javaClass.kotlin.memberProperties.forEach{
            var test = it.get(Settings)
            println(test)
        }

        with(FileWriter(FILE)){
            super.write(this, 4,0)
            this.close()
        }
    }
}


class JSON<T> (private val default: T) {
    operator fun setValue(settings: Settings, property: KProperty<*>, value: T) {
        settings.put(property.name, value)
        Settings.write()
    }

    operator fun getValue(settings: Settings, property: KProperty<*>): T {
        if(!settings.has(property.name)){
            setValue(settings, property, default)
        }
        @Suppress("UNCHECKED_CAST")
        return settings.get(property.name) as T
    }

}
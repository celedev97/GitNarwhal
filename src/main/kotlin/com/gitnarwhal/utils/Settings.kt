package com.gitnarwhal.utils

import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KProperty


private const val CONFIG = "./gitnarwhal.json"
private val settingsJSON = if(File(CONFIG).exists()) Files.readString(Path.of(CONFIG)) else "{}";

object Settings : JSONObject(settingsJSON) {
    const val FILE = CONFIG;

    var autoUpdate by JSON(true)
    var theme by JSON("jar://stylesheets/main.css")

    fun write() {
        with(FileWriter(FILE)){
            super.write(FileWriter(FILE))
            this.close()
        }
    }
}


class JSON<T> (val default: T) {
    init {
        Settings.put(property.name, this.default)
    }

    operator fun setValue(settings: Settings, property: KProperty<*>, value: T) {
        settings.put(property.name, value)
    }

    operator fun getValue(settings: Settings, property: KProperty<*>): T {
        if(!settings.has(property.name)){
            setValue(settings, property, default)
        }
        @Suppress("UNCHECKED_CAST")
        return settings.get(property.name) as T
    }

}
package com.gitnarwhal.utils

import org.json.JSONObject
import kotlin.reflect.KProperty

open class JSONProperty<T> (private val default: T) {
    open operator fun setValue(json: JSONObject, property: KProperty<*>, value: T) {
        json.put(property.name, value)
    }

    open operator fun getValue(json: JSONObject, property: KProperty<*>): T {
        if(!json.has(property.name)){
            setValue(json, property, default)
        }
        @Suppress("UNCHECKED_CAST")
        return json.get(property.name) as T
    }

}

class JSONSetting<T> (private val default: T) : JSONProperty<T>(default) {
    operator fun setValue(settings: Settings, property: KProperty<*>, value: T) {
        super.setValue(settings, property, value)
        settings.save()
    }

    operator fun getValue(json: Settings, property: KProperty<*>): T {
        return super.getValue(json, property)
    }
}

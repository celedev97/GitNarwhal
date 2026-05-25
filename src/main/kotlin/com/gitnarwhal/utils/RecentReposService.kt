package com.gitnarwhal.utils

import org.json.JSONObject

object RecentReposService {

    data class RecentRepo(val name: String, val path: String, val lastOpened: Long)

    private const val MAX = 50

    fun record(name: String, path: String) {
        val arr = Settings.recentRepos
        // remove existing entry for same path
        val toRemove = (0 until arr.length())
            .filter { (arr.get(it) as JSONObject).getString("path") == path }
        toRemove.reversed().forEach { arr.remove(it) }
        // prepend
        val entry = JSONObject().apply {
            put("name", name); put("path", path); put("lastOpened", System.currentTimeMillis())
        }
        // JSONArray has no insert — rebuild with entry first
        val rebuilt = org.json.JSONArray()
        rebuilt.put(entry)
        for (i in 0 until minOf(arr.length(), MAX - 1)) rebuilt.put(arr.get(i))
        Settings.recentRepos = rebuilt
        Settings.save()
    }

    fun remove(path: String) {
        val arr = Settings.recentRepos
        val toRemove = (0 until arr.length())
            .filter { (arr.get(it) as JSONObject).getString("path") == path }
        toRemove.reversed().forEach { arr.remove(it) }
        Settings.save()
    }

    fun getAll(): List<RecentRepo> {
        val arr = Settings.recentRepos
        return (0 until arr.length()).mapNotNull {
            val obj = arr.optJSONObject(it) ?: return@mapNotNull null
            RecentRepo(
                name       = obj.optString("name", ""),
                path       = obj.optString("path", ""),
                lastOpened = obj.optLong("lastOpened", 0L)
            )
        }
    }
}

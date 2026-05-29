package com.gitnarwhal.utils

import org.json.JSONObject

object RecentReposService {

    data class RecentRepo(
        val name: String,
        val path: String,
        val lastOpened: Long,
        val folderId: String? = null
    )

    data class RecentFolder(val id: String, val name: String)

    private const val MAX = 50

    fun record(name: String, path: String) {
        val arr = Settings.recentRepos
        // preserve the folderId from any existing entry for this path
        val existingFolderId = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("path") == path }
            ?.let { obj -> obj.optString("folderId").ifBlank { null } }
        // remove existing entry for same path
        val toRemove = (0 until arr.length())
            .filter { (arr.get(it) as JSONObject).getString("path") == path }
        toRemove.reversed().forEach { arr.remove(it) }
        // prepend
        val entry = JSONObject().apply {
            put("name", name); put("path", path); put("lastOpened", System.currentTimeMillis())
            if (existingFolderId != null) put("folderId", existingFolderId)
        }
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

    fun renameRepo(path: String, newName: String) {
        val arr = Settings.recentRepos
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("path") == path) { obj.put("name", newName); break }
        }
        Settings.save()
    }

    fun setRepoFolder(path: String, folderId: String?) {
        val arr = Settings.recentRepos
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("path") == path) {
                if (folderId != null) obj.put("folderId", folderId) else obj.remove("folderId")
                break
            }
        }
        Settings.save()
    }

    fun getAll(): List<RecentRepo> {
        val arr = Settings.recentRepos
        return (0 until arr.length()).mapNotNull {
            val obj = arr.optJSONObject(it) ?: return@mapNotNull null
            RecentRepo(
                name       = obj.optString("name", ""),
                path       = obj.optString("path", ""),
                lastOpened = obj.optLong("lastOpened", 0L),
                folderId   = obj.optString("folderId").ifBlank { null }
            )
        }
    }

    fun getFolders(): List<RecentFolder> {
        val arr = Settings.recentFolders
        return (0 until arr.length()).mapNotNull {
            val obj = arr.optJSONObject(it) ?: return@mapNotNull null
            RecentFolder(id = obj.optString("id"), name = obj.optString("name"))
        }.filter { it.id.isNotBlank() }
    }

    fun addFolder(name: String): String {
        val id = System.currentTimeMillis().toString()
        Settings.recentFolders.put(JSONObject().apply { put("id", id); put("name", name) })
        Settings.save()
        return id
    }

    fun removeFolder(id: String) {
        val folders = Settings.recentFolders
        (0 until folders.length())
            .filter { folders.optJSONObject(it)?.optString("id") == id }
            .reversed()
            .forEach { folders.remove(it) }
        // ungroup repos that belonged to this folder
        val repos = Settings.recentRepos
        for (i in 0 until repos.length()) {
            val obj = repos.optJSONObject(i) ?: continue
            if (obj.optString("folderId") == id) obj.remove("folderId")
        }
        Settings.save()
    }

    fun renameFolder(id: String, newName: String) {
        val arr = Settings.recentFolders
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) { obj.put("name", newName); break }
        }
        Settings.save()
    }
}

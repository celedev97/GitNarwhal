package com.gitnarwhal

class Git(repo: String, path: String? = null) {
    val repo = repo;
    val git: String = if(path != null) "\"" + path + "\"" else "git"

    fun status(){
        Runtime.getRuntime().exec(git+" status")
    }
}
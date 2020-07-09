package com.gitnarwhal

import com.gitnarwhal.utils.GitDownloader

class Git(repo: String) {
    companion object{
        val GIT:String = GitDownloader.GIT
    }

    val repo = repo;

    fun status(){
        Runtime.getRuntime().exec("$GIT status")
    }
}
package com.gitnarwhal.backend

import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.GitDownloader
import com.gitnarwhal.utils.OS
import com.gitnarwhal.backend.Git.Companion

class Git(val repo: String) {
    companion object{
        private val INTERNAL_GIT = "./git/bin/git${OS.EXE}"

        private var WHERE = Command.find("git")
            private set

        val GIT:String = when{
            //if the where/which command gives a result then git is in PATH
            WHERE != null -> WHERE.toString()

            //if the internal git exists than that's the git path
            java.io.File(INTERNAL_GIT).exists() -> INTERNAL_GIT

            //if neither are true then i need to download git
            else -> when(OS.CURRENT){
                OS.WINDOWS -> run {
                    GitDownloader.downloadWindowsGit();
                    INTERNAL_GIT
                };
                OS.LINUX   -> GitDownloader.downloadLinuxGit();
                OS.MAC     -> GitDownloader.downloadMacGit();
            }
        };
    }

    private fun git(vararg command:String, prependGit: Boolean = true) : Command{
        var realCommand = command
        if(prependGit){
            val list = realCommand.toMutableList()
            list.add(0,GIT)
            realCommand = list.toTypedArray()
        }

        return Command(*realCommand, path = repo).execute()
    }

    fun status() = git("status")

    fun fetch() = git("fetch")

    fun branches() = git("branch","--all","-vv")

    fun selectBranch(branch: String) = git("checkout",branch)

    fun add(fileName: String) = git("add", fileName)

    fun restore(fileName: String) = git("restore", fileName)

    fun log() = git("--no-pager", "log", "--pretty=format:%H %P")

    // Commit, short commit, short parents, author name, autor email, author date, commit date, committer name,commit message
    // flags: %H, %h, %p, %an, %ae, %ad, %cn, %s   "%H%n%h%n%cN <%cE>%n%cd%n%s"
    fun show(commit:Commit) = show(commit.hash)
    fun show(commitHash:String) =
            git("--no-pager", "show", commitHash ,"-s",
            "--pretty=format:"+arrayOf("%h", "%aN <%aE>", "%ad", "%cN <%cE>", "%cd", "%s","%b").joinToString("%n"),
            "--date=unix")

    fun remoteUrl(remote: String = "origin") = git("config", "--get", "remote.$remote.url")

}
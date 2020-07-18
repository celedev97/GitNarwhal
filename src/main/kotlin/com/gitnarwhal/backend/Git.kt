package com.gitnarwhal.backend

import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.GitDownloader

class Git(val repo: String) {
    companion object{
        val GIT:String = GitDownloader.GIT
        const val FAKE_SEP = "g\\nrwl?/>"
        const val FORMAT = "{${FAKE_SEP}commit${FAKE_SEP}:${FAKE_SEP}%H${FAKE_SEP},${FAKE_SEP}author${FAKE_SEP}:${FAKE_SEP}%aN<%aE>${FAKE_SEP},${FAKE_SEP}date${FAKE_SEP}:${FAKE_SEP}%ad${FAKE_SEP},${FAKE_SEP}description${FAKE_SEP}:${FAKE_SEP}%f${FAKE_SEP}}"
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

    fun show(commit:Commit) = show(commit.hash)
    fun show(commitHash:String) = git("--no-pager", "show", commitHash ,"-s", "--pretty=format:%cN <%cE>%n%cd%n%s")


}
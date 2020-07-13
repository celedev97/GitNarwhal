package com.gitnarwhal.backend

import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.GitDownloader

class Git(val repo: String) {
    companion object{
        val GIT:String = GitDownloader.GIT
        const val FAKE_SEP = "g\\nrwl?/>"
        const val FORMAT = "{${FAKE_SEP}commit${FAKE_SEP}:${FAKE_SEP}%H${FAKE_SEP},${FAKE_SEP}author${FAKE_SEP}:${FAKE_SEP}%aN<%aE>${FAKE_SEP},${FAKE_SEP}date${FAKE_SEP}:${FAKE_SEP}%ad${FAKE_SEP},${FAKE_SEP}description${FAKE_SEP}:${FAKE_SEP}%f${FAKE_SEP}}"
    }

    fun status() = Command("$GIT status").execute()

    fun log():Command {
        val out = Command("$GIT --no-pager log --graph --pretty=format:'$FORMAT'").execute()
        out.output = out.output.replace("\"","\\\"").replace(FAKE_SEP, "\"")
        return out
    }

    fun add(fileName: String) = Command("$GIT add $fileName").execute()

    fun restore(fileName: String) = Command("$GIT restore $fileName").execute()

    fun fetch() = Command("$GIT fetch").execute()
}
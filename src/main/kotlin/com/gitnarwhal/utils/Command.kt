package com.gitnarwhal.utils

import java.io.File
import java.nio.file.Path

class Command(vararg command: String, path: String = "./") {
    var output: String = "";

    var success:Boolean = false;
    var code:Int = -1;



    private lateinit var workingDirFile: File
    var workingDir: String
        get() = workingDirFile.absolutePath.toString()
        set(value) {
            workingDirFile = Path.of(value).toAbsolutePath().toFile()
        }

    init {
        workingDir = path
    }


    private val commandParts = run {
        var output = command.toList()
        if(command.size == 1){
            output = command[0].split(" ")
        }
        output
    }


    fun execute(path:String?): Command {
        //path override for localized commands
        var realWorkingDirectory = workingDirFile
        if(path != null){
            realWorkingDirectory = Path.of(path).toAbsolutePath().toFile()
        }

        //executing command
        val process = ProcessBuilder(commandParts).directory(realWorkingDirectory).redirectErrorStream(true).start()

        //reading result streams
        output = String(process.inputStream.readAllBytes()).trim()

        //waiting for process end
        process.waitFor()

        //getting the exit code
        code = process.exitValue()
        success = code == 0
        return this
    }

    override fun toString(): String {
        return commandParts.joinToString (" ")
    }

}



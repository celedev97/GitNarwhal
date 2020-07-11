package com.gitnarwhal.utils

import java.nio.file.Path

class Command(vararg command: String, path: String = "./") {
    var output: String = "";

    var success:Boolean = false;
    var code:Int = -1;

    val workingDir = Path.of(path).toAbsolutePath().toFile()

    private val command = run {
        var output = command.toList()
        if(command.size == 1){
            output = command[0].split(" ")
        }
        output
    }

    fun execute(): Command {
        //executing command
        val process = ProcessBuilder(command).directory(workingDir).redirectErrorStream(true).start()

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
        return command.joinToString (" ")
    }

}



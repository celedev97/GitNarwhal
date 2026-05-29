package com.gitnarwhal.utils

import java.io.File
import java.nio.file.Path

class Command(vararg command: String, path: String = "./") {

    //region Class fields/properties

    //region execute output
    var output: String = ""
        private set

    var success: Boolean = false
        private set

    var code:Int = -1
        private set
    //endregion

    private lateinit var workingDirFile: File
    var workingDir: String
        get() = workingDirFile.absolutePath.toString()
        set(value) {
            workingDirFile = Path.of(value).toAbsolutePath().toFile()
        }

    init {
        workingDir = path
    }


    private val commandParts = ArrayList(commandToCommandParts(*command))
    //endregion


    //region Private helper functions
    private fun commandToCommandParts(vararg command: String): List<String> {
        var output = command.toList()
        if(command.size == 1){
            output = command[0].trim().split(" ")
        }
        return output
    }
    //endregion

    fun execute(path: String? = null, onLine: ((String) -> Unit)? = null): Command {
        var realWorkingDirectory = workingDirFile
        if (path != null) realWorkingDirectory = Path.of(path).toAbsolutePath().toFile()

        val process = ProcessBuilder(commandParts).directory(realWorkingDirectory).redirectErrorStream(true).start()

        if (onLine != null) {
            val sb = StringBuilder()
            process.inputStream.bufferedReader().forEachLine { line ->
                sb.appendLine(line)
                onLine(line)
            }
            output = sb.toString().trim()
        } else {
            output = String(process.inputStream.readAllBytes()).trim()
        }

        process.waitFor()
        code = process.exitValue()
        success = code == 0
        return this
    }

    operator fun plus(parameters: String): Command {
        //returns a new command with appended parameters
        return Command(
                *with(ArrayList(commandParts)){
                    addAll(commandToCommandParts(parameters))
                    toTypedArray()
                }
        )
    }

    override fun toString(): String {
        return commandParts.joinToString (" ")
    }


    companion object{
        fun find(command: String): Command? {
            val where = Command(OS.WHERE, command).execute()
            if (where.success && where.output.isNotEmpty()) {
                val firstLine = where.output.lines().firstOrNull()?.trim().orEmpty()
                if (firstLine.isNotEmpty() && File(firstLine).exists()) {
                    val resolved = Command()
                    resolved.commandParts.add(firstLine)
                    return resolved
                }
            }
            return null
        }
    }

}



package com.gitnarwhal.utils

import java.io.InputStream

class Command(private val command: String) {
    var output: String = "";
    var error: String = "";

    fun execute(): Boolean {
        //executing command
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()

        //reading result streams
        output  = String(process.inputStream.readAllBytes())
        error   = String(process.errorStream.readAllBytes())

        //returning true if there was no error
        return process.exitValue() == 0
    }

}



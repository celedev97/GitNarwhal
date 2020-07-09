package com.gitnarwhal.utils

import javax.swing.JOptionPane

enum class OS{
    WINDOWS,
    LINUX,
    MAC;

    companion object{
        val CURRENT = with(System.getProperty("os.name").toLowerCase()){
            when{
                containsOne("win") -> WINDOWS
                containsOne("nix","nux","aix") -> LINUX
                containsOne("mac","osx") -> MAC
                else -> TODO("Unsupported OS!")
            }
        }

        val WHERE = when(CURRENT){
            WINDOWS -> "where"
            else -> "which"
        }

        val EXE = when(CURRENT){
            WINDOWS -> ".exe"
            else -> ""
        }

    }

}

fun String.containsOne(vararg needles:String): Boolean{
    for(needle in needles)
        if(this.indexOf(needle) != -1)
            return true
    return false
}
fun String.containsAll(vararg needles:String): Boolean{
    for(needle in needles)
        if(this.indexOf(needle) == -1)
            return false
    return true
}

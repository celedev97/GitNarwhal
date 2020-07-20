package com.gitnarwhal.utils

import com.gitnarwhal.backend.Git
import com.gitnarwhal.utils.Command
import tornadofx.*
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.Path
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

        val EXPLORER = when(CURRENT){
            WINDOWS -> Command("explorer")
            LINUX   -> Command("xdg-open")
            MAC     -> Command("open")
        }

        val BROWSER = when(CURRENT){
            WINDOWS -> Command("cmd /c START")
            LINUX   -> EXPLORER
            MAC     -> EXPLORER
        }

        val TERMINAL by lazy{
            when(CURRENT){
                WINDOWS -> run {
                    //if git bash exists than use that as the terminal
                    val gitBash = Git.GIT.removeSuffix("cmd\\git.exe") + "git-bash.exe"
                    if(Files.exists(Path.of(gitBash))){
                        return@run Command(gitBash)
                    }

                    var command: Command? = null

                    //fallback to pwsh
                    command = Command.find("pwsh")
                    if(command != null)
                        return@run command!!

                    //fallback again to powershell
                    command = Command.find("powershell")
                    if(command != null)
                        return@run command!!

                    //if there's literally nothing else than use cmd
                    Command("cmd")
                }
                LINUX   -> Command("x-terminal-emulator")
                else -> Command("open -a Terminal") //TODO: this need testing
            }
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

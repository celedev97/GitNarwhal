package com.gitnarwhal.utils

import javafx.scene.control.Alert
import javafx.scene.control.ProgressBar
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess


object GitDownloader{
    private val INTERNAL_GIT = "./git/bin/git${OS.EXE}"

    val whereGit = Command("${OS.WHERE} git");

    val GIT:String = when{
        //if the where/which command gives a result then git is in PATH
        whereGit.execute().success && whereGit.output.isNotEmpty() && File(whereGit.output.lines()[0]).exists() -> "git"

        //if the internal git exists than that's the git path
        File(INTERNAL_GIT).exists() -> INTERNAL_GIT

        //if neither are true then i need to download git
        else -> when(OS.CURRENT){
            OS.WINDOWS -> downloadWindowsGit();
            OS.LINUX   -> downloadLinuxGit();
            OS.MAC     -> downloadMacGit();
        }
    };

    private val cantDoAnything by lazy {
        with(Alert(Alert.AlertType.ERROR)){
            title = "Error"
            contentText = "Impossible to find or install Git, download it and add it to path before using GitNarwhal"
            this
        }
    }

    private fun downloadWindowsGit(progress:ProgressBar? = null): String {
        //getting latest release data
        val release = JSONObject(URL("https://api.github.com/repos/git-for-windows/git/releases/latest").readText())
        //extracting assets
        val assets  = release.getJSONArray("assets")

        //finding the portable 64 bit release
        var portable64BitRelease = assets.filter {
            (it as JSONObject).getString("name").toLowerCase().containsAll("port","64")
        }[0] as JSONObject;

        //finding its download url
        val fileName = portable64BitRelease["name"] as String
        val downloadURL = portable64BitRelease["browser_download_url"] as String

        //downloading it in a temporany directory
        val tempGit = Files.createTempFile("gitnarwhal_", fileName);

        downloadWithProgress(downloadURL, tempGit, progress)

        //extracting it
        val execute = Command("\"${tempGit}\" -o\".\\git\" -y").execute()

        Files.delete(tempGit)

        if(!execute.success || !Files.exists(Paths.get(INTERNAL_GIT))){
            cantDoAnything.showAndWait()
            exitProcess(1)
        }

        return INTERNAL_GIT
    }

    private fun downloadLinuxGit(progress:ProgressBar? = null): String {
        var commandsStrings = arrayOf(
                //debian
                "apt-get install -y git",
                //fedora
                "yum -y install git",
                "dnf -y install git",
                //Gentoo
                "emerge --verbose dev-vcs/git",
                //Arch Linux
                "pacman -S --noconfirm git",
                //openSUSE
                "zypper --non-interactive install git",
                //Mageia
                "urpmi --force git",
                //Nix/NixOS
                "nix-env -i git",
                //FreeBSD
                "pkg install -y git",
                //Solaris 9/10/11 (OpenCSW)
                "pkgutil -i -y  git",
                //Solaris 11 Express
                "pkg install -y  developer/versioning/git",
                //OpenBSD
                "pkg_add -a git",
                //Alpine
                "apk add git",
                //Slitaz
                "tazpkg get-install git"
        )
        commandsStrings.forEach {
            //executing where on the command to be sure it's a valid command
            var where = Command("${OS.WHERE} ${it.split(' ').first()}")
            if(where.execute().success && where.output.isNotEmpty()){
                println("${it.split(' ').first()} FOUND!")
                //command exists, calling it
                val install = Command("pkexec $it")
                if(install.execute().success){
                    println(install.output)
                    //if git now exists
                    if(whereGit.execute().success && whereGit.output.isNotEmpty()){
                        return "git"
                    }
                }
            }else{
                println("${it.split(' ').first()} not found")
            }
        }

        cantDoAnything.showAndWait()
        exitProcess(1)
    }

    private fun downloadMacGit(progress:ProgressBar? = null): String {
        TODO()
    }

    private fun downloadWithProgress(downloadURL:String, outputFile: Path, progress: ProgressBar?){
        val outputStream = FileOutputStream(outputFile.toFile())
        val downloadStream = URL(downloadURL).openStream()

        val buffer = ByteArray(1024)
        var byteRead : Int
        val totalSize = downloadStream.available()
        var downloadedSize = 0
        while (downloadStream.read(buffer).also { byteRead = it } > -1){
            outputStream.write(buffer, 0, byteRead)
            downloadedSize += byteRead
            progress?.progress =  totalSize.toDouble() / downloadedSize
        }
        downloadStream.close()
        outputStream.close()
    }

}
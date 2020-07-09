package com.gitnarwhal.utils

import javafx.scene.control.Alert
import javafx.scene.control.Dialog
import javafx.scene.control.ProgressBar
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


object GitDownloader{
    private val INTERNAL_GIT = "./git/bin/git${OS.EXE}"

    private val where = Command("${OS.WHERE} git");

    val GIT:String = when{
        //if the where/which command gives a result then git is in PATH
        where.execute() && where.output.isNotEmpty() && File(where.output.lines()[0]).exists() -> "git"

        //if the internal git exists than that's the git path
        File(INTERNAL_GIT).exists() -> INTERNAL_GIT

        //if neither are true then i need to download git
        else -> when(OS.CURRENT){
            OS.WINDOWS -> downloadWindowsGit();
            OS.LINUX   -> downloadLinuxGit();
            OS.MAC     -> downloadMacGit();
        }.absolutePath
    };


    private fun downloadWindowsGit(progress:ProgressBar? = null): File {
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

        if(!execute || !Files.exists(Paths.get(INTERNAL_GIT))){
            Dialog<Alert>().showAndWait()
            throw Exception("Life sucks uwu")
        }

        return File(INTERNAL_GIT)
    }

    private fun downloadLinuxGit(progress:ProgressBar? = null): File {
        TODO()
    }

    private fun downloadMacGit(progress:ProgressBar? = null): File {
        TODO()
    }

    private fun downloadWithProgress(downloadURL:String, outputFile: Path, progress: ProgressBar?){
        val outputStream = FileOutputStream(outputFile.toFile())
        val downloadStream = URL(downloadURL).openStream()

        val buffer = ByteArray(1024)
        var byteRead = -1
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
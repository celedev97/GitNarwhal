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

    private val cantDoAnything by lazy {
        with(Alert(Alert.AlertType.ERROR)){
            title = "Error"
            contentText = "Impossible to find or install Git, download it and add it to path before using GitNarwhal"
            this
        }
    }

    fun downloadWindowsGit(progress:ProgressBar? = null) {
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
    }

    fun downloadLinuxGit(progress:ProgressBar? = null): String {
        TODO()
    }

    fun downloadMacGit(progress:ProgressBar? = null): String {
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
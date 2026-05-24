package com.gitnarwhal.utils

import org.json.JSONObject
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JOptionPane


typealias ProgressCallback = (downloaded: Long, total: Long) -> Unit

object GitDownloader{

    fun showCantDoAnything() {
        JOptionPane.showMessageDialog(
            null,
            "Impossible to find or install Git, download it and add it to PATH before using GitNarwhal",
            "Error",
            JOptionPane.ERROR_MESSAGE
        )
    }

    fun downloadWindowsGit(progress: ProgressCallback? = null) {
        val release = JSONObject(URL("https://api.github.com/repos/git-for-windows/git/releases/latest").readText())
        val assets  = release.getJSONArray("assets")

        val portable64BitRelease = assets.filter {
            (it as JSONObject).getString("name").lowercase().containsAll("port","64")
        }[0] as JSONObject

        val fileName    = portable64BitRelease["name"] as String
        val downloadURL = portable64BitRelease["browser_download_url"] as String

        val tempGit = Files.createTempFile("gitnarwhal_", fileName)
        downloadWithProgress(downloadURL, tempGit, progress)

        Command("\"${tempGit}\" -o\".\\git\" -y").execute()

        Files.delete(tempGit)
    }

    fun downloadLinuxGit(progress: ProgressCallback? = null): String {
        TODO("Linux git auto-install not implemented — install git via package manager")
    }

    fun downloadMacGit(progress: ProgressCallback? = null): String {
        TODO("Mac git auto-install not implemented — install Xcode CLT or git via brew")
    }

    private fun downloadWithProgress(downloadURL: String, outputFile: Path, progress: ProgressCallback?){
        val connection = URL(downloadURL).openConnection()
        val totalSize  = connection.contentLengthLong
        connection.getInputStream().use { input ->
            FileOutputStream(outputFile.toFile()).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    progress?.invoke(downloaded, totalSize)
                }
            }
        }
    }

}

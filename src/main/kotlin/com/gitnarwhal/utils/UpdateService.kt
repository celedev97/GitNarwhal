package com.gitnarwhal.utils

import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.awt.Window
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.Properties
import javax.swing.JOptionPane
import javax.swing.ProgressMonitor
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

object UpdateService {

    private const val GITHUB_OWNER = "git-narwhal"
    private const val GITHUB_REPO  = "GitNarwhal"
    private const val API_URL      =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val VERSION_REGEX = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

    private val osName    = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("win")
    private val isMac     = osName.contains("mac")
    private val isLinux   = !isWindows && !isMac

    // ── Version read from bundled version.properties ──────────────────────────
    val currentVersion: String by lazy {
        try {
            val props = Properties()
            val stream = UpdateService::class.java.getResourceAsStream("/version.properties")
                ?: return@lazy "dev"
            props.load(stream)
            props.getProperty("version", "dev")
        } catch (_: Exception) { "dev" }
    }

    // ── Public entry point — call once after UI is shown ──────────────────────
    fun checkForUpdates(owner: Window?) {
        if (!Settings.autoUpdate) return
        if (!VERSION_REGEX.containsMatchIn(currentVersion)) return  // dev build
        fetchAndNotify(owner, silent = true)
    }

    // ── Manual trigger from Help menu — always runs, shows "up to date" ───────
    fun checkForUpdatesManual(owner: Window?) {
        fetchAndNotify(owner, silent = false)
    }

    private fun fetchAndNotify(owner: Window?, silent: Boolean) {
        object : SwingWorker<JSONObject?, Void>() {
            override fun doInBackground(): JSONObject? {
                return try {
                    val client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build()
                    val req = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "GitNarwhal/$currentVersion")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build()
                    val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() == 200) JSONObject(resp.body()) else null
                } catch (_: Exception) { null }
            }

            override fun done() {
                val release = try { get() } catch (_: Exception) { null }
                if (release == null) {
                    if (!silent) JOptionPane.showMessageDialog(owner,
                        "Could not reach GitHub. Check your connection.",
                        "Update check failed", JOptionPane.WARNING_MESSAGE)
                    return
                }
                val newTag  = release.optString("tag_name", "").ifBlank {
                    if (!silent) JOptionPane.showMessageDialog(owner,
                        "No release found.", "Update check", JOptionPane.INFORMATION_MESSAGE)
                    return
                }
                val htmlUrl = release.optString("html_url", "")

                if (!isNewer(newTag, currentVersion)) {
                    if (!silent) JOptionPane.showMessageDialog(owner,
                        "GitNarwhal $currentVersion is up to date.",
                        "No updates", JOptionPane.INFORMATION_MESSAGE)
                    return
                }
                if (silent && newTag == Settings.ignoredUpdateVersion) return

                val assets = release.optJSONArray("assets")
                val asset  = when {
                    isWindows -> findAssetBySuffix(assets, "-windows-x64-setup.exe")
                    isMac     -> findAssetBySuffix(assets, "-mac-x64.dmg")
                    isLinux   -> findAssetBySuffix(assets, "-linux-x64.deb")
                    else      -> null
                }
                val installLabel = if (asset != null) "Install now" else "Open release page"

                val options = arrayOf(installLabel, "Ask me later", "Skip this version")
                val choice  = JOptionPane.showOptionDialog(
                    owner,
                    "GitNarwhal $newTag is available  (current: $currentVersion)\n\nWould you like to update?",
                    "Update available",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
                )
                when (choice) {
                    0 -> when {
                        asset != null && isWindows -> downloadAndInstallWindows(asset, owner)
                        asset != null && isMac     -> downloadAndInstallMac(asset, owner)
                        asset != null && isLinux   -> downloadAndInstallLinux(asset, owner)
                        else                       -> openBrowser(htmlUrl)
                    }
                    2 -> { Settings.ignoredUpdateVersion = newTag; Settings.save() }
                }
            }
        }.execute()
    }

    // ── Semver comparison ─────────────────────────────────────────────────────
    private fun isNewer(candidate: String, current: String): Boolean {
        val (cm, cn, cp) = parseSemver(current)   ?: return false
        val (nm, nn, np) = parseSemver(candidate) ?: return false
        return nm > cm || (nm == cm && nn > cn) || (nm == cm && nn == cn && np > cp)
    }

    private fun parseSemver(v: String): Triple<Int, Int, Int>? {
        val m = VERSION_REGEX.find(v) ?: return null
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    // ── Find release asset by filename suffix ─────────────────────────────────
    private fun findAssetBySuffix(assets: JSONArray?, suffix: String): JSONObject? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(suffix)) return a
        }
        return null
    }

    // ── Common download helper — calls onDownloaded(file) on the EDT ──────────
    private fun downloadAsset(
        asset: JSONObject,
        owner: Window?,
        suffix: String,
        onDownloaded: (File) -> Unit
    ) {
        val downloadUrl = asset.optString("browser_download_url").ifBlank { return }
        val fileSize    = asset.optLong("size", -1L)

        val pm = ProgressMonitor(owner, "Downloading update…", "", 0, 100)
        pm.millisToDecideToPopup = 500
        pm.millisToPopup         = 1000

        object : SwingWorker<File?, Int>() {
            override fun doInBackground(): File? {
                return try {
                    val tmp = File.createTempFile("gitnarwhal_update_", suffix)
                    val conn = URL(downloadUrl).openConnection()
                    conn.connect()
                    conn.getInputStream().use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf  = ByteArray(8192)
                            var read = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                output.write(buf, 0, n)
                                read += n
                                if (fileSize > 0) publish((read * 100L / fileSize).toInt())
                            }
                        }
                    }
                    tmp
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(owner, "Download failed:\n${e.message}")
                    null
                }
            }

            override fun process(chunks: List<Int>) {
                pm.setProgress(chunks.last())
                if (pm.isCanceled) cancel(true)
            }

            override fun done() {
                pm.close()
                val file = try { get() } catch (_: Exception) { null } ?: return
                onDownloaded(file)
            }
        }.execute()
    }

    // ── Windows: run Inno Setup installer silently ────────────────────────────
    private fun downloadAndInstallWindows(asset: JSONObject, owner: Window?) {
        downloadAsset(asset, owner, ".exe") { file ->
            ProcessBuilder(file.absolutePath, "/SILENT", "/FORCECLOSEAPPLICATIONS", "/RESTARTAPPLICATIONS")
                .start()
            System.exit(0)
        }
    }

    // ── macOS: mount DMG, copy .app to /Applications, unmount, relaunch ──────
    private fun downloadAndInstallMac(asset: JSONObject, owner: Window?) {
        downloadAsset(asset, owner, ".dmg") { dmg ->
            Thread {
                try {
                    val mountDir = Files.createTempDirectory("gitnarwhal_mount_").toFile()

                    ProcessBuilder(
                        "hdiutil", "attach", dmg.absolutePath,
                        "-nobrowse", "-quiet", "-mountpoint", mountDir.absolutePath
                    ).start().waitFor()

                    val appSrc = mountDir.listFiles()?.firstOrNull { it.name.endsWith(".app") }
                        ?: File(mountDir, "GitNarwhal.app")
                    val appDest = "/Applications/GitNarwhal.app"

                    // Try direct copy; escalate via osascript if permission denied
                    val copyOk = ProcessBuilder("cp", "-Rf", appSrc.absolutePath, appDest)
                        .start().waitFor() == 0
                    if (!copyOk) {
                        val script = "do shell script \"cp -Rf '${appSrc.absolutePath}' '$appDest'\" with administrator privileges"
                        ProcessBuilder("osascript", "-e", script).start().waitFor()
                    }

                    ProcessBuilder("hdiutil", "detach", mountDir.absolutePath, "-quiet").start().waitFor()
                    mountDir.deleteRecursively()

                    ProcessBuilder("open", appDest).start()
                    System.exit(0)
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(owner, "Installation failed:\n${e.message}")
                    }
                }
            }.start()
        }
    }

    // ── Linux: install .deb via pkexec dpkg -i, then relaunch ────────────────
    private fun downloadAndInstallLinux(asset: JSONObject, owner: Window?) {
        downloadAsset(asset, owner, ".deb") { deb ->
            Thread {
                try {
                    val hasDpkg   = ProcessBuilder("which", "dpkg").start().waitFor() == 0
                    val hasPkexec = ProcessBuilder("which", "pkexec").start().waitFor() == 0

                    if (!hasDpkg) {
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(owner,
                                "dpkg not found — install manually:\nsudo dpkg -i ${deb.absolutePath}",
                                "Cannot install", JOptionPane.WARNING_MESSAGE)
                        }
                        return@Thread
                    }

                    val cmd = if (hasPkexec) listOf("pkexec", "dpkg", "-i", deb.absolutePath)
                              else           listOf("sudo",   "dpkg", "-i", deb.absolutePath)
                    val exitCode = ProcessBuilder(cmd)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start().waitFor()

                    if (exitCode == 0) {
                        ProcessBuilder("/usr/bin/gitnarwhal").start()
                        System.exit(0)
                    } else {
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(owner,
                                "Installation failed — install manually:\nsudo dpkg -i ${deb.absolutePath}",
                                "Install failed", JOptionPane.ERROR_MESSAGE)
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(owner, "Installation failed:\n${e.message}")
                    }
                }
            }.start()
        }
    }

    // ── Open release page in default browser ─────────────────────────────────
    private fun openBrowser(url: String) {
        try {
            Desktop.getDesktop().browse(URI.create(url))
        } catch (_: Exception) {
            JOptionPane.showMessageDialog(null, "Open in browser:\n$url")
        }
    }
}

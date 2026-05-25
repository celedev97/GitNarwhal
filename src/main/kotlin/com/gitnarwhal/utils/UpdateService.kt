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
import java.time.Duration
import java.util.Properties
import javax.swing.JOptionPane
import javax.swing.ProgressMonitor
import javax.swing.SwingWorker

object UpdateService {

    private const val GITHUB_OWNER = "fc-dev"
    private const val GITHUB_REPO  = "GitNarwhal"
    private const val API_URL      =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val VERSION_REGEX = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

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
        if (!VERSION_REGEX.containsMatchIn(currentVersion)) {
            // dev build — skip
            return
        }

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
                val release = try { get() } catch (_: Exception) { null } ?: return
                val newTag  = release.optString("tag_name", "") .ifBlank { return }
                val htmlUrl = release.optString("html_url", "")

                if (newTag == Settings.ignoredUpdateVersion) return
                if (!isNewer(newTag, currentVersion))        return

                val isWindows    = System.getProperty("os.name", "").lowercase().contains("win")
                val winAsset     = if (isWindows) findWindowsAsset(release.optJSONArray("assets")) else null
                val installLabel = if (winAsset != null) "Install now" else "Open release page"

                val options = arrayOf(installLabel, "Ask me later", "Skip this version")
                val choice  = JOptionPane.showOptionDialog(
                    owner,
                    "GitNarwhal $newTag is available  (current: $currentVersion)\n\n" +
                    "Would you like to update?",
                    "Update available",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
                )
                when (choice) {
                    0 -> if (winAsset != null) downloadAndInstall(winAsset, owner)
                         else openBrowser(htmlUrl)
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

    // ── Find Windows installer asset ──────────────────────────────────────────
    private fun findWindowsAsset(assets: JSONArray?): JSONObject? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith("-windows-x64-setup.exe")) return a
        }
        return null
    }

    // ── Download installer and run silently ───────────────────────────────────
    private fun downloadAndInstall(asset: JSONObject, owner: Window?) {
        val downloadUrl = asset.optString("browser_download_url").ifBlank { return }
        val fileSize    = asset.optLong("size", -1L)

        val pm = ProgressMonitor(owner, "Downloading update…", "", 0, 100)
        pm.millisToDecideToPopup   = 500
        pm.millisToPopup           = 1000

        object : SwingWorker<File?, Int>() {
            override fun doInBackground(): File? {
                return try {
                    val tmp = File.createTempFile("gitnarwhal_update_", ".exe")
                    val conn = URL(downloadUrl).openConnection()
                    conn.connect()
                    conn.getInputStream().use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf     = ByteArray(8192)
                            var read    = 0L
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
                // Launch installer silently, then exit — installer will restart the app
                ProcessBuilder(file.absolutePath, "/SILENT", "/FORCECLOSEAPPLICATIONS", "/RESTARTAPPLICATIONS")
                    .start()
                System.exit(0)
            }
        }.execute()
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

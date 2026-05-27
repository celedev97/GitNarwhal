package com.gitnarwhal.backend

import com.gitnarwhal.utils.Command
import com.gitnarwhal.utils.GitDownloader
import com.gitnarwhal.utils.OS

class Git(val repo: String) {
    companion object {
        private const val INTERNAL_GIT = "./git/bin/git"

        private val WHERE = Command.find("git")

        val GIT: String = when {
            //if where/which gives a result then git is in PATH
            WHERE != null -> WHERE.toString()

            //if the internal git exists, use that
            java.io.File(INTERNAL_GIT + OS.EXE).exists() -> INTERNAL_GIT + OS.EXE

            //otherwise download it
            else -> when (OS.CURRENT) {
                OS.WINDOWS -> { GitDownloader.downloadWindowsGit(); INTERNAL_GIT + OS.EXE }
                OS.LINUX   -> GitDownloader.downloadLinuxGit()
                OS.MAC     -> GitDownloader.downloadMacGit()
            }
        }
    }

    private fun git(vararg command: String, prependGit: Boolean = true): Command {
        var realCommand = command
        if (prependGit) {
            val list = realCommand.toMutableList()
            list.add(0, GIT)
            realCommand = list.toTypedArray()
        }
        return Command(*realCommand, path = repo).execute()
    }

    //region read
    fun currentBranch() = git("branch", "--show-current")
    fun revParse(ref: String) = git("rev-parse", ref)
    fun status()   = git("status", "--porcelain=v1", "-b")
    fun statusReadable() = git("status")
    fun fetch()    = git("fetch", "--all", "--prune")
    fun branches() = git("branch", "--all", "-vv")
    fun tags()     = git("tag", "--list")
    /**
     * Returns one record per commit.
     * Format (fields separated by , records separated by ):
     *   hash | parentHashes | shortHash | author | authorDateUnix
     *       | committer | committerDateUnix | subject
     *
     * Using ASCII control chars as delimiters avoids conflicts with content.
     * Parsed by RepoTab.applyCommits() to pre-populate Commit metadata
     * without per-commit git-show calls on the EDT.
     */
    fun log() = git(
        "--no-pager", "log", "--all", "--date=unix",
        "--pretty=format:%x1E%H%x1F%P%x1F%h%x1F%aN <%aE>%x1F%ad%x1F%cN <%cE>%x1F%cd%x1F%s%x1F%D"
    )
    /** Files changed in a commit: output is "STATUS\tpath" lines (M/A/D/R/C…). */
    fun commitFiles(hash: String) = git("diff-tree", "--no-commit-id", "-r", "--name-status", hash)
    /** Diff of a single file as it appears in a commit. */
    fun showFileDiff(hash: String, path: String) = git("--no-pager", "show", "--format=", hash, "--", path)
    /** Reverse-apply a patch (undo a commit's change to a file in working copy). */
    fun applyPatchReverse(patch: String) = applyPatch(patch, cached = false, reverse = true)

    fun diff(path: String? = null) = if (path != null) git("--no-pager", "diff", "--", path) else git("--no-pager", "diff")
    fun diffStaged(path: String? = null) = if (path != null) git("--no-pager", "diff", "--cached", "--", path) else git("--no-pager", "diff", "--cached")
    fun remoteUrl(remote: String = "origin") = git("config", "--get", "remote.$remote.url")
    fun remoteList()                         = git("remote")
    fun remoteAdd(name: String, url: String) = git("remote", "add", name, url)
    fun remoteSetUrl(name: String, url: String) = git("remote", "set-url", name, url)
    fun remoteRemove(name: String)           = git("remote", "remove", name)
    fun configGet(key: String)               = git("config", "--get", key)
    fun configGetGlobal(key: String)         = git("config", "--global", "--get", key)
    fun configSet(key: String, value: String) = git("config", key, value)
    fun configSetGlobal(key: String, value: String) = git("config", "--global", key, value)
    fun configUnset(key: String)             = git("config", "--unset", key)

    /** Number of local commits not yet pushed to upstream (returns "0" on failure). */
    fun unpushedCount() = git("rev-list", "--count", "@{u}..HEAD")
    /** Number of upstream commits not yet pulled (returns "0" on failure). */
    fun unpulledCount() = git("rev-list", "--count", "HEAD..@{u}")

    fun show(commit: Commit) = show(commit.hash)
    fun show(commitHash: String) =
        git("--no-pager", "show", commitHash, "-s",
            "--pretty=format:" + arrayOf("%h", "%aN <%aE>", "%ad", "%cN <%cE>", "%cd", "%s", "%b").joinToString("%n"),
            "--date=unix")
    //endregion

    //region branch / checkout
    fun selectBranch(branch: String)        = git("checkout", branch)
    fun createBranch(branch: String)        = git("checkout", "-b", branch)
    fun createBranchFrom(branch: String, startPoint: String) = git("checkout", "-b", branch, startPoint)
    fun deleteBranch(branch: String, force: Boolean = false) =
        if (force) git("branch", "-D", branch) else git("branch", "-d", branch)
    fun renameBranch(old: String, new: String) = git("branch", "-m", old, new)
    //endregion

    //region staging
    fun add(fileName: String)     = git("add", "--", fileName)
    fun addAll()                  = git("add", "-A")
    fun restore(fileName: String) = git("restore", "--", fileName)
    fun unstage(fileName: String) = git("restore", "--staged", "--", fileName)
    fun unstageAll()              = git("restore", "--staged", ".")

    /** Apply a unified diff patch. Pass [cached]=true to stage, [reverse]=true to undo. */
    fun applyPatch(patch: String, cached: Boolean = false, reverse: Boolean = false): Command {
        val tmp = java.io.File.createTempFile("gitnarwhal_", ".patch")
        return try {
            tmp.writeText(patch)
            val args = mutableListOf("apply", "--whitespace=nowarn")
            if (cached)  args += "--cached"
            if (reverse) args += "--reverse"
            args += tmp.absolutePath
            git(*args.toTypedArray())
        } finally {
            tmp.delete()
        }
    }
    //endregion

    //region commit / sync
    fun commit(message: String)   = git("commit", "-m", message)
    fun commitAmend(message: String? = null) =
        if (message != null) git("commit", "--amend", "-m", message) else git("commit", "--amend", "--no-edit")

    fun push(remote: String = "origin", branch: String? = null, force: Boolean = false): Command {
        val parts = mutableListOf("push", remote)
        if (branch != null) parts += branch
        if (force) parts += "--force-with-lease"
        return git(*parts.toTypedArray())
    }

    fun pull(remote: String = "origin", branch: String? = null, rebase: Boolean = false): Command {
        val parts = mutableListOf("pull")
        if (rebase) parts += "--rebase"
        parts += remote
        if (branch != null) parts += branch
        return git(*parts.toTypedArray())
    }

    fun merge(branch: String)  = git("merge", branch)
    fun rebase(onto: String)   = git("rebase", onto)
    fun rebaseAbort()          = git("rebase", "--abort")
    fun rebaseContinue()       = git("rebase", "--continue")
    fun cherryPick(hash: String) = git("cherry-pick", hash)
    fun revert(hash: String)   = git("revert", "--no-edit", hash)
    fun reset(target: String, mode: String = "mixed") = git("reset", "--$mode", target)
    //endregion

    //region stash / tag
    fun stashList()                       = git("stash", "list")
    fun stashPush(message: String? = null) =
        if (message != null) git("stash", "push", "-m", message) else git("stash", "push")
    fun stashPop(index: Int = 0)          = git("stash", "pop", "stash@{$index}")
    fun stashDrop(index: Int = 0)         = git("stash", "drop", "stash@{$index}")
    fun stashApply(index: Int = 0)        = git("stash", "apply", "stash@{$index}")

    fun tagCreate(name: String, message: String? = null) =
        if (message != null) git("tag", "-a", name, "-m", message) else git("tag", name)

    fun tagCreateAt(
        name: String, hash: String,
        message: String? = null,
        force: Boolean = false,
        lightweight: Boolean = false
    ): Command {
        val args = mutableListOf("tag")
        if (force) args += "-f"
        if (!lightweight) {
            args += "-a"
            args += "-m"; args += (message?.ifBlank { null } ?: name)
        }
        args += name; args += hash
        return git(*args.toTypedArray())
    }

    fun tagDelete(name: String)                          = git("tag", "-d", name)
    fun pushTag(remote: String = "origin", tag: String)  = git("push", remote, tag)
    fun pushDeleteTag(remote: String, tag: String)       = git("push", remote, "--delete", tag)
    //endregion

    //region init / clone (static-style helpers — no repo context required)
    object Static {
        fun init(path: String) = Command(GIT, "init", path = path).execute()
        fun clone(url: String, dest: String) = Command(GIT, "clone", url, dest).execute()
    }
    //endregion
}

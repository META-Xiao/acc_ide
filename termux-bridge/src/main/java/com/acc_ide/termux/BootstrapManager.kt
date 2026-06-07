package com.acc_ide.termux

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Extracts the Termux bootstrap with custom com.acc_ide prefix.
 *
 * The bootstrap is compiled from source (termux-packages) with
 * TERMUX_APP__PACKAGE_NAME=com.acc_ide, so all paths are already correct.
 *
 * Steps:
 * 1. Delete old $PREFIX and $PREFIX-staging
 * 2. Extract zip to $PREFIX-staging
 * 3. Set POSIX permissions (0700 dirs, 0500 exec, 0400 libs)
 * 4. Create symlinks from SYMLINKS.txt
 * 5. Create $HOME, $TMPDIR, ~/.bashrc
 * 6. Atomically rename staging to $PREFIX
 */
object BootstrapManager {

    private const val TAG = "termux-bridge"
    private const val ASSET_NAME = "bootstrap-aarch64.zip"
    // Increment this when the extraction/fix logic changes to force re-extraction
    const val BOOTSTRAP_VERSION = 7

    private val stagingDir: String get() = TermuxPaths.PREFIX + "-staging"

    fun ensureInstalled(context: Context) {
        if (TermuxPaths.isInstalled && isBootstrapCurrent()) {
            Log.d(TAG, "Bootstrap already installed at ${TermuxPaths.PREFIX} (v$BOOTSTRAP_VERSION)")
            return
        }

        if (TermuxPaths.isInstalled && !isBootstrapCurrent()) {
            Log.d(TAG, "Bootstrap version mismatch, re-extracting...")
        }

        try {
            // Step 1: forcefully remove old prefix and staging
            deleteForcefully(File(stagingDir))
            deleteForcefully(TermuxPaths.PREFIX_FILE)
            // Also clean up old wrong path from earlier versions
            deleteForcefully(File("/data/data/com.acc_ide/files/termux"))

            // Step 2: extract to staging
            File(stagingDir).mkdirs()
            extractFromAssets(context)

            // Bootstrap is compiled with TERMUX_APP__PACKAGE_NAME=com.acc_ide,
            // so all paths (text files, ELF .rodata, shebangs) are already correct.
            // No path fixing or second-stage blocking needed.

            // Step 4: permissions
            fixPermissions()

            // Step 5: symlinks
            createSymlinks()

            // Step 6: create home + tmp + .bashrc
            TermuxPaths.HOME_FILE.mkdirs()
            File(TermuxPaths.TMP).mkdirs()
            createBashrc()

            // Step 7: atomic rename staging to prefix
            val staging = File(stagingDir)
            val prefix = TermuxPaths.PREFIX_FILE
            if (prefix.exists()) {
                deleteForcefully(prefix)
            }
            if (!staging.renameTo(prefix)) {
                throw RuntimeException(
                    "renameTo failed. staging=$stagingDir exists=${staging.exists()}, " +
                    "prefix=${TermuxPaths.PREFIX} exists=${prefix.exists()}"
                )
            }

            // Write version sentinel so we know this extraction is current
            File(TermuxPaths.PREFIX, ".bootstrap_version").writeText("$BOOTSTRAP_VERSION")
            Log.d(TAG, "Bootstrap installed successfully (v$BOOTSTRAP_VERSION)")
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap install failed: ${e.message}", e)
            deleteForcefully(File(stagingDir))
        }
    }

    /** Check if the installed bootstrap version matches the current code. */
    private fun isBootstrapCurrent(): Boolean {
        val sentinel = File(TermuxPaths.PREFIX, ".bootstrap_version")
        return sentinel.exists() && sentinel.readText().trim() == "$BOOTSTRAP_VERSION"
    }

    private fun extractFromAssets(context: Context) {
        var count = 0
        val buffer = ByteArray(8192)
        context.assets.open(ASSET_NAME).buffered().use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = File(stagingDir, entry.name)
                    if (entry.isDirectory) {
                        if (!target.mkdirs() && !target.isDirectory) {
                            throw RuntimeException("Failed to create directory: ${target.absolutePath}")
                        }
                    } else {
                        target.parentFile?.let { parent ->
                            if (!parent.mkdirs() && !parent.isDirectory) {
                                throw RuntimeException("Failed to create parent: ${parent.absolutePath}")
                            }
                        }
                        FileOutputStream(target).use { out ->
                            var n: Int
                            while (zis.read(buffer).also { n = it } != -1) {
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    count++
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        Log.d(TAG, "Bootstrap extracted: $count entries")
    }

    /**
     * Set correct POSIX permissions on the extracted staging tree.
     *
     * Android 10+ enforces W^X: executables on writable filesystems MUST NOT
     * be writable, or execve() returns EACCES even when SELinux allows it.
     *
     * - Directories: 0700 (rwx)
     * - Executable files: 0500 (r-x), NO write bit
     * - Regular .so libraries: 0400 (r--)
     */
    private fun fixPermissions() {
        val staging = File(stagingDir)
        val execPrefixes = listOf("bin/", "libexec/", "lib/apt/apt-helper", "lib/apt/methods")
        val interpreterPrefixes = listOf("lib/ld-android.so", "lib/ld-linux")
        var execCount = 0
        var dirCount = 0
        var libCount = 0

        walkAll(staging) { file ->
            if (file.isDirectory) {
                Os.chmod(file.absolutePath, OsConstants.S_IRWXU) // 0700
                dirCount++
            } else {
                val rel = file.relativeTo(staging).path
                if (execPrefixes.any { rel.startsWith(it) } ||
                    interpreterPrefixes.any { rel.startsWith(it) }) {
                    Os.chmod(file.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IXUSR) // 0500
                    execCount++
                } else if (rel.startsWith("lib/") && rel.endsWith(".so")) {
                    Os.chmod(file.absolutePath, OsConstants.S_IRUSR) // 0400
                    libCount++
                }
            }
        }
        Log.d(TAG, "Permissions set: $dirCount dirs + $execCount exec + $libCount libs")
    }

    /** Walk files AND directories. */
    private fun walkAll(dir: File, action: (File) -> Unit) {
        action(dir)
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) walkAll(child, action) else action(child)
        }
    }

    /**
     * Create a minimal ~/.bashrc so /etc/profile can source it.
     * The system /etc/bash.bashrc provides PS1, aliases, etc.
     */
    private fun createBashrc() {
        val bashrc = File(TermuxPaths.HOME, ".bashrc")
        if (bashrc.exists()) return
        bashrc.writeText(
            "# ACC IDE Termux bashrc\n" +
            "# /etc/profile and /etc/bash.bashrc handle env setup\n"
        )
        Log.d(TAG, "Created ~/.bashrc")
    }

    private fun createSymlinks() {
        val symlinksFile = File(stagingDir, "SYMLINKS.txt")
        if (!symlinksFile.exists()) {
            Log.w(TAG, "SYMLINKS.txt not found")
            return
        }
        var count = 0
        BufferedReader(InputStreamReader(symlinksFile.inputStream())).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("←") // ← arrow character
                if (parts.size != 2) throw RuntimeException("Malformed symlink: $line")
                val target = parts[0]
                val linkFile = File(stagingDir, parts[1])
                linkFile.parentFile?.mkdirs()
                Os.symlink(target, linkFile.absolutePath)
                count++
            }
        }
        Log.d(TAG, "Symlinks created: $count")
    }

    /** Delete a file/directory tree, forcing writable beforehand if needed. */
    private fun deleteForcefully(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    deleteForcefully(child)
                } else {
                    child.setWritable(true)
                    child.delete()
                    if (child.exists()) {
                        Log.w(TAG, "Could not delete: ${child.absolutePath}")
                    }
                }
            }
        }
        file.setWritable(true)
        val deleted = file.delete()
        if (!deleted && file.exists()) {
            Log.w(TAG, "Could not delete: ${file.absolutePath}")
        }
    }

}

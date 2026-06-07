package com.acc_ide.termux

import android.os.Build
import java.io.File

/**
 * Termux filesystem paths for the ACC IDE Termux environment.
 *
 * Mirrors the structure of a standard Termux installation:
 *   $PREFIX = /data/data/com.acc_ide/files/termux/usr
 *   $HOME   = /data/data/com.acc_ide/files/termux/home
 */
object TermuxPaths {

    /** $PREFIX — Termux's /usr equivalent. Bootstrap zip content goes here. */
    val PREFIX: String = "/data/data/com.acc_ide/files/usr"

    /** $HOME — user home directory. */
    val HOME: String = "/data/data/com.acc_ide/files/home"

    /** Binaries (bash, apt, clang, python, etc.). */
    val BIN: String = "$PREFIX/bin"

    /** Libraries. */
    val LIB: String = "$PREFIX/lib"

    /** Temporary directory. */
    val TMP: String = "$PREFIX/tmp"

    /** etc (profile, apt configs). */
    val ETC: String = "$PREFIX/etc"

    val PREFIX_FILE: File get() = File(PREFIX)
    val HOME_FILE: File get() = File(HOME)
    val BIN_FILE: File get() = File(BIN)

    /** True once bootstrap has been extracted AND bash is executable. */
    val isInstalled: Boolean get() {
        val bash = File(BIN, "bash")
        return bash.exists() && bash.canExecute()
    }

    /**
     * True when the installed bootstrap version matches the expected version.
     * Used to trigger automatic re-extraction when fix logic changes.
     */
    fun needsReinstall(expectedVersion: Int): Boolean {
        if (!isInstalled) return true
        val sentinel = File(PREFIX, ".bootstrap_version")
        return !sentinel.exists() || sentinel.readText().trim() != "$expectedVersion"
    }

    /**
     * Map Android ABI to Termux architecture name for bootstrap download.
     * e.g. "arm64-v8a" → "aarch64"
     */
    val arch: String get() = when (Build.SUPPORTED_ABIS.getOrNull(0)) {
        "arm64-v8a"   -> "aarch64"
        "armeabi-v7a" -> "arm"
        "x86_64"      -> "x86_64"
        "x86"         -> "i686"
        else          -> throw IllegalStateException("Unsupported ABI: ${Build.SUPPORTED_ABIS.contentToString()}")
    }

}

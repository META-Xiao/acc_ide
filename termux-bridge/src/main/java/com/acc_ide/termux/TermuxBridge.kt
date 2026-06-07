package com.acc_ide.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TermuxBridge(
    private val context: Context,
    private val shellPath: String = DEFAULT_SHELL,
    private val cwd: String = DEFAULT_CWD,
    private val env: Array<String> = emptyArray(),
    private val args: Array<String> = emptyArray()
) {
    private val tag = "termux-bridge"

    var session: TerminalSession? = null
        private set
    private var terminalView: TerminalView? = null
    private var bridgeClient: BridgeSessionClient? = null

    val isRunning: Boolean get() = session?.isRunning == true

    fun initialize(rows: Int, cols: Int, cellWidthPx: Int = 9, cellHeightPx: Int = 18) {
        if (isRunning) {
            Log.w(tag, "initialize: session already running, skipping")
            return
        }
        Log.d(tag, "initialize: shell=$shellPath cwd=$cwd rows=$rows cols=$cols")
        if (env.isNotEmpty()) Log.d(tag, "env: ${env.joinToString(" ")}")

        val client = BridgeSessionClient()
        bridgeClient = client
        session = TerminalSession(shellPath, cwd, args, env, null, client)
        session!!.updateSize(cols, rows, cellWidthPx, cellHeightPx)
        Log.d(tag, "initialize: running=$isRunning")
    }

    fun createView(): TerminalView {
        val s = session ?: throw IllegalStateException("Call initialize() before createView()")
        val view = TerminalView(context, null)
        terminalView = view

        bridgeClient?.attachView(view)
        view.setTerminalViewClient(BridgeViewClient())
        view.attachSession(s)
        view.setTextSize(DEFAULT_TEXT_SIZE_SP)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        Log.d(tag, "createView: done")
        return view
    }

    fun write(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        session?.write(data, offset, count)
    }

    fun writeText(text: String) {
        val normalized = text + if (text.endsWith("\n")) "\r" else "\r\n"
        session?.write(normalized)
    }

    fun destroy() {
        Log.d(tag, "destroy")
        session?.finishIfRunning()
        session = null
        terminalView = null
    }

    fun applyColors(bgColor: Int, fgColor: Int, cursorColor: Int) {
        val cs = TerminalColors.COLOR_SCHEME
        cs.mDefaultColors[COLOR_IDX_FG] = fgColor
        cs.mDefaultColors[COLOR_IDX_BG] = bgColor
        cs.mDefaultColors[COLOR_IDX_CURSOR] = cursorColor

        val s = session ?: return
        arrayOf(oscColor(10, fgColor), oscColor(11, bgColor), oscColor(12, cursorColor))
            .forEach { s.write(it, 0, it.size) }
    }

    fun resize(rows: Int, cols: Int, cellWidthPx: Int = 9, cellHeightPx: Int = 18) {
        session?.updateSize(cols, rows, cellWidthPx, cellHeightPx)
    }

    // ---- internal ----

    private inner class BridgeSessionClient : TerminalSessionClient {
        private var view: TerminalView? = null
        fun attachView(v: TerminalView) { view = v }

        override fun onTextChanged(changedSession: TerminalSession) {
            view?.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            // Log terminal transcript to debug why the shell exited
            val emulator = finishedSession.getEmulator()
            val transcript = emulator?.getScreen()?.getTranscriptTextWithFullLinesJoined() ?: "(null)"
            Log.d(tag, "session finished: exit=${finishedSession.getExitStatus()}")
            Log.d(tag, "transcript:\n$transcript")
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("termux", text))
            Log.d(tag, "copy: ${text.take(80)}")
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val s = session ?: return
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            val text = clip.getItemAt(0)?.text?.toString() ?: return
            s.write(text)
            Log.d(tag, "paste: ${text.take(80)}")
        }

        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String, msg: String) { Log.e("$this@TermuxBridge.tag:$tag", msg) }
        override fun logWarn(tag: String, msg: String)  { Log.w("$this@TermuxBridge.tag:$tag", msg) }
        override fun logInfo(tag: String, msg: String)   { Log.i("$this@TermuxBridge.tag:$tag", msg) }
        override fun logDebug(tag: String, msg: String)  { Log.d("$this@TermuxBridge.tag:$tag", msg) }
        override fun logVerbose(tag: String, msg: String){}
        override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) { Log.e("$this@TermuxBridge.tag:$tag", msg, e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e("$this@TermuxBridge.tag:$tag", "${e.message}", e) }
    }

    private inner class BridgeViewClient : TerminalViewClient {
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
            // Log printable characters only, for debugging
            Log.d(tag, "input: cp=$codePoint ctrl=$ctrlDown")
            return false // let TerminalView handle it
        }

        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
            if (keyCode == KeyEvent.KEYCODE_ENTER) Log.d(tag, "input: ENTER")
            return false
        }
        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
        override fun onEmulatorSet() {}
        override fun onSingleTapUp(e: MotionEvent) {}
        override fun onLongPress(event: MotionEvent): Boolean = false
        override fun onScale(scale: Float): Float = scale
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = false
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun readControlKey(): Boolean = false
        override fun readAltKey(): Boolean = false
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun logError(tag: String, msg: String) {}
        override fun logWarn(tag: String, msg: String) {}
        override fun logInfo(tag: String, msg: String) {}
        override fun logDebug(tag: String, msg: String) {}
        override fun logVerbose(tag: String, msg: String) {}
        override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    private fun oscColor(param: Int, colorArgb: Int): ByteArray {
        val r = Color.red(colorArgb); val g = Color.green(colorArgb); val b = Color.blue(colorArgb)
        val r4 = String.format("%04x", (r shl 8) or r)
        val g4 = String.format("%04x", (g shl 8) or g)
        val b4 = String.format("%04x", (b shl 8) or b)
        return "]${param};rgb:${r4}/${g4}/${b4}".toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_SHELL = "/system/bin/sh"
        const val DEFAULT_CWD = "/data/data/com.acc_ide/files"
        const val DEFAULT_TEXT_SIZE_SP = 14
        private const val COLOR_IDX_FG = TextStyle.NUM_INDEXED_COLORS - 3
        private const val COLOR_IDX_BG = TextStyle.NUM_INDEXED_COLORS - 2
        private const val COLOR_IDX_CURSOR = TextStyle.NUM_INDEXED_COLORS - 1

        fun create(context: Context): TermuxBridge {
            return if (TermuxPaths.isInstalled) {
                Log.d("termux-bridge", "Bootstrap found, using Termux bash")
                TermuxBridge(
                    context = context,
                    shellPath = TermuxPaths.BIN + "/bash",
                    cwd = TermuxPaths.HOME,
                    args = arrayOf("--login"),
                    env = arrayOf(
                        "PREFIX=${TermuxPaths.PREFIX}",
                        "HOME=${TermuxPaths.HOME}",
                        "PATH=${TermuxPaths.BIN}",
                        "LD_LIBRARY_PATH=${TermuxPaths.LIB}",
                        "LD_PRELOAD=${TermuxPaths.LIB}/libtermux-exec-ld-preload.so",
                        "TERM=xterm-256color",
                        "LANG=en_US.UTF-8",
                        "TMPDIR=${TermuxPaths.TMP}",
                        "TERMUX_VERSION=0.119.0",
                        "TERMUX_APP_PACKAGE_MANAGER=apt"
                    )
                )
            } else {
                Log.d("termux-bridge", "Bootstrap not found, using system sh")
                TermuxBridge(
                    context = context,
                    shellPath = DEFAULT_SHELL,
                    cwd = context.filesDir?.absolutePath ?: DEFAULT_CWD
                )
            }
        }
    }
}

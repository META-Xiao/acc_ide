package com.acc_ide.termux

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

/**
 * Bridge between ACC IDE and the Termux terminal-emulator / terminal-view libraries.
 */
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

        val client = BridgeSessionClient()
        bridgeClient = client
        session = TerminalSession(shellPath, cwd, args, env, null, client)
        session!!.updateSize(cols, rows, cellWidthPx, cellHeightPx)

        Log.d(tag, "initialize: running=${isRunning}")
    }

    fun createView(): TerminalView {
        val s = session ?: throw IllegalStateException("Call initialize() before createView()")
        val view = TerminalView(context, null)
        terminalView = view

        // Forward session output → TerminalView.onScreenUpdated()
        bridgeClient?.attachView(view)

        view.setTerminalViewClient(BridgeViewClient())
        view.attachSession(s)
        view.setTextSize(DEFAULT_TEXT_SIZE_SP)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()

        Log.d(tag, "createView: TerminalView created, session=${s.mHandle}")
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
        Log.d(tag, "destroy: finishing session")
        session?.finishIfRunning()
        session = null
        terminalView = null
    }

    fun applyColors(bgColor: Int, fgColor: Int, cursorColor: Int) {
        Log.d(tag, "applyColors: bg=#${Integer.toHexString(bgColor)} fg=#${Integer.toHexString(fgColor)}")

        val cs = TerminalColors.COLOR_SCHEME
        cs.mDefaultColors[COLOR_IDX_FG] = fgColor
        cs.mDefaultColors[COLOR_IDX_BG] = bgColor
        cs.mDefaultColors[COLOR_IDX_CURSOR] = cursorColor

        // OSC sequences for live update on running session
        val s = session ?: return
        arrayOf(
            oscColor(10, fgColor),
            oscColor(11, bgColor),
            oscColor(12, cursorColor)
        ).forEach { s.write(it, 0, it.size) }
    }

    fun resize(rows: Int, cols: Int, cellWidthPx: Int = 9, cellHeightPx: Int = 18) {
        session?.updateSize(cols, rows, cellWidthPx, cellHeightPx)
    }

    // ---- internal ----

    private inner class BridgeSessionClient : TerminalSessionClient {
        private var view: TerminalView? = null

        fun attachView(v: TerminalView) {
            view = v
        }

        override fun onTextChanged(changedSession: TerminalSession) {
            view?.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            Log.d(tag, "title: ${changedSession.title}")
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            Log.d(tag, "session finished: exit=${finishedSession.getExitStatus()}")
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String, message: String) { Log.e("$this@TermuxBridge.tag:$tag", message) }
        override fun logWarn(tag: String, message: String) { Log.w("$this@TermuxBridge.tag:$tag", message) }
        override fun logInfo(tag: String, message: String) { Log.i("$this@TermuxBridge.tag:$tag", message) }
        override fun logDebug(tag: String, message: String) { Log.d("$this@TermuxBridge.tag:$tag", message) }
        override fun logVerbose(tag: String, message: String) { Log.v("$this@TermuxBridge.tag:$tag", message) }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e("$this@TermuxBridge.tag:$tag", "$message", e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e("$this@TermuxBridge.tag:$tag", "${e.message}", e) }
    }

    private class BridgeViewClient : TerminalViewClient {
        override fun onEmulatorSet() {}
        override fun onSingleTapUp(e: MotionEvent) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
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
        override fun logError(tag: String, message: String) {}
        override fun logWarn(tag: String, message: String) {}
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    private fun oscColor(param: Int, colorArgb: Int): ByteArray {
        val r = Color.red(colorArgb)
        val g = Color.green(colorArgb)
        val b = Color.blue(colorArgb)
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
    }
}

package com.acc_ide.ui.shell

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.method.ScrollingMovementMethod
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.acc_ide.R

/**
 * Shell / Terminal fragment — embedded terminal UI like a file tab.
 *
 * Background and foreground colors match the code editor's TextMate theme
 * (light.json / dark.json). Uses the same Agave Nerd Font Mono as the editor.
 * Tap anywhere to bring up the keyboard; typed text appears inline with a
 * `$ ` prompt and a thin `|` cursor. The hamburger drawer stays accessible.
 */
class ShellFragment : Fragment() {

    private lateinit var outputView: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var hiddenInput: EditText

    private val historyLines = mutableListOf<String>()
    private var sessionConnected = false
    private var typeface: Typeface? = null
    private var cursorVisible = true
    private val blinkHandler = Handler(Looper.getMainLooper())
    private var cursorColor = 0xFFFFFFFF.toInt()
    private var bgColor = 0xFF000000.toInt()
    private var themeCallback: android.content.ComponentCallbacks? = null

    // T = 500ms, PWM = 0.5 → on 250ms, off 250ms
    private val blinkRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            refreshDisplay()
            blinkHandler.postDelayed(this, 250)
        }
    }

    companion object {
        private const val PROMPT = "$ "
        private const val CURSOR = "|"

        @JvmStatic
        fun newInstance() = ShellFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shell, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        outputView = view.findViewById(R.id.shell_output)
        outputScroll = view.findViewById(R.id.shell_output_scroll)
        hiddenInput = view.findViewById(R.id.shell_hidden_input)

        typeface = try {
            Typeface.createFromAsset(requireContext().assets, "fonts/AgaveNerdFontMono-Regular.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
        outputView.typeface = typeface
        outputView.movementMethod = ScrollingMovementMethod()

        loadThemeColors()
        historyLines.add(getString(R.string.shell_welcome))

        setupListeners()
        refreshDisplay()
        startCursorBlink()
        registerThemeListener()
    }

    private fun loadThemeColors() {
        cursorColor = ContextCompat.getColor(requireContext(), R.color.shell_cursor)
        bgColor = ContextCompat.getColor(requireContext(), R.color.shell_background)
    }

    private fun registerThemeListener() {
        themeCallback = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val oldUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (oldUiMode != newUiMode) {
                    loadThemeColors()
                    view?.setBackgroundColor(bgColor)
                    refreshDisplay()
                }
            }

            override fun onLowMemory() {}
        }
        requireActivity().registerComponentCallbacks(themeCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        themeCallback?.let { requireActivity().unregisterComponentCallbacks(it) }
        stopCursorBlink()
    }

    private fun setupListeners() {
        val tapListener = View.OnClickListener {
            hiddenInput.requestFocus()
            showKeyboard()
        }
        outputView.setOnClickListener(tapListener)
        outputScroll.setOnClickListener(tapListener)

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                cursorVisible = true
                refreshDisplay()
            }
        })

        hiddenInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                sendCommand()
                true
            } else {
                false
            }
        }

        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendCommand()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadThemeColors()
        view?.setBackgroundColor(bgColor)
        startCursorBlink()
        view?.postDelayed({
            hiddenInput.requestFocus()
            showKeyboard()
        }, 200)
    }

    override fun onPause() {
        super.onPause()
        stopCursorBlink()
    }

    private fun sendCommand() {
        val cmd = hiddenInput.text.toString()
        hiddenInput.text?.clear()

        historyLines.add("$PROMPT$cmd")

        if (cmd.isNotBlank()) {
            // TODO: delegate to Termux PTY session once Phase 3 is integrated
            when (cmd.trim().lowercase()) {
                "help" -> historyLines.add(
                    "clear    — Clear terminal\n" +
                    "status   — Show session status\n" +
                    "connect  — Connect to Termux backend\n" +
                    "exit     — Close session\n" +
                    "help     — This message"
                )
                "clear" -> historyLines.clear()
                "status" -> {
                    val status = if (sessionConnected) "connected" else "disconnected"
                    historyLines.add("Session: $status\nBackend: Termux (not yet integrated)")
                }
                "connect" -> {
                    sessionConnected = true
                    historyLines.add("[system] Connected to Termux backend.")
                }
                "exit" -> {
                    sessionConnected = false
                    historyLines.add("[system] Session closed.")
                }
                else -> historyLines.add("sh: ${cmd.trim()}: command not found")
            }
        }

        refreshDisplay()
        scrollToBottom()
    }

    private fun refreshDisplay() {
        val typing = hiddenInput.text?.toString() ?: ""
        val liveLine = "$PROMPT$typing"

        val fullText = if (historyLines.isEmpty()) {
            liveLine
        } else {
            historyLines.joinToString("\n") + "\n" + liveLine
        }

        val cursor = if (cursorVisible) CURSOR else " "
        val spannable = SpannableString(fullText + cursor)
        if (cursorVisible) {
            spannable.setSpan(
                ForegroundColorSpan(cursorColor),
                fullText.length,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        outputView.text = spannable
    }

    private fun scrollToBottom() {
        outputScroll.post {
            outputScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(hiddenInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun startCursorBlink() {
        cursorVisible = true
        blinkHandler.removeCallbacks(blinkRunnable)
        blinkHandler.postDelayed(blinkRunnable, 250)
    }

    private fun stopCursorBlink() {
        blinkHandler.removeCallbacks(blinkRunnable)
    }
}

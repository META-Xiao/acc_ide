package com.acc_ide.ui.shell

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.acc_ide.R
import com.acc_ide.termux.TermuxBridge
import com.termux.view.TerminalView

/**
 * Shell fragment hosting a real terminal via [TermuxBridge] and [TerminalView].
 * Background / foreground match the code editor's TextMate theme.
 */
class ShellFragment : Fragment() {

    private var bridge: TermuxBridge? = null
    private var terminalView: TerminalView? = null
    private var themeCallback: android.content.ComponentCallbacks? = null

    private var bgColor = 0
    private var fgColor = 0
    private var cursorColor = 0

    companion object {
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

        loadThemeColors()
        view.setBackgroundColor(bgColor)

        val workDir = requireContext().filesDir.absolutePath
        bridge = TermuxBridge(requireContext(), cwd = workDir).also {
            it.applyColors(bgColor, fgColor, cursorColor)
            it.initialize(rows = 40, cols = 80)
        }

        terminalView = bridge!!.createView()
        (view as FrameLayout).addView(terminalView)

        registerThemeListener()
    }

    override fun onResume() {
        super.onResume()
        terminalView?.requestFocus()
        terminalView?.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        themeCallback?.let { requireActivity().unregisterComponentCallbacks(it) }
        bridge?.destroy()
        bridge = null
        terminalView = null
    }

    private fun loadThemeColors() {
        bgColor = ContextCompat.getColor(requireContext(), R.color.shell_background)
        fgColor = ContextCompat.getColor(requireContext(), R.color.shell_foreground)
        cursorColor = ContextCompat.getColor(requireContext(), R.color.shell_cursor)
    }

    private fun registerThemeListener() {
        themeCallback = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val oldUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (oldUiMode != newUiMode) {
                    loadThemeColors()
                    view?.setBackgroundColor(bgColor)
                    bridge?.applyColors(bgColor, fgColor, cursorColor)
                }
            }

            override fun onLowMemory() {}
        }
        requireActivity().registerComponentCallbacks(themeCallback)
    }
}

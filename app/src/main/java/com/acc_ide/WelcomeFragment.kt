package com.acc_ide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class WelcomeFragment : Fragment() {
    private lateinit var welcomeText: TextView
    private lateinit var startCodingButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(com.acc_ide.R.layout.fragment_welcome, container, false)

        welcomeText = view.findViewById(com.acc_ide.R.id.welcome_text)
        startCodingButton = view.findViewById(com.acc_ide.R.id.start_coding_button)

        startCodingButton.setOnClickListener {
            // 显示创建新文件的对话框
            NewFileDialogFragment().show(requireActivity().supportFragmentManager, "new_file_dialog")
        }

        return view
    }
} 
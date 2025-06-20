package com.acc_ide

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NewFileDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(com.acc_ide.R.string.new_file)
            .setItems(arrayOf(getString(com.acc_ide.R.string.cpp), getString(com.acc_ide.R.string.python), getString(com.acc_ide.R.string.java))) { _, which ->
                val language = when(which) {
                    0 -> "cpp"
                    1 -> "python"
                    2 -> "java"
                    else -> "cpp"
                }
                
                // 使用选择的语言创建新文件
                val activity = activity as MainActivity
                activity.createNewFile(language)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                dismiss()
            }
        
        return builder.create()

    }
} 
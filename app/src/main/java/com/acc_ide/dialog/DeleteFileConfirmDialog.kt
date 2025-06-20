package com.acc_ide.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.acc_ide.R

/**
 * 删除文件确认对话框
 */
class DeleteFileConfirmDialog : DialogFragment() {
    
    // 文件名
    private lateinit var fileName: String
    
    // 删除回调
    private var onDeleteConfirmed: ((String) -> Unit)? = null
    
    /**
     * 设置文件名和回调
     */
    fun setUp(fileName: String, callback: (String) -> Unit): DeleteFileConfirmDialog {
        this.fileName = fileName
        this.onDeleteConfirmed = callback
        return this
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        
        // 配置对话框
        builder.setTitle(R.string.delete_file)
            .setMessage(getString(R.string.delete_file_confirm, fileName))
            .setPositiveButton(R.string.delete) { _, _ ->
                // 调用删除回调
                onDeleteConfirmed?.invoke(fileName)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            
        return builder.create()
    }
} 
package com.acc_ide.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.acc_ide.R

/**
 * 文件重命名对话框
 */
class RenameFileDialog : DialogFragment() {
    
    // 原始文件名
    private lateinit var originalFileName: String
    
    // 重命名回调
    private var onRenameConfirmed: ((String, String) -> Unit)? = null
    
    /**
     * 设置原始文件名和回调
     */
    fun setUp(fileName: String, callback: (originalName: String, newName: String) -> Unit): RenameFileDialog {
        this.originalFileName = fileName
        this.onRenameConfirmed = callback
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        
        // 创建对话框视图
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_rename_file, null)
        
        // 获取输入框
        val inputField = view.findViewById<EditText>(R.id.rename_input)
        
        // 设置默认值为当前文件名(不含扩展名)
        val extension = originalFileName.substringAfterLast(".", "")
        val nameWithoutExtension = if (extension.isNotEmpty()) {
            originalFileName.substringBeforeLast(".")
        } else {
            originalFileName
        }
        inputField.setText(nameWithoutExtension)
        inputField.setSelection(0, nameWithoutExtension.length)

        // 配置对话框
        builder.setView(view)
            .setTitle(R.string.rename_file)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // 获取新名称
                val newName = inputField.text.toString().trim()
                
                // 验证新名称
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.file_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 添加原始扩展名
                val newFileName = if (extension.isNotEmpty()) {
                    "$newName.$extension"
                } else {
                    newName
                }
                
                // 调用回调
                onRenameConfirmed?.invoke(originalFileName, newFileName)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
        
        return builder.create()
    }
} 
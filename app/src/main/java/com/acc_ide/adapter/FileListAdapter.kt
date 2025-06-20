package com.acc_ide.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.acc_ide.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 文件列表适配器
 */
class FileListAdapter(
    private var fileList: List<String> = emptyList(),
    private val onFileClickListener: (String) -> Unit,
    private val onRenameClickListener: (String) -> Unit,
    private val onDeleteClickListener: (String) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    // 当前选中的文件位置
    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_list_item, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileName = fileList[position]
        
        // 设置文件名
        holder.fileName.text = fileName
        
        // 设置文件图标（根据文件类型可以设置不同图标）
        when {
            fileName.endsWith(".cpp") -> holder.fileIcon.setImageResource(R.drawable.file_cpp_icon)
            fileName.endsWith(".py") -> holder.fileIcon.setImageResource(R.drawable.file_python_icon)
            fileName.endsWith(".java") -> holder.fileIcon.setImageResource(R.drawable.file_java_icon)
            else -> holder.fileIcon.setImageResource(R.drawable.file_text_icon)
        }
        
        // 设置选中状态
        val isSelected = position == selectedPosition
        holder.fileItemContainer.isChecked = isSelected
        
        // 设置点击事件
        holder.fileItemContainer.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.adapterPosition
            
            // 刷新先前选中和新选中的项
            if (previousSelected != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousSelected)
            }
            notifyItemChanged(selectedPosition)
            
            onFileClickListener(fileName)
        }
        
        // 设置重命名按钮点击事件
        holder.renameButton.setOnClickListener {
            onRenameClickListener(fileName)
        }
        
        // 设置删除按钮点击事件
        holder.deleteButton.setOnClickListener {
            onDeleteClickListener(fileName)
        }
    }

    override fun getItemCount(): Int = fileList.size
    
    /**
     * 更新文件列表数据
     */
    fun updateFileList(newFileList: List<String>, currentFileName: String? = null) {
        fileList = newFileList
        
        // 如果有当前文件名，更新选中位置
        if (currentFileName != null) {
            selectedPosition = fileList.indexOf(currentFileName)
        }
        
        notifyDataSetChanged()
    }
    
    /**
     * 设置当前选中的文件
     */
    fun setSelectedFile(fileName: String) {
        val newPosition = fileList.indexOf(fileName)
        if (newPosition >= 0) {
            val previousSelected = selectedPosition
            selectedPosition = newPosition
            
            if (previousSelected != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousSelected)
            }
            notifyItemChanged(selectedPosition)
        }
    }

    /**
     * 文件视图持有者
     */
    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileItemContainer: MaterialCardView = itemView.findViewById(R.id.file_item_container)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val renameButton: MaterialButton = itemView.findViewById(R.id.file_rename_button)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.file_delete_button)
    }
} 
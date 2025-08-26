package com.acc_ide.ui.settings

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.acc_ide.R
import com.acc_ide.compiler.CompilerManager
import com.acc_ide.compiler.Language
import com.acc_ide.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * 编译器管理设置Fragment
 */
class CompilerSettingsFragment : Fragment() {
    
    private lateinit var compilerManager: CompilerManager
    private lateinit var compilerRecyclerView: RecyclerView
    private lateinit var compilerAdapter: CompilerAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_compiler_settings, container, false)
        
        compilerManager = CompilerManager(requireContext())
        
        initViews(view)
        setupRecyclerView()
        loadCompilerStatus()
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置标题
        val mainActivity = activity as? MainActivity
        mainActivity?.supportActionBar?.title = "编译器管理"
    }
    
    private fun initViews(view: View) {
        compilerRecyclerView = view.findViewById(R.id.compiler_recycler_view)
    }
    
    private fun setupRecyclerView() {
        compilerAdapter = CompilerAdapter { language: Language, action: CompilerAction ->
            when (action) {
                CompilerAction.INSTALL -> installCompiler(language)
                CompilerAction.UNINSTALL -> uninstallCompiler(language)
            }
        }
        
        compilerRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        compilerRecyclerView.adapter = compilerAdapter
    }
    
    private fun loadCompilerStatus() {
        lifecycleScope.launch {
            try {
                val compilerInfoList = Language.values().map { language ->
                    compilerManager.getCompilerInfo(language)
                }
                compilerAdapter.updateCompilers(compilerInfoList)
            } catch (e: Exception) {
                Toast.makeText(context, "加载编译器状态失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun installCompiler(language: Language) {
        lifecycleScope.launch {
            try {
                val result = compilerManager.installFromAssets(language) { progress ->
                    // 更新进度
                    lifecycleScope.launch {
                        compilerAdapter.updateProgress(language, progress)
                    }
                }
                
                if (result.success) {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    loadCompilerStatus() // 刷新状态
                } else {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun uninstallCompiler(language: Language) {
        lifecycleScope.launch {
            try {
                val result = compilerManager.uninstallCompiler(language)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                loadCompilerStatus() // 刷新状态
            } catch (e: Exception) {
                Toast.makeText(context, "卸载失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    companion object {
        @JvmStatic
        fun newInstance() = CompilerSettingsFragment()
    }
}

enum class CompilerAction {
    INSTALL, UNINSTALL
}

data class CompilerInfo(
    val language: Language,
    val isInstalled: Boolean,
    val version: String,
    val path: String
)

class CompilerAdapter(
    private val onAction: (Language, CompilerAction) -> Unit
) : RecyclerView.Adapter<CompilerAdapter.CompilerViewHolder>() {
    
    private var compilers = listOf<com.acc_ide.compiler.CompilerInfo>()
    private val progressMap = mutableMapOf<Language, Int>()
    
    fun updateCompilers(newCompilers: List<com.acc_ide.compiler.CompilerInfo>) {
        compilers = newCompilers
        notifyDataSetChanged()
    }
    
    fun updateProgress(language: Language, progress: Int) {
        progressMap[language] = progress
        val position = compilers.indexOfFirst { it.language == language }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompilerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_compiler, parent, false)
        return CompilerViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CompilerViewHolder, position: Int) {
        val compiler = compilers[position]
        val progress = progressMap[compiler.language] ?: 0
        holder.bind(compiler, progress, onAction)
    }
    
    override fun getItemCount() = compilers.size
    
    class CompilerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val languageText: TextView = itemView.findViewById(R.id.language_text)
        private val statusText: TextView = itemView.findViewById(R.id.status_text)
        private val versionText: TextView = itemView.findViewById(R.id.version_text)
        private val actionButton: Button = itemView.findViewById(R.id.action_button)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        
        fun bind(
            compiler: com.acc_ide.compiler.CompilerInfo,
            progress: Int,
            onAction: (Language, CompilerAction) -> Unit
        ) {
            languageText.text = compiler.language.name
            statusText.text = if (compiler.isInstalled) "已安装" else "未安装"
            versionText.text = compiler.version
            
            if (progress > 0 && progress < 100) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = progress
                actionButton.isEnabled = false
                actionButton.text = "$progress%"
            } else {
                progressBar.visibility = View.GONE
                actionButton.isEnabled = true
                
                if (compiler.isInstalled) {
                    actionButton.text = "卸载"
                    actionButton.setOnClickListener {
                        onAction(compiler.language, CompilerAction.UNINSTALL)
                    }
                } else {
                    actionButton.text = "安装"
                    actionButton.setOnClickListener {
                        onAction(compiler.language, CompilerAction.INSTALL)
                    }
                }
            }
        }
    }
}
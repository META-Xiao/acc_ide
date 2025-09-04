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
import com.acc_ide.compiler.AssetBuildManager
import com.acc_ide.compiler.Language
import com.acc_ide.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * 编译器管理设置Fragment
 */
class CompilerSettingsFragment : Fragment() {
    
    private lateinit var compilerManager: CompilerManager
    private lateinit var assetBuildManager: AssetBuildManager
    private lateinit var compilerRecyclerView: RecyclerView
    private lateinit var compilerAdapter: CompilerAdapter
    
    // Asset build UI components
    private lateinit var buildStatusCard: LinearLayout
    private lateinit var buildStatusText: TextView
    private lateinit var buildAssetsButton: Button
    private lateinit var cleanAssetsButton: Button
    private lateinit var buildProgressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_compiler_settings, container, false)
        
        compilerManager = CompilerManager(requireContext())
        assetBuildManager = AssetBuildManager(requireContext())
        
        initViews(view)
        setupRecyclerView()
        setupAssetBuildUI()
        loadCompilerStatus()
        loadAssetBuildStatus()
        
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
        
        // Initialize asset build UI components (will need corresponding layout updates)
        try {
            // buildStatusCard = view.findViewById(R.id.build_status_card)
            // buildStatusText = view.findViewById(R.id.build_status_text)
            // buildAssetsButton = view.findViewById(R.id.build_assets_button)
            // cleanAssetsButton = view.findViewById(R.id.clean_assets_button)  
            // buildProgressBar = view.findViewById(R.id.build_progress_bar)
            // 临时注释掉，等待布局文件更新
        } catch (e: Exception) {
            // Layout may not include these components yet
            android.util.Log.w("CompilerSettings", "Asset build UI components not found in layout")
        }
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
    
    private fun setupAssetBuildUI() {
        try {
            buildAssetsButton.setOnClickListener {
                buildAssets()
            }
            
            cleanAssetsButton.setOnClickListener {
                cleanAssets()
            }
        } catch (e: Exception) {
            // Asset build UI components may not be available yet
            android.util.Log.w("CompilerSettings", "Asset build UI setup skipped: ${e.message}")
        }
    }
    
    private fun loadAssetBuildStatus() {
        lifecycleScope.launch {
            try {
                val status = assetBuildManager.getBuildStatus()
                updateAssetBuildUI(status)
            } catch (e: Exception) {
                android.util.Log.w("CompilerSettings", "Failed to load asset build status: ${e.message}")
            }
        }
    }
    
    private fun updateAssetBuildUI(status: com.acc_ide.compiler.BuildStatus) {
        try {
            if (status.hasAssets) {
                buildStatusText.text = "资产状态: 已构建 (${status.packagesCount}个包)\n版本: ${status.version}"
                buildAssetsButton.text = "重新构建"
                cleanAssetsButton.isEnabled = true
            } else {
                buildStatusText.text = "资产状态: 未构建\n请先构建编译器资产包"
                buildAssetsButton.text = "构建资产"
                cleanAssetsButton.isEnabled = false
            }
        } catch (e: Exception) {
            android.util.Log.w("CompilerSettings", "Failed to update asset build UI: ${e.message}")
        }
    }
    
    private fun buildAssets() {
        lifecycleScope.launch {
            try {
                buildProgressBar.visibility = android.view.View.VISIBLE
                buildAssetsButton.isEnabled = false
                buildStatusText.text = "正在构建资产包..."
                
                val result = assetBuildManager.buildAllAssets()
                
                buildProgressBar.visibility = android.view.View.GONE
                buildAssetsButton.isEnabled = true
                
                if (result.success) {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    loadAssetBuildStatus() // 刷新状态
                } else {
                    Toast.makeText(context, "构建失败: ${result.message}", Toast.LENGTH_LONG).show()
                    buildStatusText.text = "构建失败: ${result.message}"
                }
                
            } catch (e: Exception) {
                buildProgressBar.visibility = android.view.View.GONE
                buildAssetsButton.isEnabled = true
                Toast.makeText(context, "构建异常: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun cleanAssets() {
        lifecycleScope.launch {
            try {
                val cleaned = assetBuildManager.cleanAssets()
                if (cleaned) {
                    Toast.makeText(context, "资产清理完成", Toast.LENGTH_SHORT).show()
                    loadAssetBuildStatus() // 刷新状态
                } else {
                    Toast.makeText(context, "没有资产需要清理", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "清理失败: ${e.message}", Toast.LENGTH_LONG).show()
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
package com.acc_ide.ui.iopanel

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.acc_ide.R
import com.acc_ide.ui.main.MainActivity
import com.acc_ide.compiler.CompilerService
import com.acc_ide.compiler.Language
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * IO panel fragment for code execution and testing
 * IO面板Fragment - 用于代码执行和测试
 */
class IOPanelFragment : Fragment() {
    private lateinit var runCodeButton: MaterialButton
    private lateinit var runStatus: TextView
    private lateinit var inputText: TextInputEditText
    private lateinit var actualOutputText: TextInputEditText
    private lateinit var expectedOutputText: TextInputEditText
    private lateinit var executionModeChipGroup: ChipGroup
    private lateinit var localModeChip: Chip
    private lateinit var cloudModeChip: Chip
    
    // 本地编译服务
    private lateinit var compilerService: CompilerService
    
    private var fileName: String = ""
    private var language: String = ""
    private var fileContent: String = ""
    
    // Execution modes
    enum class ExecutionMode {
        LOCAL, CLOUD
    }
    
    private var currentExecutionMode: ExecutionMode = ExecutionMode.CLOUD
    private var isGitHubRunning = false
    private var pollingJob: Job? = null

    // Cache for storing IO instances
    companion object {
        private const val ARG_FILENAME = "filename"
        private const val ARG_LANGUAGE = "language"
        private const val ARG_FILE_CONTENT = "file_content"
        
        // Cache IO instances for each file
        private val ioCache = mutableMapOf<String, IOInstance>()

        // GitHub related constants
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val PREF_GITHUB_REPO_URL = "github_repo_url"
        private const val PREF_GITHUB_PAT = "github_pat"
        
        // Language mapping
        private val LANGUAGE_MAP = mapOf(
            "cpp" to "cpp",
            "python" to "py",
            "java" to "java"
        )

        @JvmStatic
        /**
         * Create new instance of IOPanelFragment
         * @param fileName The name of the file to execute
         * @param language The programming language of the file
         * @param fileContent The content of the file (for local execution)
         */
        fun newInstance(fileName: String, language: String, fileContent: String = "") =
            IOPanelFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILENAME, fileName)
                    putString(ARG_LANGUAGE, language)
                    putString(ARG_FILE_CONTENT, fileContent)
                }
            }
    }
    
    /**
     * IO instance data class for caching input/output data
     * IO实例数据类 - 用于缓存输入/输出数据
     */
    data class IOInstance(
        var input: String = "",
        var actualOutput: String = "",
        var expectedOutput: String = "",
        var status: String = "",
        var executionTime: Int = 0
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_io_panel, container, false)
        
        // Get arguments
        arguments?.let {
            fileName = it.getString(ARG_FILENAME, "")
            language = it.getString(ARG_LANGUAGE, "")
            fileContent = it.getString(ARG_FILE_CONTENT, "")
        }
        
        // Initialize views
        runCodeButton = view.findViewById(R.id.run_code_button)
        runStatus = view.findViewById(R.id.run_status)
        inputText = view.findViewById(R.id.input_text)
        actualOutputText = view.findViewById(R.id.actual_output_text)
        expectedOutputText = view.findViewById(R.id.expected_output_text)
        executionModeChipGroup = view.findViewById(R.id.execution_mode_chip_group)
        localModeChip = view.findViewById(R.id.local_mode_chip)
        cloudModeChip = view.findViewById(R.id.cloud_mode_chip)
        
        // Initialize compiler service
        compilerService = CompilerService(requireContext())
        
        // Set initial state
        runStatus.text = ""
        
        // Load execution mode preference
        loadExecutionModePreference()
        
        // Load data from cache
        loadFromCache()
        
        // Set event listeners
        setupListeners()
        
        return view
    }
    
    /**
     * Set up event listeners for UI components
     * 设置UI组件的事件监听器
     */
    private fun setupListeners() {
        // Run code button
        runCodeButton.setOnClickListener {
            when (currentExecutionMode) {
                ExecutionMode.LOCAL -> runCodeLocally()
                ExecutionMode.CLOUD -> runCodeOnGitHub()
            }
        }
        
        // Execution mode chips
        localModeChip.setOnClickListener {
            setExecutionMode(ExecutionMode.LOCAL)
        }
        
        cloudModeChip.setOnClickListener {
            setExecutionMode(ExecutionMode.CLOUD)
        }
    }
    
    /**
     * Load execution mode preference
     */
    private fun loadExecutionModePreference() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val modeString = sharedPref.getString("execution_mode", "CLOUD")
        currentExecutionMode = try {
            ExecutionMode.valueOf(modeString ?: "CLOUD")
        } catch (e: Exception) {
            ExecutionMode.CLOUD
        }
        
        updateExecutionModeUI()
    }
    
    /**
     * Set execution mode and save preference
     */
    private fun setExecutionMode(mode: ExecutionMode) {
        currentExecutionMode = mode
        
        // Save to preferences
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPref.edit().putString("execution_mode", mode.name).apply()
        
        updateExecutionModeUI()
    }
    
    /**
     * Update UI based on execution mode
     */
    private fun updateExecutionModeUI() {
        when (currentExecutionMode) {
            ExecutionMode.LOCAL -> {
                localModeChip.isChecked = true
                cloudModeChip.isChecked = false
                runCodeButton.text = "本地运行"
                runCodeButton.setIconResource(R.drawable.ic_play_arrow)
            }
            ExecutionMode.CLOUD -> {
                localModeChip.isChecked = false
                cloudModeChip.isChecked = true
                runCodeButton.text = "云端运行"
                runCodeButton.setIconResource(R.drawable.ic_cloud)
            }
        }
    }
    
    /**
     * Run code locally using the compiler service
     */
    private fun runCodeLocally() {
        if (fileContent.isBlank()) {
            showError("没有代码内容可执行")
            return
        }
        
        setRunning(true, "本地编译运行中...")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = when (getLanguageFromExtension(fileName)) {
                    Language.C -> compilerService.compileC(fileContent, Language.C)
                    Language.CPP -> compilerService.compileC(fileContent, Language.CPP)
                    Language.JAVA -> compilerService.compileJava(fileContent)
                    Language.PYTHON -> {
                        // Python直接执行
                        val execResult = compilerService.runPython(fileContent)
                        displayLocalExecutionResult(execResult.output, execResult.error, execResult.success)
                        return@launch
                    }
                    else -> {
                        showError("不支持的语言类型: $language")
                        return@launch
                    }
                }
                
                if (result.success && result.outputFile != null) {
                    // 编译成功，运行程序
                    setRunning(true, "运行程序中...")
                    val execResult = compilerService.runProgram(result.outputFile)
                    displayLocalExecutionResult(execResult.output, execResult.error, execResult.success)
                } else {
                    // 编译失败
                    displayLocalExecutionResult("", result.error ?: "编译失败", false)
                }
                
            } catch (e: Exception) {
                Log.e("IOPanel", "本地执行异常", e)
                displayLocalExecutionResult("", "本地执行异常: ${e.message}", false)
            } finally {
                setRunning(false)
            }
        }
    }
    
    /**
     * Display local execution result
     */
    private fun displayLocalExecutionResult(output: String, error: String?, success: Boolean) {
        val resultText = if (success) {
            if (output.isNotBlank()) output else "(程序执行完成，无输出)"
        } else {
            "执行失败:\n${error ?: "未知错误"}"
        }
        
        actualOutputText.setText(resultText)
        
        val statusText = if (success) "本地运行完成" else "本地运行失败"
        val statusColor = if (success) R.color.compile_success else R.color.compile_error
        
        runStatus.text = statusText
        runStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))
        
        // Save to cache
        saveToCache()
    }
    
    /**
     * Get Language enum from file extension
     */
    private fun getLanguageFromExtension(fileName: String): Language? {
        return when (fileName.substringAfterLast('.')) {
            "c" -> Language.C
            "cpp", "cc", "cxx" -> Language.CPP
            "java" -> Language.JAVA
            "py" -> Language.PYTHON
            else -> null
        }
    }
    
    /**
     * Set running state and update UI
     */
    private fun setRunning(running: Boolean, statusText: String = "") {
        runCodeButton.isEnabled = !running
        if (statusText.isNotEmpty()) {
            runStatus.text = statusText
        }
        
        if (running) {
            runStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.compile_warning))
        }
    }
    
    /**
     * Show error message
     */
    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        runStatus.text = message
        runStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.compile_error))
    }

    private fun loadFromCache() {
        // Load data from cache
        val cachedInstance = ioCache[fileName]
        if (cachedInstance != null) {
            inputText.setText(cachedInstance.input)
            actualOutputText.setText(cachedInstance.actualOutput)
            expectedOutputText.setText(cachedInstance.expectedOutput)
            if (cachedInstance.status.isNotEmpty()) {
                // Display status and execution time (if available)
                if (cachedInstance.executionTime > 0) {
                    // Set status text
                    runStatus.text = getString(R.string.execution_status_with_time, cachedInstance.status, cachedInstance.executionTime)
                    
                    // Set color for status part
                    setStatusColor(cachedInstance.status)
                    
                    // Set gray color for execution time part
                    val spannable = SpannableString(runStatus.text)
                    spannable.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(requireContext(), android.R.color.darker_gray)),
                        cachedInstance.status.length, // Start from after status
                        runStatus.text.length, // To end of text
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    runStatus.text = spannable
                } else {
                runStatus.text = cachedInstance.status
                setStatusColor(cachedInstance.status)
                }
            }
        }
    }
    
    /**
     * Save current data to cache
     * 将当前数据保存到缓存
     */
    private fun saveToCache(executionTime: Int = 0) {
        // Save data to cache
        val statusText = runStatus.text.toString()
        // Extract status code (first 2-3 characters, like AC, WA, TLE, etc.)
        val status = if (statusText.length >= 2) {
            when {
                statusText.startsWith("AC") -> "AC"
                statusText.startsWith("WA") -> "WA"
                statusText.startsWith("CE") -> "CE"
                statusText.startsWith("TLE") -> "TLE"
                statusText.startsWith("MLE") -> "MLE"
                statusText.startsWith("RE") -> "RE"
                statusText.startsWith("RS") -> "RS"
                else -> statusText
            }
        } else {
            statusText
        }
        
        ioCache[fileName] = IOInstance(
            input = inputText.text.toString(),
            actualOutput = actualOutputText.text.toString(),
            expectedOutput = expectedOutputText.text.toString(),
            status = status, // Save only status code, not execution time
            executionTime = executionTime
        )
    }
    
    /**
     * Run code on GitHub using GitHub Actions workflow
     * 在GitHub上使用GitHub Actions工作流运行代码
     */
    private fun runCodeOnGitHub() {
        // Check if there's already a running workflow
        if (isGitHubRunning) {
            Toast.makeText(context, getString(R.string.already_running), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get GitHub settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val repoUrl = prefs.getString(PREF_GITHUB_REPO_URL, "") ?: ""
        val pat = prefs.getString(PREF_GITHUB_PAT, "") ?: ""
        
        if (repoUrl.isEmpty() || pat.isEmpty()) {
            Toast.makeText(context, getString(R.string.no_github_settings), Toast.LENGTH_LONG).show()
            return
        }
        
        // Parse repository owner and name
        val regex = "https://github.com/([^/]+)/([^/]+)".toRegex()
        val match = regex.find(repoUrl)
        
        if (match == null || match.groupValues.size < 3) {
            Toast.makeText(context, getString(R.string.invalid_repo_url), Toast.LENGTH_SHORT).show()
            return
        }
        
        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")
        
        // Get input, code and expected output
        val input = inputText.text.toString()
        val expected = expectedOutputText.text.toString()
        
        // Get code
        val mainActivity = activity as MainActivity
        val code = mainActivity.files[fileName] ?: ""
        
        if (code.isBlank()) {
            Toast.makeText(context, getString(R.string.code_empty), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Map language format
        val mappedLanguage = LANGUAGE_MAP[language] ?: "cpp"
        
        // Disable run button, show progress
        runCodeButton.isEnabled = false
        runStatus.text = getString(R.string.code_running)
        runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        actualOutputText.setText(getString(R.string.code_running_on_github))
        isGitHubRunning = true
        
        // Use coroutines to execute GitHub API calls in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Call GitHub API to trigger workflow
                val runId = triggerGitHubWorkflow(owner, repo, pat, code, input, expected, mappedLanguage)
                
                if (runId > 0) {
                    // Start polling workflow status
                    pollWorkflowStatus(owner, repo, pat, runId)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, getString(R.string.workflow_trigger_failed), Toast.LENGTH_SHORT).show()
                        resetRunState()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.workflow_error, e.message), Toast.LENGTH_LONG).show()
                    resetRunState()
                }
            }
        }
    }
    
    /**
     * Trigger GitHub workflow through API
     * 通过API触发GitHub工作流
     */
    private suspend fun triggerGitHubWorkflow(
        owner: String,
        repo: String,
        pat: String,
        code: String,
        input: String,
        expected: String,
        language: String
    ): Long = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // Prepare request body
        val jsonBody = JSONObject().apply {
            put("ref", "main") // Assume workflow is on main branch
            
            // Input parameters
            val inputs = JSONObject().apply {
                put("source_code", code)
                put("std_input", input)
                put("expected_output", expected)
                put("language", language)
            }
            
            put("inputs", inputs)
        }
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        
        // Build API request - correct workflow file path
        val workflowFileName = "code-execution.yml"
        val request = Request.Builder()
            .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches")
            .header("Authorization", "token $pat")
            .header("Accept", "application/vnd.github.v3+json")
            .post(requestBody)
            .build()
        
        // Execute request
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw IOException("GitHub API error: $errorBody")
            }
            
            // Get latest workflow run ID
            // Since GitHub API doesn't directly return run ID when triggering workflow,
            // we need to query recent workflow runs separately
            delay(2000) // Give GitHub some time (2s) to create workflow run
            getLatestWorkflowRunId(owner, repo, pat)
        }
    }
    
    /**
     * Get latest workflow run ID
     * 获取最新的工作流运行ID
     */
    private suspend fun getLatestWorkflowRunId(
        owner: String,
        repo: String,
        pat: String
    ): Long = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/runs?per_page=1")
            .header("Authorization", "token $pat")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw IOException("Failed to get workflow run ID: $errorBody")
            }
            
            val responseBody = response.body?.string() ?: "{}"
            val jsonResponse = JSONObject(responseBody)
            val workflowRuns = jsonResponse.getJSONArray("workflow_runs")
            
            if (workflowRuns.length() > 0) {
                val latestRun = workflowRuns.getJSONObject(0)
                latestRun.getLong("id")
            } else {
                -1L
            }
        }
    }
    
    /**
     * Poll workflow status until completion
     * 轮询工作流状态直到完成
     */
    private fun pollWorkflowStatus(owner: String, repo: String, pat: String, runId: Long) {
        // Cancel any ongoing polling
        pollingJob?.cancel()
        
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            var isCompleted = false
            var attempts = 0
            
            while (!isCompleted && attempts < 60) { // Maximum 60 attempts
                delay(2000) // Check every 2 seconds
                attempts++
                
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    
                    val request = Request.Builder()
                        .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/runs/$runId")
                        .header("Authorization", "token $pat")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: "{}"
                            val jsonResponse = JSONObject(responseBody)
                            val status = jsonResponse.getString("status")
                            
                            withContext(Dispatchers.Main) {
                                val progressText = getString(R.string.code_running_time, attempts * 2)
                                actualOutputText.setText(progressText)
                            }
                            
                            if (status == "completed") {
                                isCompleted = true
                                // Get workflow results
                                downloadAndProcessArtifacts(owner, repo, pat, runId)
                            }
                        } else {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            throw IOException("Failed to check workflow status: $errorBody")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, getString(R.string.workflow_polling_error, e.message), Toast.LENGTH_SHORT).show()
                    }
                    break
                }
            }
            
            if (!isCompleted) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, getString(R.string.workflow_timeout), Toast.LENGTH_SHORT).show()
                    resetRunState()
                }
            }
        }
    }
    
    /**
     * Download and process workflow artifacts
     * 下载并处理工作流产物
     */
    private suspend fun downloadAndProcessArtifacts(
        owner: String,
        repo: String,
        pat: String,
        runId: Long
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Get Artifacts list
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val listRequest = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/runs/$runId/artifacts")
                .header("Authorization", "token $pat")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            client.newCall(listRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw IOException(getString(R.string.result_processing_error, "Failed to get Artifacts: $errorBody"))
                }
                
                val responseBody = response.body?.string() ?: "{}"
                val jsonResponse = JSONObject(responseBody)
                val artifacts = jsonResponse.getJSONArray("artifacts")
                
                if (artifacts.length() == 0) {
                    throw IOException(getString(R.string.artifact_not_found) + " - Workflow execution may not have generated results")
                }
                
                // Find judge-result artifact
                var artifactId = -1L
                var artifactName = ""
                
                for (i in 0 until artifacts.length()) {
                    val artifact = artifacts.getJSONObject(i)
                    val name = artifact.getString("name")
                    if (name == "judge-result") {
                        artifactId = artifact.getLong("id")
                        artifactName = name
                        break
                    }
                }
                
                if (artifactId == -1L) {
                    // List all available artifact names for debugging
                    val availableArtifacts = (0 until artifacts.length())
                        .map { artifacts.getJSONObject(it).getString("name") }
                        .joinToString(", ")
                    
                    throw IOException(getString(R.string.artifact_not_found) + 
                        " - Available artifacts: $availableArtifacts")
                }
                
                // 2. Download Artifact
                val downloadRequest = Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/artifacts/$artifactId/zip")
                    .header("Authorization", "token $pat")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                client.newCall(downloadRequest).execute().use { downloadResponse ->
                    if (!downloadResponse.isSuccessful) {
                        val errorBody = downloadResponse.body?.string() ?: "Unknown error"
                        throw IOException(getString(R.string.result_processing_error, "Download failed (${downloadResponse.code}): $errorBody"))
                    }
                    
                    // Save ZIP file to temporary directory
                    val zipFile = File(requireContext().cacheDir, "$artifactName.zip")
                    val inputStream = downloadResponse.body?.byteStream()
                        ?: throw IOException(getString(R.string.result_processing_error, "Download response is empty"))
                    
                    FileOutputStream(zipFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    
                    // 3. Extract and process results
                    val resultsDir = File(requireContext().cacheDir, "judge-results")
                    if (!resultsDir.exists()) {
                        resultsDir.mkdirs()
                    } else {
                        resultsDir.listFiles()?.forEach { it.delete() }
                    }
                    
                    unzipFile(zipFile, resultsDir)
                    
                    // List all extracted files for debugging
                    val extractedFiles = resultsDir.listFiles()?.map { it.name } ?: listOf()
                    Log.d("IOPanelFragment", "Extracted files: ${extractedFiles.joinToString()}")
                    
                    // 4. Read results
                    val resultFile = File(resultsDir, "result.json")
                    val actualOutputFile = File(resultsDir, "actual.txt")
                    
                    if (resultFile.exists()) {
                        val resultJson = JSONObject(resultFile.readText())
                        val status = resultJson.getString("status")
                        val executionTime = resultJson.optInt("execution_time", 0)
                        
                        var actualOutput = getString(R.string.no_output)
                        if (actualOutputFile.exists()) {
                            actualOutput = actualOutputFile.readText()
                        } else {
                            // If actual.txt doesn't exist, but there's compile error log, show compile error
                            val compileErrorFile = File(resultsDir, "compile.log")
                            if (compileErrorFile.exists() && status == "CE") {
                                actualOutput = compileErrorFile.readText()
                            }
                        }
                        
                        // Update UI
                        withContext(Dispatchers.Main) {
                            // Display status and execution time
                            if (executionTime > 0 && status != "CE") {
                                runStatus.text = getString(R.string.execution_status_with_time, status, executionTime)
                                setStatusColor(status)
                                val spannable = SpannableString(runStatus.text)
                                spannable.setSpan(
                                    ForegroundColorSpan(ContextCompat.getColor(requireContext(), android.R.color.darker_gray)),
                                    status.length, // Start from after status
                                    runStatus.text.length, // To end of text
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                runStatus.text = spannable
                            } else {
                                runStatus.text = status
                                setStatusColor(status)
                            }
                            actualOutputText.setText(actualOutput)
                            // Save execution time to cache
                            saveToCache(executionTime)
                            resetRunState()
                        }
                    } else {
                        throw IOException(getString(R.string.artifact_not_found) + 
                            " - result.json file not found. Extracted files: ${extractedFiles.joinToString()}")
                    }
                    
                    // Clean up temporary files
                    zipFile.delete()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, getString(R.string.result_processing_error, e.message), Toast.LENGTH_LONG).show()
                resetRunState()
            }
        }
    }
    
    /**
     * Extract ZIP file to destination directory
     * 解压ZIP文件到目标目录
     */
    private fun unzipFile(zipFile: File, destinationDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            val buffer = ByteArray(1024)
            
            while (entry != null) {
                val file = File(destinationDir, entry.name)

                if (!file.parentFile?.exists()!!) {
                    file.parentFile?.mkdirs()
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    // Write file content
                    FileOutputStream(file).use { fileOutputStream ->
                        var len: Int
                        while (zipInputStream.read(buffer).also { len = it } > 0) {
                            fileOutputStream.write(buffer, 0, len)
                        }
                    }
                }
                
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
    }
    
    /**
     * Reset running state to allow new executions
     * 重置运行状态以允许新的执行
     */
    private fun resetRunState() {
        runCodeButton.isEnabled = true
        isGitHubRunning = false
    }
    
    /**
     * Set color for status text based on status type
     * 根据状态类型设置状态文本的颜色
     */
    private fun setStatusColor(status: String) {
        when (status) {
            "CE" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            "AC" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            "WA" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            "TLE" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
            "MLE" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
            "RE" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_purple))
            "RS" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light))
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save to cache
        saveToCache()
        
        // Cancel polling task
        pollingJob?.cancel()
    }
} 
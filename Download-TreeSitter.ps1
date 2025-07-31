# Tree-sitter Query Files Download Script
param(
    [string]$TargetPath = "app/src/main/assets/treesitter",
    [switch]$Force = $false,
    [switch]$Help = $false
)

if ($Help) {
    Write-Host @"
Tree-sitter Query Files Download Script

Usage: .\Download-TreeSitterQueries.ps1 [options]

Options:
  -TargetPath <path>  Target directory path (default: app/src/main/assets/treesitter)
  -Force              Force overwrite existing files
  -Help               Show this help message

Examples:
  .\Download-TreeSitterQueries.ps1
  .\Download-TreeSitterQueries.ps1 -TargetPath "custom/path" -Force
"@
    exit 0
}

function Write-Status {
    param($Message, $Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

Write-Status "`n=== Tree-sitter Query Files Downloader ===" "Magenta"
Write-Status "Target: $TargetPath" "Cyan"
Write-Status "Force: $Force" "Cyan"
Write-Host ""


if (-not (Test-Path $TargetPath)) {
    New-Item -ItemType Directory -Path $TargetPath -Force | Out-Null
    Write-Status "Created directory: $TargetPath" "Green"
}


$urls = @(
    "https://raw.githubusercontent.com/tree-sitter/tree-sitter-java/master/queries/highlights.scm",
    "https://raw.githubusercontent.com/tree-sitter/tree-sitter-java/master/queries/tags.scm",
    "https://raw.githubusercontent.com/tree-sitter/tree-sitter-cpp/master/queries/highlights.scm",
    "https://raw.githubusercontent.com/tree-sitter/tree-sitter-cpp/master/queries/tags.scm",
    "https://raw.githubusercontent.com/tree-sitter/tree-sitter-python/master/queries/highlights.scm",
    "https://raw.githubusercontent.com/tree-sitter/tree-sitter-python/master/queries/tags.scm"
)

$paths = @(
    "$TargetPath/java/highlights.scm",
    "$TargetPath/java/tags.scm",
    "$TargetPath/cpp/highlights.scm",
    "$TargetPath/cpp/tags.scm",
    "$TargetPath/python/highlights.scm",
    "$TargetPath/python/tags.scm"
)

$names = @(
    "Java highlights",
    "Java tags",
    "C++ highlights",
    "C++ tags",
    "Python highlights",
    "Python tags"
)

$success = 0
$total = $urls.Length

Write-Status "Starting download of $total files..." "Yellow"
Write-Host ""

for ($i = 0; $i -lt $total; $i++) {
    try {
        Write-Status "Downloading $($names[$i])..." "Cyan"
        Write-Host "  URL: $($urls[$i])" -ForegroundColor Gray
        Write-Host "  Path: $($paths[$i])" -ForegroundColor Gray
        
        $dir = Split-Path $paths[$i] -Parent
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
        
        if ((Test-Path $paths[$i]) -and -not $Force) {
            Write-Status "  File exists, skipping (use -Force to overwrite)" "Yellow"
        } else {
            Invoke-WebRequest -Uri $urls[$i] -OutFile $paths[$i] -ErrorAction Stop
            Write-Status "  Success!" "Green"
            $success++
        }
    }
    catch {
        Write-Status "  Failed: $($_.Exception.Message)" "Red"
    }
    Write-Host ""
}

Write-Host ("=" * 50)
if ($success -eq $total) {
    Write-Status "All done! Downloaded $success/$total files" "Green"
} elseif ($success -gt 0) {
    Write-Status "Partial success! Downloaded $success/$total files" "Yellow"
} else {
    Write-Status "Download failed!" "Red"
}

if ($success -gt 0) {
    Write-Host ""
    Write-Status "Files saved to: $TargetPath/" "Cyan"
    Write-Host ""
    Write-Status "Usage notes:" "Cyan"
    Write-Host "  1. Files will be auto-loaded by TreeSitterAnalyzer" -ForegroundColor Gray
    Write-Host "  2. Built-in queries used if files not found" -ForegroundColor Gray
    Write-Host "  3. You can modify query files as needed" -ForegroundColor Gray
}

Write-Host "" 
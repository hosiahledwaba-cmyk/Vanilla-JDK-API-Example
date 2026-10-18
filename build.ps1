param(
    [switch]$Debug,
    [string]$Mode = ""
)

$isDebug = $Debug -or ($Mode -match "^-?-?debug$" -or $Mode -eq "-g")

Write-Host "========================================================" -ForegroundColor Cyan
if ($isDebug) {
    Write-Host "  Robinhood Vanilla JDK Platform - Build [DEBUG MODE]" -ForegroundColor Yellow
} else {
    Write-Host "  Robinhood Vanilla JDK Platform - Build" -ForegroundColor Cyan
}
Write-Host "========================================================" -ForegroundColor Cyan

$root = $PSScriptRoot
if (-not $root) { $root = Get-Location }

$binDir = Join-Path $root "bin"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir | Out-Null
}

$apiSrc = Join-Path $root "api\src"
$engineSrc = Join-Path $root "engine\src"

$files = Get-ChildItem -Recurse -Filter *.java $apiSrc, $engineSrc | Select-Object -ExpandProperty FullName
if (-not $files -or $files.Count -eq 0) {
    Write-Host "[ERROR] No Java source files found in api\src or engine\src." -ForegroundColor Red
    exit 1
}

$fileCount = $files.Count
if ($isDebug) {
    Write-Host "[DEBUG] Discovered $fileCount Java source files across api and engine." -ForegroundColor Gray
    try {
        $v = javac -version 2>&1
        Write-Host "[DEBUG] Java Compiler: $v" -ForegroundColor Gray
    } catch {}
    Write-Host "[DEBUG] Compiler Flags: -encoding UTF-8 -g -parameters -Xlint:unchecked -Xlint:deprecation" -ForegroundColor Gray
    Write-Host "[DEBUG] Target Directory: $binDir" -ForegroundColor Gray
    $javacArgs = @("-encoding", "UTF-8", "-g", "-parameters", "-Xlint:unchecked", "-Xlint:deprecation", "-cp", "lib/*;bin", "-d", "bin") + $files
} else {
    Write-Host "Compiling $fileCount Java source files (api and engine)..."
    $javacArgs = @("-encoding", "UTF-8", "-g:lines,source", "-cp", "lib/*;bin", "-d", "bin") + $files
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()
& javac @javacArgs
$sw.Stop()
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host ""
    Write-Host ("[SUCCESS] Compilation successful in {0:N2}s! Output stored in .\bin" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
    if (-not $isDebug) {
        Write-Host "Tip: Run 'build.bat debug' to compile with full debug symbols (-g) and diagnostics." -ForegroundColor DarkGray
    }
    exit 0
} else {
    Write-Host ""
    Write-Host "[ERROR] Compilation failed with exit code $exitCode" -ForegroundColor Red
    exit $exitCode
}

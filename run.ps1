param(
    [string]$Action = "toggle"
)

$root = $PSScriptRoot
if (-not $root) { $root = Get-Location }

$pidFile = Join-Path $root ".server.pid"
$logFile = Join-Path $root "server.log"
$binClass = Join-Path $root "bin\com\robinhood\Main.class"

function Get-RunningServerPid {
    param($RootPath, $PidFilePath)

    # 1. Check PID file
    if (Test-Path $PidFilePath) {
        try {
            $content = (Get-Content $PidFilePath -ErrorAction SilentlyContinue | Out-String).Trim()
            if ($content -match "^\d+$") {
                $candidatePid = [int]$content
                $proc = Get-Process -Id $candidatePid -ErrorAction SilentlyContinue
                if ($proc -and ($proc.ProcessName -match "java")) {
                    return $candidatePid
                }
            }
        } catch {}
    }

    # 2. Check port 8080 listener
    try {
        $conns = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $conns) {
            $p = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            if ($p -and ($p.ProcessName -match "java")) {
                return $p.Id
            }
        }
    } catch {}

    return $null
}

function Stop-ServerInstance {
    param($PidToKill, $PidFilePath)
    Write-Host "[ACTION] Stopping Robinhood server (PID: $PidToKill)..." -ForegroundColor Yellow
    try {
        Stop-Process -Id $PidToKill -Force -ErrorAction SilentlyContinue
    } catch {}

    if (Test-Path $PidFilePath) {
        Remove-Item -Path $PidFilePath -Force -ErrorAction SilentlyContinue
    }

    # Wait briefly and verify stopped
    $stopped = $false
    for ($i = 0; $i -lt 6; $i++) {
        Start-Sleep -Milliseconds 500
        $check = Get-Process -Id $PidToKill -ErrorAction SilentlyContinue
        if (-not $check) {
            $stopped = $true
            break
        }
    }

    if ($stopped) {
        Write-Host "[SUCCESS] Robinhood server stopped successfully." -ForegroundColor Green
        Write-Host "Tip: Run 'run.bat' to start the server again in the background." -ForegroundColor DarkGray
    } else {
        Write-Host "[WARNING] Process $PidToKill may still be terminating." -ForegroundColor Yellow
    }
}

function Start-ServerInstance {
    param($RootPath, $PidFilePath, $LogFilePath, $BinClassPath)

    # Ensure project is compiled
    if (-not (Test-Path $BinClassPath)) {
        Write-Host "[INFO] Binaries not found. Building project first..." -ForegroundColor Cyan
        & "$RootPath\build.bat"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Build failed. Cannot start server." -ForegroundColor Red
            exit $LASTEXITCODE
        }
    }

    Write-Host "[ACTION] Starting Robinhood engine in the background..." -ForegroundColor Cyan

    $outLog = Join-Path $RootPath "server.log"

    # Launch java detached in background via WMI to ensure complete independence from console session
    $cmd = 'cmd.exe /c "java -cp bin;lib/*;api\src com.robinhood.Main > server.log 2>&1"'
    $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine = $cmd
        CurrentDirectory = $RootPath
    }

    # Wait up to 4 seconds for server to initialize and bind port 8080
    $javaPid = $null
    for ($i = 0; $i -lt 8; $i++) {
        Start-Sleep -Milliseconds 500
        $conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) {
            $p = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            if ($p -and ($p.ProcessName -match "java")) {
                $javaPid = $p.Id
                break
            }
        }
    }

    if ($javaPid) {
        $javaPid | Out-File -FilePath $PidFilePath -Encoding ascii
        Write-Host ""
        Write-Host "[SUCCESS] Robinhood server is now running in the background!" -ForegroundColor Green
        Write-Host ("  PID:      " + $javaPid) -ForegroundColor White
        Write-Host ("  Port:     http://localhost:8080") -ForegroundColor White
        Write-Host ("  Logs:     server.log") -ForegroundColor White
        Write-Host ""
        Write-Host "Tip: Run 'run.bat' again while it is running to stop the server." -ForegroundColor DarkGray
        exit 0
    } else {
        Write-Host ""
        Write-Host "[ERROR] Server failed to start or bind to port 8080." -ForegroundColor Red
        if (Test-Path $outLog) {
            Write-Host "Recent server output:" -ForegroundColor Yellow
            Get-Content $outLog -Tail 15
        }
        if (Test-Path $PidFilePath) {
            Remove-Item -Path $PidFilePath -Force -ErrorAction SilentlyContinue
        }
        exit 1
    }
}

# --- Main Entry Point ---
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Robinhood Vanilla JDK Platform - Server Control" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$runningPid = Get-RunningServerPid -RootPath $root -PidFilePath $pidFile

$act = $Action.ToLower().Trim()

if ($act -eq "status") {
    if ($runningPid) {
        Write-Host "[STATUS] Server is RUNNING in the background (PID: $runningPid)." -ForegroundColor Green
        Write-Host "  URL: http://localhost:8080"
    } else {
        Write-Host "[STATUS] Server is STOPPED." -ForegroundColor Yellow
    }
    exit 0
}

if ($act -eq "stop") {
    if ($runningPid) {
        Stop-ServerInstance -PidToKill $runningPid -PidFilePath $pidFile
    } else {
        Write-Host "[INFO] Server is not running." -ForegroundColor Yellow
    }
    exit 0
}

if ($act -eq "start") {
    if ($runningPid) {
        Write-Host "[INFO] Server is ALREADY running in the background (PID: $runningPid)." -ForegroundColor Yellow
        Write-Host "  URL: http://localhost:8080"
        Write-Host "  To stop it, run: run.bat stop  (or run.bat to toggle)" -ForegroundColor DarkGray
    } else {
        Start-ServerInstance -RootPath $root -PidFilePath $pidFile -LogFilePath $logFile -BinClassPath $binClass
    }
    exit 0
}

# Default: Toggle behavior!
# If already running -> stop it. If stopped -> start it.
if ($runningPid) {
    Write-Host "[STATUS] Server is currently RUNNING in the background (PID: $runningPid)." -ForegroundColor Cyan
    Stop-ServerInstance -PidToKill $runningPid -PidFilePath $pidFile
} else {
    Write-Host "[STATUS] Server is currently STOPPED." -ForegroundColor Cyan
    Start-ServerInstance -RootPath $root -PidFilePath $pidFile -LogFilePath $logFile -BinClassPath $binClass
}

<#
.SYNOPSIS
    Stop everything started by start-all.ps1.

.DESCRIPTION
    1. Stop backend (java) and frontend (node/vite) processes.
    2. Optional fallback: kill any java/node process whose command line points to this project,
       or that still occupies ports 8989 / 8980.
    3. Stop the Milvus and Redis containers (use -KeepInfra to keep them running).

    MySQL is a Windows service and is never stopped by this script.

.EXAMPLE
    .\stop-all.ps1
    .\stop-all.ps1 -KeepInfra
    .\stop-all.ps1 -BackendPort 8989 -FrontendPort 8980
#>
[CmdletBinding()]
param(
    [switch]$KeepInfra,
    [int]$BackendPort = 8989,
    [int]$FrontendPort = 8980
)

$Root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunDir   = Join-Path $Root '.run'
$BackPid  = Join-Path $RunDir 'backend.pid'
$FrontPid = Join-Path $RunDir 'frontend.pid'

$ErrorActionPreference = 'Continue'

function Write-Step { param([string]$m) Write-Host "[..] $m" -ForegroundColor Cyan }
function Write-Ok   { param([string]$m) Write-Host "[OK] $m" -ForegroundColor Green }
function Write-Warn2{ param([string]$m) Write-Host "[!!] $m" -ForegroundColor Yellow }

function Test-Port {
    param([int]$Port, [string]$TargetHost = '127.0.0.1')
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(800)) { return $false }
        $client.EndConnect($ar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-PidFromFile {
    param([string]$File)
    if (-not (Test-Path $File)) { return 0 }
    $raw = Get-Content $File -ErrorAction SilentlyContinue | Select-Object -First 1
    $n = 0
    if ([int]::TryParse($raw, [ref]$n)) { return $n }
    return 0
}

function Stop-ByPidFile {
    param([string]$File, [string]$Name)
    $procId = Get-PidFromFile -File $File
    if ($procId -le 0) {
        Write-Warn2 "$Name : no pid file"
        return $false
    }
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $proc) {
        Write-Warn2 "$Name : process $procId already gone"
        Remove-Item $File -Force -ErrorAction SilentlyContinue
        return $false
    }
    if ($proc.ProcessName -notin @('java', 'javaw', 'node')) {
        Write-Warn2 "$Name : pid $procId is '$($proc.ProcessName)', not java/node, skip"
        return $false
    }
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    Write-Ok "$Name : stopped pid $procId ($($proc.ProcessName))"
    Remove-Item $File -Force -ErrorAction SilentlyContinue
    return $true
}

function Stop-ProjectProcesses {
    param([string]$Name, [string]$MatchPath, [string[]]$ProcessNames)
    $killed = 0
    foreach ($pn in $ProcessNames) {
        $procs = Get-CimInstance Win32_Process -Filter "Name='$pn'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -like "*$MatchPath*" }
        foreach ($p in $procs) {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Ok "$Name : stopped leftover $pn pid $($p.ProcessId)"
            $killed++
        }
    }
    return $killed
}

function Stop-PortOwner {
    param([int]$Port, [string]$Name)
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
        if ($proc -and $proc.ProcessName -in @('java', 'javaw', 'node')) {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Write-Ok "$Name : freed port $Port (killed $($proc.ProcessName) pid $($proc.Id))"
        }
    }
}

# ------------------------------------------------------------------- Backend
Write-Host ''
Write-Host '===== common-rag-ai stop =====' -ForegroundColor White

Stop-ByPidFile -File $BackPid -Name 'backend' | Out-Null
Stop-ProjectProcesses -Name 'backend' -MatchPath 'common-rag-ai-back\target' -ProcessNames @('java.exe', 'javaw.exe') | Out-Null

# ------------------------------------------------------------------ Frontend
Stop-ByPidFile -File $FrontPid -Name 'frontend' | Out-Null
Stop-ProjectProcesses -Name 'frontend' -MatchPath 'common-rag-ai-front' -ProcessNames @('node.exe') | Out-Null

# -------------------------------------------------------------- Ports / wait
Start-Sleep -Seconds 2
if (Test-Port -Port $BackendPort)  { Stop-PortOwner -Port $BackendPort  -Name 'backend' }
if (Test-Port -Port $FrontendPort) { Stop-PortOwner -Port $FrontendPort -Name 'frontend' }

if (Test-Port -Port $BackendPort) {
    Write-Warn2 "Port $BackendPort is still in use by a non java process"
} else {
    Write-Ok "backend port $BackendPort free"
}
if (Test-Port -Port $FrontendPort) {
    Write-Warn2 "Port $FrontendPort is still in use by a non node process"
} else {
    Write-Ok "frontend port $FrontendPort free"
}

# -------------------------------------------------------------------- Docker
if ($KeepInfra) {
    Write-Warn2 'Infra containers kept running (-KeepInfra)'
} else {
    $dockerUp = $false
    $probe = docker info --format '{{.ServerVersion}}' 2>&1
    if ($LASTEXITCODE -eq 0) { $dockerUp = $true }

    if ($dockerUp) {
        foreach ($name in @('milvus-standalone', 'rag-redis')) {
            $state = (docker ps -a --filter "name=^$name$" --format '{{.State}}' 2>&1 | Out-String).Trim()
            if ($state -eq 'running') {
                Write-Step "Stopping container $name"
                docker stop $name 2>&1 | Out-Null
                Write-Ok "container $name stopped"
            } elseif ($state) {
                Write-Warn2 "container $name is '$state', nothing to stop"
            } else {
                Write-Warn2 "container $name not found"
            }
        }
    } else {
        Write-Warn2 'Docker engine not reachable, skip container stop'
    }
}

Write-Host ''
Write-Host 'Done. MySQL service is left untouched.' -ForegroundColor DarkGray
Write-Host ''
exit 0

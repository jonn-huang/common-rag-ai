<#
.SYNOPSIS
    One-click launcher for common-rag-ai (infra + backend + frontend).

.DESCRIPTION
    1. Resolve JDK 17+ and Maven (PATH or JetBrains bundled maven).
    2. Make sure MySQL is up and the common_rag schema exists (auto import init.sql).
    3. Make sure Docker engine, Redis and Milvus containers are running.
    4. Build (optional) and start the Spring Boot backend on port 8989.
    5. Start the Vite dev server on port 8980.

.EXAMPLE
    .\start-all.ps1
    .\start-all.ps1 -SkipBuild -SkipInfra
    .\start-all.ps1 -DbPassword 123456 -BackendPort 8989
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipInfra,
    [switch]$SkipFrontend,
    [int]$BackendPort = 8989,
    [int]$FrontendPort = 8980,
    [string]$DbUser = 'root',
    [string]$DbPassword = '123456'
)

$Root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackDir   = Join-Path $Root 'common-rag-ai-back'
$FrontDir  = Join-Path $Root 'common-rag-ai-front'
$RunDir    = Join-Path $Root '.run'
$LogDir    = Join-Path $RunDir 'logs'
$BackPid   = Join-Path $RunDir 'backend.pid'
$FrontPid  = Join-Path $RunDir 'frontend.pid'
$BackLog   = Join-Path $LogDir 'backend.out.log'
$BackAppLog= Join-Path $LogDir 'backend.log'
$FrontLog  = Join-Path $LogDir 'frontend.out.log'

$ErrorActionPreference = 'Continue'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Write-Step { param([string]$m) Write-Host "[..] $m" -ForegroundColor Cyan }
function Write-Ok   { param([string]$m) Write-Host "[OK] $m" -ForegroundColor Green }
function Write-Warn2{ param([string]$m) Write-Host "[!!] $m" -ForegroundColor Yellow }
function Write-Err2 { param([string]$m) Write-Host "[XX] $m" -ForegroundColor Red }

function Invoke-Native {
    param([string]$File, [string[]]$Arguments)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $File @Arguments 2>&1
        return [pscustomobject]@{ Output = $out; Code = $LASTEXITCODE }
    } catch {
        return [pscustomobject]@{ Output = $_.Exception.Message; Code = 1 }
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Test-Port {
    param([int]$Port, [string]$TargetHost = '127.0.0.1')
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(1000)) { return $false }
        $client.EndConnect($ar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-Port {
    param([int]$Port, [int]$TimeoutSec = 180, [string]$Name = 'service')
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if (Test-Port -Port $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    Write-Err2 "$Name did not listen on port $Port within $TimeoutSec s"
    return $false
}

# Start a process detached from the current console / job object, so it survives
# after this script returns or the launching window is closed.
function Start-Detached {
    param(
        [string]$CommandLine,
        [string]$WorkingDirectory,
        [string]$MatchPath,
        [int]$TimeoutSec = 30
    )
    $created = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine        = $CommandLine
        CurrentDirectory   = $WorkingDirectory
    }
    if ($created.ReturnValue -ne 0) {
        throw "Win32_Process.Create failed with code $($created.ReturnValue)"
    }

    # Resolve the real java/node child (cmd.exe wrapper is not what we want to track)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $hit = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='node.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -like "*$MatchPath*" -and $_.ProcessId -ne $PID } |
            Sort-Object CreationDate -Descending |
            Select-Object -First 1
        if ($hit) { return [int]$hit.ProcessId }
        Start-Sleep -Milliseconds 500
    }
    return [int]$created.ProcessId
}

function Get-PidFromFile {
    param([string]$File)
    if (-not (Test-Path $File)) { return 0 }
    $raw = (Get-Content $File -ErrorAction SilentlyContinue | Select-Object -First 1)
    $n = 0
    if ([int]::TryParse($raw, [ref]$n)) { return $n }
    return 0
}

# ---------------------------------------------------------------- JDK / Maven
function Resolve-JavaHome {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }

    $jdks = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdks) {
        Get-ChildItem $jdks -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '(17|18|19|2[0-9])' -and $_.Name -notmatch '1\.8' } |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }
    foreach ($base in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'D:\Program Files\Java')) {
        if (Test-Path $base) {
            Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '(17|18|19|2[0-9])' -and $_.Name -notmatch '1\.8' } |
                ForEach-Object { $candidates.Add($_.FullName) }
        }
    }

    foreach ($c in $candidates) {
        if (-not $c) { continue }
        $javaExe = Join-Path $c 'bin\java.exe'
        if (-not (Test-Path $javaExe)) { continue }
        $r = Invoke-Native $javaExe @('-version')
        $text = ($r.Output -join ' ')
        if ($text -match 'version "(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17) { return $c }
        }
    }
    return $null
}

function Resolve-MavenCmd {
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    foreach ($base in @('D:\Program Files\JetBrains', 'C:\Program Files\JetBrains')) {
        if (-not (Test-Path $base)) { continue }
        $hit = Get-ChildItem $base -Filter 'mvn.cmd' -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -like '*maven3\bin\mvn.cmd' } |
            Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

# ---------------------------------------------------------------------- MySQL
function Resolve-MysqlExe {
    foreach ($c in @('C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
                     'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe',
                     'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')) {
        if (Test-Path $c) { return $c }
    }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Ensure-MySql {
    if (Test-Port -Port 3306) { Write-Ok 'MySQL is listening on 3306'; return $true }

    $svc = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -ne 'Running') {
        Write-Step 'Starting MySQL80 service'
        try { Start-Service 'MySQL80' } catch { Write-Warn2 "Cannot start MySQL80 (need admin): $($_.Exception.Message)" }
    }
    if (Wait-Port -Port 3306 -TimeoutSec 30 -Name 'MySQL') {
        Write-Ok 'MySQL is listening on 3306'
        return $true
    }
    Write-Err2 'MySQL is not reachable on port 3306, start the MySQL service first.'
    return $false
}

function Ensure-Database {
    param([string]$MysqlExe, [string]$User, [string]$Password)
    $dbName   = 'common_rag'
    $initSql  = Join-Path $BackDir 'src\main\resources\sql\init.sql'

    $check = Invoke-Native $MysqlExe @("-u$User", "-p$Password", '-N', '-B', '-e', "SHOW DATABASES LIKE '$dbName';")
    if (($check.Output -join '') -match $dbName) {
        Write-Ok "MySQL database '$dbName' exists"
        return $true
    }

    Write-Step "Creating database '$dbName' and importing init.sql"
    Invoke-Native $MysqlExe @("-u$User", "-p$Password", '-e',
        "CREATE DATABASE IF NOT EXISTS $dbName DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;") | Out-Null
    if (Test-Path $initSql) {
        $sqlPath = $initSql.Replace('\', '/')
        Invoke-Native $MysqlExe @("-u$User", "-p$Password", '--default-character-set=utf8mb4', $dbName,
            '-e', "source $sqlPath") | Out-Null
    } else {
        Write-Warn2 "init.sql not found at $initSql"
    }

    $verify = Invoke-Native $MysqlExe @("-u$User", "-p$Password", '-N', '-B', '-e',
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$dbName';")
    $count = 0
    if ([int]::TryParse(($verify.Output -join '').Trim(), [ref]$count) -and $count -gt 0) {
        Write-Ok "Database '$dbName' ready ($count tables)"
        return $true
    }
    Write-Err2 "Database '$dbName' initialization failed. Run init.sql manually."
    return $false
}

# --------------------------------------------------------------------- Docker
function Test-DockerReady {
    $r = Invoke-Native 'docker' @('info', '--format', '{{.ServerVersion}}')
    return ($r.Code -eq 0)
}

function Ensure-DockerEngine {
    if (Test-DockerReady) { Write-Ok 'Docker engine is ready'; return $true }

    $dd = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (Test-Path $dd) {
        Write-Step 'Starting Docker Desktop (this can take a while)'
        try { Start-Process -FilePath $dd -ErrorAction Stop } catch {
            Write-Warn2 "Start-Process failed, trying elevated start: $($_.Exception.Message)"
            try { Start-Process -FilePath $dd -Verb RunAs -ErrorAction Stop } catch { Write-Warn2 $_.Exception.Message }
        }
    } else {
        Write-Warn2 'Docker Desktop.exe not found, expecting a remote/external docker daemon'
    }

    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt 240) {
        if (Test-DockerReady) { Write-Ok 'Docker engine is ready'; return $true }
        Start-Sleep -Seconds 5
    }
    Write-Err2 'Docker engine is not ready. Start Docker Desktop manually and retry.'
    return $false
}

function Get-ContainerState {
    param([string]$Name)
    $r = Invoke-Native 'docker' @('ps', '-a', '--filter', "name=^$Name$", '--format', '{{.State}}')
    return (($r.Output -join '').Trim())
}

function Ensure-Container {
    param([string]$Name, [scriptblock]$CreateIfMissing, [int]$Port, [int]$TimeoutSec = 180)

    if (Test-Port -Port $Port) { Write-Ok "$Name already listening on $Port"; return $true }

    $state = Get-ContainerState -Name $Name
    if (-not $state) {
        Write-Step "Creating container $Name"
        & $CreateIfMissing | Out-Null
    } elseif ($state -ne 'running') {
        Write-Step "Starting container $Name"
        Invoke-Native 'docker' @('start', $Name) | Out-Null
    } else {
        Write-Step "Container $Name is running, waiting for port $Port"
    }

    if (Wait-Port -Port $Port -TimeoutSec $TimeoutSec -Name $Name) {
        Write-Ok "$Name is ready on $Port"
        return $true
    }
    return $false
}

function Ensure-Redis {
    Ensure-Container -Name 'rag-redis' -Port 6379 -TimeoutSec 90 -CreateIfMissing {
        # --restart unless-stopped: survive Docker Desktop / host restarts (manual stop still wins)
        Invoke-Native 'docker' @('run', '-d', '--name', 'rag-redis', '--restart', 'unless-stopped',
            '-p', '6379:6379', 'redis:7-alpine')
    }
}

# Milvus opens 19530 before its internal components (rootcoord/datacoord) are ready.
# Waiting on /healthz keeps the backend from hitting DEADLINE_EXCEEDED at startup.
function Wait-MilvusHealthy {
    param([int]$TimeoutSec = 120)
    $url = 'http://127.0.0.1:9091/healthz'
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($resp.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Ensure-Milvus {
    $volume = (Join-Path $Root 'volumes\milvus').Replace('\', '/')
    New-Item -ItemType Directory -Force -Path (Join-Path $Root 'volumes\milvus') | Out-Null
    $ok = Ensure-Container -Name 'milvus-standalone' -Port 19530 -TimeoutSec 240 -CreateIfMissing {
        Invoke-Native 'docker' @(
            'run', '-d', '--name', 'milvus-standalone', '--restart', 'unless-stopped',
            '-p', '19530:19530', '-p', '9091:9091',
            '-e', 'ETCD_USE_EMBED=true',
            '-e', 'ETCD_DATA_DIR=/var/lib/milvus/etcd',
            '-e', 'COMMON_STORAGETYPE=local',
            '-e', 'DEPLOY_MODE=STANDALONE',
            '-v', "${volume}:/var/lib/milvus",
            '--security-opt', 'seccomp:unconfined',
            'milvusdb/milvus:v2.4.15', 'milvus', 'run', 'standalone'
        )
    }
    if (-not $ok) { return $false }
    if (Wait-MilvusHealthy -TimeoutSec 120) {
        Write-Ok 'Milvus internal components are ready (/healthz 200)'
        return $true
    }
    Write-Warn2 'Milvus listens on 19530 but /healthz is not OK yet, backend may fail to connect'
    return $false
}

# ------------------------------------------------------------------- Backend
function Start-Backend {
    param([string]$JavaHome, [string]$MavenCmd)

    if (Test-Port -Port $BackendPort) {
        Write-Warn2 "Port $BackendPort is already in use, backend assumed running (skip)"
        return $true
    }

    if (-not $SkipBuild) {
        Write-Step 'Building backend (mvn -DskipTests package)'
        $env:JAVA_HOME = $JavaHome
        $env:Path = (Join-Path $JavaHome 'bin') + ';' + (Split-Path -Parent $MavenCmd) + ';' + $env:Path
        Push-Location $BackDir
        $build = Invoke-Native $MavenCmd @('-B', '-DskipTests', 'package')
        Pop-Location
        if ($build.Code -ne 0) {
            ($build.Output | Select-Object -Last 25) | ForEach-Object { Write-Host $_ -ForegroundColor DarkGray }
            Write-Err2 'Backend build failed'
            return $false
        }
        Write-Ok 'Backend build finished'
    }

    $jar = Get-ChildItem (Join-Path $BackDir 'target') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) {
        Write-Err2 'No runnable jar found in target/, run without -SkipBuild'
        return $false
    }

    Write-Step "Starting backend: $($jar.Name)"
    $javaExe = Join-Path $JavaHome 'bin\java.exe'
    $cmdLine = 'cmd.exe /c ""' + $javaExe + '" -Dfile.encoding=UTF-8 -Dlogging.file.name="' + $BackAppLog + '" -jar "' + $jar.FullName + '" > "' + $BackLog + '" 2>&1"'
    try {
        $appPid = Start-Detached -CommandLine $cmdLine -WorkingDirectory $BackDir -MatchPath 'common-rag-ai-back'
    } catch {
        Write-Err2 "Cannot start backend: $($_.Exception.Message)"
        return $false
    }
    Set-Content -Path $BackPid -Value $appPid -Encoding ascii
    Write-Ok "Backend process started (PID $appPid), logs: $LogDir"

    if (Wait-Port -Port $BackendPort -TimeoutSec 180 -Name 'Backend') {
        Write-Ok "Backend is listening on http://localhost:$BackendPort"
        return $true
    }
    Write-Err2 'Backend failed to start, last log lines:'
    foreach ($f in @($BackLog, $BackAppLog)) {
        if (Test-Path $f) { Get-Content $f -Tail 30 | ForEach-Object { Write-Host $_ -ForegroundColor DarkGray } }
    }
    return $false
}

# ------------------------------------------------------------------ Frontend
function Start-Frontend {
    if (Test-Port -Port $FrontendPort) {
        Write-Warn2 "Port $FrontendPort is already in use, frontend assumed running (skip)"
        return $true
    }

    $vite = Join-Path $FrontDir 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path $vite)) {
        Write-Step 'Installing frontend dependencies (npm install --legacy-peer-deps)'
        Push-Location $FrontDir
        $install = Invoke-Native 'npm.cmd' @('install', '--legacy-peer-deps')
        Pop-Location
        if ($install.Code -ne 0 -or -not (Test-Path $vite)) {
            ($install.Output | Select-Object -Last 20) | ForEach-Object { Write-Host $_ -ForegroundColor DarkGray }
            Write-Err2 'Frontend dependency installation failed'
            return $false
        }
    }

    Write-Step 'Starting frontend (vite dev server)'
    $nodeExe = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
    if (-not $nodeExe) { Write-Err2 'node.exe not found on PATH'; return $false }
    $cmdLine = 'cmd.exe /c ""' + $nodeExe + '" "' + $vite + '" --port ' + $FrontendPort + ' > "' + $FrontLog + '" 2>&1"'
    try {
        $appPid = Start-Detached -CommandLine $cmdLine -WorkingDirectory $FrontDir -MatchPath 'common-rag-ai-front'
    } catch {
        Write-Err2 "Cannot start frontend: $($_.Exception.Message)"
        return $false
    }
    Set-Content -Path $FrontPid -Value $appPid -Encoding ascii
    Write-Ok "Frontend process started (PID $appPid), log: $FrontLog"

    if (Wait-Port -Port $FrontendPort -TimeoutSec 90 -Name 'Frontend') {
        Write-Ok "Frontend is listening on http://localhost:$FrontendPort"
        return $true
    }
    Write-Err2 'Frontend failed to start, last log lines:'
    if (Test-Path $FrontLog) { Get-Content $FrontLog -Tail 20 | ForEach-Object { Write-Host $_ -ForegroundColor DarkGray } }
    return $false
}

# ---------------------------------------------------------------------- Main
Write-Host ''
Write-Host '===== common-rag-ai one-click start =====' -ForegroundColor White
Write-Host "Root: $Root" -ForegroundColor DarkGray

$javaHome = Resolve-JavaHome
if (-not $javaHome) {
    Write-Err2 'No JDK 17+ found. Install one or set JAVA_HOME.'
    exit 1
}
$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome 'bin') + ';' + $env:Path
Write-Ok "JAVA_HOME = $javaHome"

$mavenCmd = Resolve-MavenCmd
if (-not $mavenCmd) {
    Write-Err2 'Maven not found (nor mvn on PATH, nor JetBrains bundled maven).'
    exit 1
}
Write-Ok "Maven = $mavenCmd"

$infraOk = $true
if (-not $SkipInfra) {
    if (-not (Ensure-MySql)) { $infraOk = $false }
    $mysqlExe = Resolve-MysqlExe
    if ($mysqlExe -and (Test-Port -Port 3306)) {
        if (-not (Ensure-Database -MysqlExe $mysqlExe -User $DbUser -Password $DbPassword)) { $infraOk = $false }
    } elseif (-not $mysqlExe) {
        Write-Warn2 'mysql.exe not found, skip database check'
    }

    if (Ensure-DockerEngine) {
        if (-not (Ensure-Redis))  { $infraOk = $false }
        if (-not (Ensure-Milvus)) { $infraOk = $false }
    } else {
        $infraOk = $false
    }
} else {
    Write-Warn2 'Infra checks skipped (-SkipInfra)'
}

if (-not $infraOk) {
    Write-Warn2 'Some infrastructure is not ready, backend may fail to start.'
}

$backendOk = Start-Backend -JavaHome $javaHome -MavenCmd $mavenCmd

$frontendOk = $true
if (-not $SkipFrontend) {
    $frontendOk = Start-Frontend
}

Write-Host ''
Write-Host '===== Result =====' -ForegroundColor White
if ($backendOk)  { Write-Ok  "backend  : http://localhost:$BackendPort" } else { Write-Err2 'backend  : FAILED' }
if (-not $SkipFrontend) {
    if ($frontendOk) { Write-Ok "frontend : http://localhost:$FrontendPort  (login admin / admin)" } else { Write-Err2 'frontend : FAILED' }
}
Write-Host "logs     : $LogDir" -ForegroundColor DarkGray
Write-Host 'stop all : .\stop-all.ps1' -ForegroundColor DarkGray
Write-Host ''

if ($backendOk -and ($SkipFrontend -or $frontendOk)) { exit 0 } else { exit 1 }

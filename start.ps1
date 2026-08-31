<#
.SYNOPSIS
    Starts the Signa producer service and the console together.

.DESCRIPTION
    Brings up Postgres (via Docker if it is not already listening), then runs the
    Spring Boot backend and the Vite frontend side by side, tagging each line of
    output so you can tell them apart. Ctrl+C stops both.

    Development defaults only. The OPRF runs on the public development key and a
    first super administrator is seeded so there is something to sign in with.
    Neither belongs anywhere near real data.

.PARAMETER InMemory
    Run with an in-memory database. Needs no Postgres and no Docker, and starts
    from an empty network every time. The quickest way to see the system work.

.PARAMETER SkipDatabase
    Do not touch Postgres. Use when you are pointing DB_URL at your own instance.

.PARAMETER BackendOnly
    Start the producer service without the console.

.PARAMETER FrontendOnly
    Start the console without the producer service.

.PARAMETER Fresh
    Drop and recreate the Docker Postgres volume before starting. Wipes local data.

.PARAMETER NoPause
    Do not wait for a keypress on failure. For CI, where nobody is watching.

.EXAMPLE
    .\start.ps1 -InMemory

.EXAMPLE
    .\start.ps1

.EXAMPLE
    .\start.ps1 -Fresh
#>

[CmdletBinding()]
param(
    [switch]$InMemory,
    [switch]$SkipDatabase,
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$Fresh,
    [switch]$NoPause
)

$ErrorActionPreference = 'Stop'

# Gradle and Vite both emit UTF-8. Without this the console renders their box
# drawing and arrows as mojibake.
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$root = $PSScriptRoot
$backendPath = Join-Path $root 'producer-main'
$frontendPath = Join-Path $root 'frontend'

$script:jobs = @()

# --------------------------------------------------------------- output ----

function Write-Step  ($message) { Write-Host "  $message" -ForegroundColor White }
function Write-Info  ($message) { Write-Host "  $message" -ForegroundColor DarkGray }
function Write-Good  ($message) { Write-Host "  $message" -ForegroundColor Green }
function Write-Warn  ($message) { Write-Host "  $message" -ForegroundColor Yellow }
function Write-Bad   ($message) { Write-Host "  $message" -ForegroundColor Red }

function Write-Banner {
    Write-Host ''
    Write-Host '  SIGNA' -ForegroundColor White -NoNewline
    Write-Host '  producer + console' -ForegroundColor DarkGray
    Write-Host '  ---------------------------------------------------------'
    Write-Host ''
}

<#
    Ends the run with a message the reader can actually see.

    A plain `exit` inside a script launched by double-click or "Run with
    PowerShell" terminates the host, so the window vanishes with the
    explanation still on it. Everything that used to call `exit 1` comes
    through here instead.
#>
function Stop-Here ($message, $remedy) {
    Write-Host ''
    Write-Bad "  $message"
    if ($remedy) {
        Write-Host ''
        foreach ($line in $remedy) { Write-Info "  $line" }
    }
    Write-Host ''
    Wait-BeforeClosing
    exit 1
}

function Wait-BeforeClosing {
    if ($NoPause) { return }
    try {
        Write-Host '  Press any key to close...' -ForegroundColor DarkGray
        $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    } catch {
        # No interactive console (redirected input, CI). Leave the text up briefly.
        Start-Sleep -Seconds 5
    }
}

<#
    Runs a native executable with error handling relaxed.

    Windows PowerShell 5.1 wraps each stderr line from a native command in an
    ErrorRecord. Under $ErrorActionPreference = 'Stop' that aborts the script
    even when the command succeeded, and `java -version` writes its version
    banner to stderr. Every external command in this script goes through here.
#>
function Invoke-Native {
    param([scriptblock]$Body)

    $prior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Body } finally { $ErrorActionPreference = $prior }
}

function Test-Port ($portNumber) {
    try {
        $connection = New-Object Net.Sockets.TcpClient
        $connection.Connect('127.0.0.1', $portNumber)
        $connection.Close()
        return $true
    } catch {
        return $false
    }
}

# ---------------------------------------------------------- prerequisites ----

function Assert-Prerequisites {
    Write-Step 'Checking prerequisites'

    if (-not (Test-Path $backendPath)) {
        Stop-Here "producer-main not found at $backendPath" @('Run this script from the folder that contains producer-main and frontend.')
    }
    if (-not (Test-Path $frontendPath)) {
        Stop-Here "frontend not found at $frontendPath" @('Run this script from the folder that contains producer-main and frontend.')
    }

    if (-not $FrontendOnly) {
        $java = Get-Command java -ErrorAction SilentlyContinue
        if (-not $java) {
            Stop-Here 'java is not on PATH.' @(
                'The backend targets JDK 26. Install a JDK and reopen this terminal,',
                'or run the console alone with:   .\start.ps1 -FrontendOnly'
            )
        }
        # The Gradle toolchain will fetch its own JDK if this one is too old, so
        # report the version rather than refusing to continue.
        # --version (Java 9+) writes to stdout; -version writes to stderr, which
        # PowerShell 5.1 turns into an ErrorRecord and prints as noise.
        $versionLine = Invoke-Native { (java --version | Select-Object -First 1) }
        Write-Info "  java     $versionLine"
    }

    if (-not $BackendOnly) {
        $node = Get-Command node -ErrorAction SilentlyContinue
        if (-not $node) {
            Stop-Here 'node is not on PATH.' @(
                'The console needs Node 20 or newer. Install it and reopen this terminal,',
                'or run the backend alone with:   .\start.ps1 -BackendOnly'
            )
        }
        Write-Info "  node     $(Invoke-Native { node --version })"
    }
}

# -------------------------------------------------------------- database ----

function Start-Database {
    if ($FrontendOnly) { return }

    Write-Step 'Database'

    if ($script:useInMemory) {
        Write-Warn '  in-memory database: data is discarded when you stop'
        return
    }

    if ($SkipDatabase) {
        Write-Info "  skipping, using $($env:DB_URL)"
        return
    }

    if ((Test-Port 5432) -and -not $Fresh) {
        Write-Good '  postgres already listening on 5432'
        return
    }

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Write-Host ''
        Write-Warn '  No database found: nothing is listening on 5432 and Docker is not installed.'
        Write-Host ''
        Write-Info '  You can run without installing anything. The service will use an'
        Write-Info '  in-memory database, which starts empty every time.'
        Write-Host ''

        $answer = $null
        try {
            $answer = Read-Host '  Start with an in-memory database? [Y/n]'
        } catch {
            $answer = $null
        }

        if ($null -ne $answer -and $answer.Trim() -match '^(n|no)$') {
            Stop-Here 'Stopped at your request.' @(
                'To use PostgreSQL instead, either:',
                '  install Docker Desktop and re-run this script, or',
                '  install PostgreSQL and create the database:',
                '    CREATE DATABASE teknofest;',
                '    CREATE USER teknofest WITH PASSWORD ''teknofest'';',
                '    GRANT ALL PRIVILEGES ON DATABASE teknofest TO teknofest;'
            )
        }

        $script:useInMemory = $true
        Write-Warn '  using the in-memory database'
        return
    }

    $container = 'signa-postgres'
    $existing = Invoke-Native { docker ps -a --filter "name=^/$container$" --format '{{.Names}}' }

    if ($Fresh -and $existing) {
        Write-Warn '  -Fresh given: removing the existing container and its data'
        Invoke-Native { docker rm -f $container } | Out-Null
        $existing = $null
    }

    if ($existing) {
        Write-Info '  starting the existing signa-postgres container'
        Invoke-Native { docker start $container } | Out-Null
    } else {
        Write-Info '  creating the signa-postgres container'
        Invoke-Native {
            docker run -d --name $container `
                -e POSTGRES_DB=teknofest `
                -e POSTGRES_USER=teknofest `
                -e POSTGRES_PASSWORD=teknofest `
                -p 5432:5432 postgres:17-alpine
        } | Out-Null
    }

    Write-Info '  waiting for postgres to accept connections'
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port 5432) { Write-Good '  postgres ready on 5432'; return }
        Start-Sleep -Milliseconds 700
    }

    Stop-Here 'Postgres did not accept connections within 60s.' @(
        'Check the container with:   docker logs signa-postgres',
        'Or run without a database:  .\start.ps1 -InMemory'
    )
}

# ------------------------------------------------------------ environment ----

function Set-Environment {
    Write-Step 'Environment'

    # 'dev' enables the seeded super administrator and lets the OPRF fall back to
    # its public development key; both refuse to run in prod. 'local' swaps the
    # datasource for an in-memory one.
    $env:SPRING_PROFILES_ACTIVE = if ($script:useInMemory) { 'dev,local' } else { 'dev' }
    Write-Info "  profiles $($env:SPRING_PROFILES_ACTIVE)"

    if (-not $env:JWT_SECRET_KEY) {
        # Generated per run rather than committed. The old checked-in default let
        # anyone holding the source mint a token for any account.
        # RandomNumberGenerator::Fill is .NET 5+, and Windows PowerShell 5.1
        # runs on .NET Framework. Create()/GetBytes() exists on both.
        $bytes = New-Object byte[] 48
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $env:JWT_SECRET_KEY = [Convert]::ToBase64String($bytes)
        Write-Info '  JWT_SECRET_KEY generated for this run (sessions end when you restart)'
    } else {
        Write-Info '  JWT_SECRET_KEY taken from your environment'
    }

    if (-not $env:DB_URL)      { $env:DB_URL = 'jdbc:postgresql://localhost:5432/teknofest' }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'teknofest' }
    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = 'teknofest' }

    if (-not $env:BOOTSTRAP_ADMIN_EMAIL)    { $env:BOOTSTRAP_ADMIN_EMAIL = 'admin@signa.az' }
    if (-not $env:BOOTSTRAP_ADMIN_PASSWORD) { $env:BOOTSTRAP_ADMIN_PASSWORD = 'signa-dev-password' }

    if ($env:SIGNA_OPRF_ACTIVE_KEY) {
        Write-Info '  SIGNA_OPRF_ACTIVE_KEY taken from your environment'
    } else {
        Write-Warn '  OPRF will use the public development key (dev profile)'
    }
}

# ------------------------------------------------------------- processes ----

function Start-Tagged ($name, $colour, $workingDirectory, $command, $argumentList) {
    $job = Start-Job -Name $name -ScriptBlock {
        param($directory, $executable, $arguments, $environment)
        # Gradle and Vite both write progress to stderr; keep it as output.
        $ErrorActionPreference = 'Continue'
        foreach ($entry in $environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value)
        }
        # A background runspace has no console for [Console]::OutputEncoding to
        # apply to, so a few decorative characters in Vite's banner arrive in the
        # OEM code page. Cosmetic, and not worth redirecting the streams by hand.
        Set-Location $directory
        & $executable @arguments 2>&1 | ForEach-Object {
            # Native stderr arrives wrapped in an ErrorRecord whose ToString() is
            # the exception type. The message is the line the tool actually wrote.
            if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message }
            else { $_.ToString() }
        }
    } -ArgumentList $workingDirectory, $command, $argumentList, @{
        SPRING_PROFILES_ACTIVE   = $env:SPRING_PROFILES_ACTIVE
        JWT_SECRET_KEY           = $env:JWT_SECRET_KEY
        DB_URL                   = $env:DB_URL
        DB_USERNAME              = $env:DB_USERNAME
        DB_PASSWORD              = $env:DB_PASSWORD
        BOOTSTRAP_ADMIN_EMAIL    = $env:BOOTSTRAP_ADMIN_EMAIL
        BOOTSTRAP_ADMIN_PASSWORD = $env:BOOTSTRAP_ADMIN_PASSWORD
        SIGNA_OPRF_ACTIVE_KEY    = $env:SIGNA_OPRF_ACTIVE_KEY
    }

    $script:jobs += [pscustomobject]@{ Job = $job; Label = $name; Colour = $colour }
    return $job
}

function Stop-Everything {
    Write-Host ''
    Write-Step 'Stopping'
    foreach ($entry in $script:jobs) {
        if ($entry.Job.State -eq 'Running') {
            Stop-Job $entry.Job -ErrorAction SilentlyContinue
        }
        Remove-Job $entry.Job -Force -ErrorAction SilentlyContinue
    }
    # Gradle and Vite both spawn children that outlive the job handle.
    Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*vite*' -and $_.CommandLine -like '*signa*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Write-Info '  done'
}

function Show-Credentials {
    Write-Host ''
    Write-Host '  ---------------------------------------------------------'
    Write-Host '  Console    ' -NoNewline; Write-Host 'http://localhost:5173' -ForegroundColor Cyan
    Write-Host '  Producer   ' -NoNewline; Write-Host 'http://localhost:9090' -ForegroundColor Cyan
    Write-Host '  API docs   ' -NoNewline; Write-Host 'http://localhost:9090/swagger-ui.html' -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  Sign in with' -ForegroundColor White
    Write-Host "    email     $($env:BOOTSTRAP_ADMIN_EMAIL)" -ForegroundColor Green
    Write-Host "    password  $($env:BOOTSTRAP_ADMIN_PASSWORD)" -ForegroundColor Green
    Write-Host ''
    if ($script:useInMemory) {
        Write-Host '  In-memory database: everything is lost when you stop.' -ForegroundColor Yellow
    }
    Write-Host '  Development account, dev profile only.' -ForegroundColor DarkGray
    Write-Host '  Ctrl+C stops both services.' -ForegroundColor DarkGray
    Write-Host '  ---------------------------------------------------------'
    Write-Host ''
}

# ------------------------------------------------------------------ main ----

$script:useInMemory = [bool]$InMemory

Write-Banner
Assert-Prerequisites
Start-Database
Set-Environment

if (-not $BackendOnly) {
    if (-not (Test-Path (Join-Path $frontendPath 'node_modules'))) {
        Write-Step 'Installing console dependencies (first run only)'
        Push-Location $frontendPath
        Invoke-Native { npm install --no-audit --no-fund }
        Pop-Location
    }
}

Write-Step 'Starting'

try {
    if (-not $FrontendOnly) {
        $gradlew = Join-Path $backendPath 'gradlew.bat'
        Start-Tagged 'producer' 'Magenta' $backendPath $gradlew @('bootRun', '--console=plain') | Out-Null
        Write-Info '  producer starting (first run compiles, give it a minute)'
    }

    if (-not $BackendOnly) {
        Start-Tagged 'console' 'Cyan' $frontendPath 'npm.cmd' @('run', 'dev') | Out-Null
        Write-Info '  console starting'
    }

    Show-Credentials

    # Stream both jobs until interrupted.
    while ($true) {
        $running = $false
        foreach ($entry in $script:jobs) {
            Receive-Job $entry.Job | ForEach-Object {
                $line = "$_".TrimEnd()
                if ($line) {
                    Write-Host "  $($entry.Label.PadRight(9))" -ForegroundColor $entry.Colour -NoNewline
                    Write-Host " $line"
                }
            }
            if ($entry.Job.State -eq 'Running') { $running = $true }
        }
        if (-not $running) {
            Write-Warn '  every service has exited'
            Write-Info '  scroll up for the reason'
            break
        }
        Start-Sleep -Milliseconds 400
    }
} catch {
    Write-Host ''
    Write-Bad "  $($_.Exception.Message)"
    Write-Info "  at $($_.InvocationInfo.PositionMessage)"
} finally {
    Stop-Everything
    Wait-BeforeClosing
}

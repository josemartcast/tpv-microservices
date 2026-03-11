param(
    [string]$Container = "tpv-mysql",
    [string]$RootPassword = "root",
    [string]$OutputRoot = ".backups",
    [string[]]$Databases = @("tpv_auth", "tpv_pos"),
    [switch]$Compress
)

$ErrorActionPreference = "Stop"

function Invoke-DockerProcess {
    param(
        [string[]]$DockerArgs,
        [string]$StdOutPath,
        [string]$StdErrPath
    )

    if (-not $DockerArgs -or $DockerArgs.Count -eq 0) {
        throw "Invoke-DockerProcess received empty argument list."
    }
    if ($DockerArgs -contains $null) {
        throw "Invoke-DockerProcess received null argument: $($DockerArgs -join ' | ')"
    }

    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & docker @DockerArgs 2> $StdErrPath | Out-File -FilePath $StdOutPath -Encoding utf8
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($exitCode -ne 0) {
        $err = ""
        if (Test-Path $StdErrPath) {
            $err = Get-Content -Path $StdErrPath -Raw
        }
        throw "docker $($DockerArgs -join ' ') failed (exit $exitCode): $err"
    }
}

function Test-ContainerRunning {
    param([string]$Name)
    $id = (& docker ps -q -f "name=^$Name$").Trim()
    return -not [string]::IsNullOrWhiteSpace($id)
}

function Test-DatabaseExists {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$DbName
    )
    $dockerArgs = @("exec", $ContainerName, "mysql", "-uroot", "-p$Password", "-Nse", "SELECT 1", $DbName)
    $tmpOut = [System.IO.Path]::GetTempFileName()
    $tmpErr = [System.IO.Path]::GetTempFileName()
    try {
        try {
            Invoke-DockerProcess -DockerArgs $dockerArgs -StdOutPath $tmpOut -StdErrPath $tmpErr
            return $true
        } catch {
            $err = if (Test-Path $tmpErr) { Get-Content -Path $tmpErr -Raw } else { "" }
            if ($err -match "Unknown database" -or $err -match "Access denied") {
                return $false
            }
            throw
        }
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $tmpOut, $tmpErr
    }
}

function Compress-GzipFile {
    param([string]$InputFile)
    $outputFile = "$InputFile.gz"
    $in = [System.IO.File]::OpenRead($InputFile)
    try {
        $out = [System.IO.File]::Create($outputFile)
        try {
            $gzip = New-Object System.IO.Compression.GzipStream($out, [System.IO.Compression.CompressionLevel]::Optimal)
            try {
                $in.CopyTo($gzip)
            } finally {
                $gzip.Dispose()
            }
        } finally {
            $out.Dispose()
        }
    } finally {
        $in.Dispose()
    }
    Remove-Item -Force $InputFile
    return $outputFile
}

if (-not (Test-ContainerRunning -Name $Container)) {
    throw "Container '$Container' is not running."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$targetDir = Join-Path $OutputRoot $timestamp
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

$meta = [ordered]@{
    createdAt = (Get-Date).ToString("o")
    host = $env:COMPUTERNAME
    gitCommit = ((git rev-parse --short HEAD) 2>$null)
    container = $Container
    files = @()
}

foreach ($db in $Databases) {
    if (-not (Test-DatabaseExists -ContainerName $Container -Password $RootPassword -DbName $db)) {
        throw "Database '$db' does not exist in container '$Container'."
    }

    $sqlPath = Join-Path $targetDir "$db.sql"
    $errPath = Join-Path $targetDir "$db.dump.err.log"
    $dockerArgs = @(
        "exec", $Container, "mysqldump",
        "-uroot", "-p$RootPassword",
        "--single-transaction", "--quick",
        "--routines", "--triggers", "--events",
        "--set-gtid-purged=OFF",
        "--default-character-set=utf8mb4",
        $db
    )

    Invoke-DockerProcess -DockerArgs $dockerArgs -StdOutPath $sqlPath -StdErrPath $errPath
    if ((Get-Item $sqlPath).Length -le 0) {
        throw "Backup file is empty: $sqlPath"
    }

    if ($Compress) {
        $sqlPath = Compress-GzipFile -InputFile $sqlPath
    }

    $meta.files += [ordered]@{
        database = $db
        file = [System.IO.Path]::GetFileName($sqlPath)
        sizeBytes = (Get-Item $sqlPath).Length
    }
}

$metaPath = Join-Path $targetDir "backup-meta.json"
$meta | ConvertTo-Json -Depth 4 | Set-Content -Path $metaPath -Encoding UTF8

Write-Host "Backup completed."
Write-Host "Directory: $targetDir"
Write-Host "Files:"
foreach ($entry in $meta.files) {
    Write-Host ("- {0}: {1} ({2} bytes)" -f $entry.database, $entry.file, $entry.sizeBytes)
}

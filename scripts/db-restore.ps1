param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [string]$Container = "tpv-mysql",
    [string]$RootPassword = "root",
    [string]$SourceAuthDb = "tpv_auth",
    [string]$SourcePosDb = "tpv_pos",
    [string]$TargetAuthDb = "tpv_auth",
    [string]$TargetPosDb = "tpv_pos",
    [switch]$AllowProductionRestore
)

$ErrorActionPreference = "Stop"

function Invoke-DockerChecked {
    param([string[]]$DockerArgs)
    $tmpOut = [System.IO.Path]::GetTempFileName()
    $tmpErr = [System.IO.Path]::GetTempFileName()
    try {
        if (-not $DockerArgs -or $DockerArgs.Count -eq 0) {
            throw "Invoke-DockerChecked received empty argument list."
        }
        if ($DockerArgs -contains $null) {
            throw "Invoke-DockerChecked received null argument: $($DockerArgs -join ' | ')"
        }

        $previous = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            & docker @DockerArgs 2> $tmpErr | Out-File -FilePath $tmpOut -Encoding utf8
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previous
        }
        if ($exitCode -ne 0) {
            $err = (Get-Content -Path $tmpErr -Raw)
            throw "docker $($DockerArgs -join ' ') failed (exit $exitCode): $err"
        }
        return (Get-Content -Path $tmpOut -Raw)
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $tmpOut, $tmpErr
    }
}

function Test-ContainerRunning {
    param([string]$Name)
    $id = (& docker ps -q -f "name=^$Name$").Trim()
    return -not [string]::IsNullOrWhiteSpace($id)
}

function Resolve-BackupFile {
    param(
        [string]$Dir,
        [string]$DbName
    )
    $plain = Join-Path $Dir "$DbName.sql"
    if (Test-Path $plain) {
        return $plain
    }
    $gz = Join-Path $Dir "$DbName.sql.gz"
    if (Test-Path $gz) {
        return $gz
    }
    throw "Backup file not found for '$DbName' in '$Dir'. Expected '$DbName.sql' or '$DbName.sql.gz'."
}

function Expand-GzipToTemp {
    param([string]$GzipPath)
    $tmp = [System.IO.Path]::GetTempFileName()
    $tmpSql = "$tmp.sql"
    Remove-Item -Force $tmp

    $in = [System.IO.File]::OpenRead($GzipPath)
    try {
        $gzip = New-Object System.IO.Compression.GzipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
        try {
            $out = [System.IO.File]::Create($tmpSql)
            try {
                $gzip.CopyTo($out)
            } finally {
                $out.Dispose()
            }
        } finally {
            $gzip.Dispose()
        }
    } finally {
        $in.Dispose()
    }
    return $tmpSql
}

function Ensure-Database {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$DbName
    )
    $sql = "CREATE DATABASE IF NOT EXISTS $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    $null = Invoke-DockerChecked -DockerArgs @("exec", $ContainerName, "mysql", "-uroot", "-p$Password", "-e", $sql)
}

function Restore-SqlIntoDb {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$SqlPath,
        [string]$TargetDb
    )

    $containerSql = "/tmp/restore-$TargetDb-$([Guid]::NewGuid().ToString('N')).sql"
    $null = Invoke-DockerChecked -DockerArgs @("cp", $SqlPath, "$ContainerName`:$containerSql")
    try {
        $restoreCmd = "source $containerSql"
        $null = Invoke-DockerChecked -DockerArgs @(
            "exec", $ContainerName, "mysql", "-uroot", "-p$Password", $TargetDb, "-e", $restoreCmd
        )
    } finally {
        try {
            $null = Invoke-DockerChecked -DockerArgs @("exec", $ContainerName, "rm", "-f", $containerSql)
        } catch {
            # best effort
        }
    }
}

if (-not (Test-Path $BackupDir)) {
    throw "Backup directory does not exist: $BackupDir"
}

if (-not (Test-ContainerRunning -Name $Container)) {
    throw "Container '$Container' is not running."
}

$isProdTarget = ($TargetAuthDb -eq "tpv_auth") -or ($TargetPosDb -eq "tpv_pos")
if ($isProdTarget -and -not $AllowProductionRestore) {
    throw "Refusing to restore over production DB names without -AllowProductionRestore."
}

$authFile = Resolve-BackupFile -Dir $BackupDir -DbName $SourceAuthDb
$posFile = Resolve-BackupFile -Dir $BackupDir -DbName $SourcePosDb

$tempFiles = @()
try {
    if ($authFile.EndsWith(".gz")) {
        $authFile = Expand-GzipToTemp -GzipPath $authFile
        $tempFiles += $authFile
    }
    if ($posFile.EndsWith(".gz")) {
        $posFile = Expand-GzipToTemp -GzipPath $posFile
        $tempFiles += $posFile
    }

    Ensure-Database -ContainerName $Container -Password $RootPassword -DbName $TargetAuthDb
    Ensure-Database -ContainerName $Container -Password $RootPassword -DbName $TargetPosDb

    Restore-SqlIntoDb -ContainerName $Container -Password $RootPassword -SqlPath $authFile -TargetDb $TargetAuthDb
    Restore-SqlIntoDb -ContainerName $Container -Password $RootPassword -SqlPath $posFile -TargetDb $TargetPosDb

    Write-Host "Restore completed."
    Write-Host "- $SourceAuthDb -> $TargetAuthDb"
    Write-Host "- $SourcePosDb -> $TargetPosDb"
} finally {
    foreach ($file in $tempFiles) {
        Remove-Item -Force -ErrorAction SilentlyContinue $file
    }
}

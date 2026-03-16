param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [ValidateSet("auto", "native", "docker")]
    [string]$Mode = "auto",
    [string]$Container = "tpv-mysql",
    [string]$MysqlBinDir = "",
    [string]$RootUser = "root",
    [string]$RootPassword = "root",
    [string]$SourceAuthDb = "tpv_auth",
    [string]$SourcePosDb = "tpv_pos",
    [string]$TargetAuthDb = "tpv_auth",
    [string]$TargetPosDb = "tpv_pos",
    [switch]$AllowProductionRestore
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "db-common.ps1")

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

if (-not (Test-Path $BackupDir)) {
    throw "Backup directory does not exist: $BackupDir"
}
$connection = Resolve-DatabaseMode -Mode $Mode -Container $Container -MysqlBinDir $MysqlBinDir

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

    Ensure-Database -Connection $connection -User $RootUser -Password $RootPassword -DbName $TargetAuthDb
    Ensure-Database -Connection $connection -User $RootUser -Password $RootPassword -DbName $TargetPosDb

    Restore-DatabaseFromFile -Connection $connection -User $RootUser -Password $RootPassword -DbName $TargetAuthDb -SqlPath $authFile
    Restore-DatabaseFromFile -Connection $connection -User $RootUser -Password $RootPassword -DbName $TargetPosDb -SqlPath $posFile

    Write-Host "Restore completed."
    Write-Host "Mode: $($connection.Mode)"
    Write-Host "- $SourceAuthDb -> $TargetAuthDb"
    Write-Host "- $SourcePosDb -> $TargetPosDb"
} finally {
    foreach ($file in $tempFiles) {
        Remove-Item -Force -ErrorAction SilentlyContinue $file
    }
}

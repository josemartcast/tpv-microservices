param(
    [ValidateSet("auto", "native", "docker")]
    [string]$Mode = "auto",
    [string]$Container = "tpv-mysql",
    [string]$MysqlBinDir = "",
    [string]$RootUser = "root",
    [string]$RootPassword = "root",
    [string]$OutputRoot = ".backups",
    [string[]]$Databases = @("tpv_auth", "tpv_pos"),
    [switch]$Compress
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "db-common.ps1")

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

$connection = Resolve-DatabaseMode -Mode $Mode -Container $Container -MysqlBinDir $MysqlBinDir

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$targetDir = Join-Path $OutputRoot $timestamp
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

$meta = [ordered]@{
    createdAt = (Get-Date).ToString("o")
    host = $env:COMPUTERNAME
    gitCommit = ((git rev-parse --short HEAD) 2>$null)
    mode = $connection.Mode
    container = $Container
    mysqlBinDir = $connection.MysqlBinDir
    files = @()
}

foreach ($db in $Databases) {
    if (-not (Test-DatabaseExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $db)) {
        throw "La base de datos '$db' no existe o no es accesible."
    }

    $sqlPath = Join-Path $targetDir "$db.sql"
    Backup-DatabaseToFile -Connection $connection -User $RootUser -Password $RootPassword -DbName $db -OutputPath $sqlPath

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
Write-Host "Mode: $($connection.Mode)"
Write-Host "Files:"
foreach ($entry in $meta.files) {
    Write-Host ("- {0}: {1} ({2} bytes)" -f $entry.database, $entry.file, $entry.sizeBytes)
}

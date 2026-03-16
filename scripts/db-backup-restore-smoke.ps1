param(
    [ValidateSet("auto", "native", "docker")]
    [string]$Mode = "auto",
    [string]$Container = "tpv-mysql",
    [string]$MysqlBinDir = "",
    [string]$RootUser = "root",
    [string]$RootPassword = "root",
    [string]$OutputRoot = ".backups",
    [string]$SourceAuthDb = "tpv_auth",
    [string]$SourcePosDb = "tpv_pos",
    [switch]$CompressBackup,
    [switch]$KeepRestoredDbs
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "db-common.ps1")

function Compare-DatabaseData {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$SourceDb,
        [string]$TargetDb
    )
    $srcTables = Invoke-DatabaseList -Connection $Connection -User $User -Password $Password -Sql "SELECT table_name FROM information_schema.tables WHERE table_schema = '$SourceDb' ORDER BY table_name;"
    $dstTables = Invoke-DatabaseList -Connection $Connection -User $User -Password $Password -Sql "SELECT table_name FROM information_schema.tables WHERE table_schema = '$TargetDb' ORDER BY table_name;"

    $srcSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $dstSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($t in $srcTables) { $null = $srcSet.Add($t) }
    foreach ($t in $dstTables) { $null = $dstSet.Add($t) }

    $missingInTarget = @($srcTables | Where-Object { -not $dstSet.Contains($_) })
    $extraInTarget = @($dstTables | Where-Object { -not $srcSet.Contains($_) })

    if ($missingInTarget.Count -gt 0 -or $extraInTarget.Count -gt 0) {
        throw "Table set mismatch for $SourceDb -> $TargetDb. MissingInTarget=[$($missingInTarget -join ', ')] ExtraInTarget=[$($extraInTarget -join ', ')]"
    }

    $mismatches = New-Object System.Collections.Generic.List[string]
    foreach ($table in $srcTables) {
        $srcCount = Invoke-DatabaseScalar -Connection $Connection -User $User -Password $Password -Sql "SELECT COUNT(*) FROM $SourceDb.$table;"
        $dstCount = Invoke-DatabaseScalar -Connection $Connection -User $User -Password $Password -Sql "SELECT COUNT(*) FROM $TargetDb.$table;"
        if ($srcCount -ne $dstCount) {
            $mismatches.Add("${table}: source=$srcCount target=$dstCount")
        }
    }

    if ($mismatches.Count -gt 0) {
        $detail = @($mismatches | Select-Object -First 20) -join "; "
        throw "Row count mismatch for $SourceDb -> $TargetDb. $detail"
    }

    return $srcTables.Count
}

$connection = Resolve-DatabaseMode -Mode $Mode -Container $Container -MysqlBinDir $MysqlBinDir

if (-not (Test-DatabaseExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $SourceAuthDb)) {
    throw "La base origen '$SourceAuthDb' no existe."
}
if (-not (Test-DatabaseExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $SourcePosDb)) {
    throw "La base origen '$SourcePosDb' no existe."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backupScript = Join-Path $PSScriptRoot "db-backup.ps1"
$restoreScript = Join-Path $PSScriptRoot "db-restore.ps1"
$outputRootPath = if ([System.IO.Path]::IsPathRooted($OutputRoot)) {
    $OutputRoot
} else {
    Join-Path $repoRoot $OutputRoot
}
New-Item -ItemType Directory -Force -Path $outputRootPath | Out-Null

if (-not (Test-Path $backupScript)) { throw "Backup script not found: $backupScript" }
if (-not (Test-Path $restoreScript)) { throw "Restore script not found: $restoreScript" }

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$targetAuthDb = "${SourceAuthDb}_smoke_$timestamp"
$targetPosDb = "${SourcePosDb}_smoke_$timestamp"

try {
    Write-Host "== Backup/Restore smoke =="
    Write-Host "Source DBs: $SourceAuthDb, $SourcePosDb"
    Write-Host "Target DBs: $targetAuthDb, $targetPosDb"

    $beforeDirs = @(Get-ChildItem -Path $outputRootPath -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    $backupParams = @{
        Mode = $Mode
        Container = $Container
        MysqlBinDir = $MysqlBinDir
        RootUser = $RootUser
        RootPassword = $RootPassword
        OutputRoot = $outputRootPath
        Databases = @($SourceAuthDb, $SourcePosDb)
    }
    if ($CompressBackup) {
        $backupParams["Compress"] = $true
    }
    & $backupScript @backupParams
    $backupOutputText = "(see console output above)"

    $afterDirs = @(Get-ChildItem -Path $outputRootPath -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    $newDirs = @($afterDirs | Where-Object { $beforeDirs -notcontains $_ })
    if ($newDirs.Count -eq 0) {
        throw "Could not locate newly created backup directory under '$outputRootPath'."
    }
    $backupDir = ($newDirs | Sort-Object | Select-Object -Last 1)
    if (-not (Test-Path $backupDir)) {
        throw "Backup directory does not exist: $backupDir"
    }
    Write-Host "Backup dir: $backupDir"

    Drop-DatabaseIfExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $targetAuthDb
    Drop-DatabaseIfExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $targetPosDb

    $restoreParams = @{
        BackupDir = $backupDir
        Mode = $Mode
        Container = $Container
        MysqlBinDir = $MysqlBinDir
        RootUser = $RootUser
        RootPassword = $RootPassword
        SourceAuthDb = $SourceAuthDb
        SourcePosDb = $SourcePosDb
        TargetAuthDb = $targetAuthDb
        TargetPosDb = $targetPosDb
    }
    & $restoreScript @restoreParams
    $restoreOutputText = "(see console output above)"

    $authTables = Compare-DatabaseData -Connection $connection -User $RootUser -Password $RootPassword -SourceDb $SourceAuthDb -TargetDb $targetAuthDb
    $posTables = Compare-DatabaseData -Connection $connection -User $RootUser -Password $RootPassword -SourceDb $SourcePosDb -TargetDb $targetPosDb

    Write-Host "Smoke OK."
    Write-Host "- Mode: $($connection.Mode)"
    Write-Host "- Auth tables verified: $authTables"
    Write-Host "- Pos tables verified: $posTables"
    Write-Host "- Backup directory: $backupDir"
    Write-Host ""
    Write-Host "Backup output:"
    Write-Host $backupOutputText
    Write-Host "Restore output:"
    Write-Host $restoreOutputText
}
finally {
    if (-not $KeepRestoredDbs) {
        try {
            Drop-DatabaseIfExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $targetAuthDb
            Drop-DatabaseIfExists -Connection $connection -User $RootUser -Password $RootPassword -DbName $targetPosDb
            Write-Host "Temporary restored databases dropped."
        } catch {
            Write-Warning "Could not cleanup temporary databases: $($_.Exception.Message)"
        }
    } else {
        Write-Host "Temporary restored databases kept (requested)."
    }
}

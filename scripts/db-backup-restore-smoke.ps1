param(
    [string]$Container = "tpv-mysql",
    [string]$RootPassword = "root",
    [string]$OutputRoot = ".backups",
    [string]$SourceAuthDb = "tpv_auth",
    [string]$SourcePosDb = "tpv_pos",
    [switch]$CompressBackup,
    [switch]$KeepRestoredDbs
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

function Invoke-MySqlScalar {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$Sql
    )
    $result = Invoke-DockerChecked -DockerArgs @(
        "exec", $ContainerName, "mysql", "-uroot", "-p$Password", "-Nse", $Sql
    )
    return $result.Trim()
}

function Invoke-MySqlList {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$Sql
    )
    $raw = Invoke-DockerChecked -DockerArgs @(
        "exec", $ContainerName, "mysql", "-uroot", "-p$Password", "-Nse", $Sql
    )
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }
    return @($raw -split "(`r`n|`n|`r)" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_.Trim() })
}

function Drop-DatabaseIfExists {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$DbName
    )
    $null = Invoke-DockerChecked -DockerArgs @(
        "exec", $ContainerName, "mysql", "-uroot", "-p$Password", "-e", "DROP DATABASE IF EXISTS $DbName;"
    )
}

function Assert-DbExists {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$DbName
    )
    $db = Invoke-MySqlScalar -ContainerName $ContainerName -Password $Password -Sql "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$DbName';"
    if ($db -ne $DbName) {
        throw "Database '$DbName' does not exist in container '$ContainerName'."
    }
}

function Compare-DatabaseData {
    param(
        [string]$ContainerName,
        [string]$Password,
        [string]$SourceDb,
        [string]$TargetDb
    )
    $srcTables = Invoke-MySqlList -ContainerName $ContainerName -Password $Password -Sql "SELECT table_name FROM information_schema.tables WHERE table_schema = '$SourceDb' ORDER BY table_name;"
    $dstTables = Invoke-MySqlList -ContainerName $ContainerName -Password $Password -Sql "SELECT table_name FROM information_schema.tables WHERE table_schema = '$TargetDb' ORDER BY table_name;"

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
        $srcCount = Invoke-MySqlScalar -ContainerName $ContainerName -Password $Password -Sql "SELECT COUNT(*) FROM $SourceDb.$table;"
        $dstCount = Invoke-MySqlScalar -ContainerName $ContainerName -Password $Password -Sql "SELECT COUNT(*) FROM $TargetDb.$table;"
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

if (-not (Test-ContainerRunning -Name $Container)) {
    throw "Container '$Container' is not running."
}

Assert-DbExists -ContainerName $Container -Password $RootPassword -DbName $SourceAuthDb
Assert-DbExists -ContainerName $Container -Password $RootPassword -DbName $SourcePosDb

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
        Container = $Container
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

    Drop-DatabaseIfExists -ContainerName $Container -Password $RootPassword -DbName $targetAuthDb
    Drop-DatabaseIfExists -ContainerName $Container -Password $RootPassword -DbName $targetPosDb

    $restoreParams = @{
        BackupDir = $backupDir
        Container = $Container
        RootPassword = $RootPassword
        SourceAuthDb = $SourceAuthDb
        SourcePosDb = $SourcePosDb
        TargetAuthDb = $targetAuthDb
        TargetPosDb = $targetPosDb
    }
    & $restoreScript @restoreParams
    $restoreOutputText = "(see console output above)"

    $authTables = Compare-DatabaseData -ContainerName $Container -Password $RootPassword -SourceDb $SourceAuthDb -TargetDb $targetAuthDb
    $posTables = Compare-DatabaseData -ContainerName $Container -Password $RootPassword -SourceDb $SourcePosDb -TargetDb $targetPosDb

    Write-Host "Smoke OK."
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
            Drop-DatabaseIfExists -ContainerName $Container -Password $RootPassword -DbName $targetAuthDb
            Drop-DatabaseIfExists -ContainerName $Container -Password $RootPassword -DbName $targetPosDb
            Write-Host "Temporary restored databases dropped."
        } catch {
            Write-Warning "Could not cleanup temporary databases: $($_.Exception.Message)"
        }
    } else {
        Write-Host "Temporary restored databases kept (requested)."
    }
}

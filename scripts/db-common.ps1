function Invoke-CheckedProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string[]]$ArgumentList = @(),
        [string]$StdOutPath = "",
        [string]$StdErrPath = ""
    )

    $startInfo = @{
        FilePath = $FilePath
        ArgumentList = $ArgumentList
        Wait = $true
        PassThru = $true
        NoNewWindow = $true
    }
    if (-not [string]::IsNullOrWhiteSpace($StdOutPath)) {
        $startInfo.RedirectStandardOutput = $StdOutPath
    }
    if (-not [string]::IsNullOrWhiteSpace($StdErrPath)) {
        $startInfo.RedirectStandardError = $StdErrPath
    }

    $proc = Start-Process @startInfo
    if ($proc.ExitCode -ne 0) {
        $err = ""
        if (-not [string]::IsNullOrWhiteSpace($StdErrPath) -and (Test-Path $StdErrPath)) {
            $err = Get-Content -Path $StdErrPath -Raw
        }
        throw "$FilePath $($ArgumentList -join ' ') failed (exit $($proc.ExitCode)): $err"
    }
}

function Invoke-DockerCapture {
    param(
        [string[]]$ArgumentList,
        [string]$StdOutPath,
        [string]$StdErrPath
    )

    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & docker @ArgumentList 2> $StdErrPath | Out-File -FilePath $StdOutPath -Encoding utf8
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }

    if ($exitCode -ne 0) {
        $err = if (Test-Path $StdErrPath) { Get-Content -Path $StdErrPath -Raw } else { "" }
        throw "docker $($ArgumentList -join ' ') failed (exit $exitCode): $err"
    }
}

function Test-DockerContainerRunning {
    param([string]$Container)
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        return $false
    }
    $id = (& docker ps -q -f "name=^$Container$").Trim()
    return -not [string]::IsNullOrWhiteSpace($id)
}

function Resolve-NativeMySqlBinDir {
    param([string]$Preferred = "")

    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($Preferred)) {
        $candidates.Add($Preferred)
    }
    if ($env:TPV_MYSQL_BIN) {
        $candidates.Add($env:TPV_MYSQL_BIN)
    }

    $scriptRootParent = Split-Path -Parent $PSScriptRoot
    if ($scriptRootParent) {
        $candidates.Add((Join-Path $scriptRootParent "mysql\bin"))
    }
    $candidates.Add("C:\TPV-Bar\mysql\bin")

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $mysql = Join-Path $candidate "mysql.exe"
        $dump = Join-Path $candidate "mysqldump.exe"
        if ((Test-Path $mysql) -and (Test-Path $dump)) {
            return $candidate
        }
    }

    $mysqlCmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    $dumpCmd = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($mysqlCmd -and $dumpCmd) {
        return Split-Path -Parent $mysqlCmd.Source
    }

    return $null
}

function Resolve-DatabaseMode {
    param(
        [string]$Mode = "auto",
        [string]$Container = "tpv-mysql",
        [string]$MysqlBinDir = ""
    )

    $normalized = $Mode.ToLowerInvariant()
    $nativeBin = Resolve-NativeMySqlBinDir -Preferred $MysqlBinDir

    switch ($normalized) {
        "native" {
            if (-not $nativeBin) {
                throw "No se encontro cliente MySQL nativo."
            }
            return @{ Mode = "native"; MysqlBinDir = $nativeBin; Container = $Container }
        }
        "docker" {
            if (-not (Test-DockerContainerRunning -Container $Container)) {
                throw "El contenedor Docker '$Container' no esta en ejecucion."
            }
            return @{ Mode = "docker"; MysqlBinDir = $null; Container = $Container }
        }
        default {
            if ($nativeBin) {
                return @{ Mode = "native"; MysqlBinDir = $nativeBin; Container = $Container }
            }
            if (Test-DockerContainerRunning -Container $Container) {
                return @{ Mode = "docker"; MysqlBinDir = $null; Container = $Container }
            }
            throw "No se encontro ni MySQL nativo ni contenedor Docker '$Container'."
        }
    }
}

function Invoke-DatabaseQuery {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$Sql,
        [string]$Database = "",
        [switch]$TabSeparated
    )

    $tmpOut = [System.IO.Path]::GetTempFileName()
    $tmpErr = [System.IO.Path]::GetTempFileName()
    try {
        if ($Connection.Mode -eq "native") {
            $mysqlExe = Join-Path $Connection.MysqlBinDir "mysql.exe"
            $args = @(
                "--protocol=TCP",
                "-h127.0.0.1",
                "-P3306",
                "-u$User",
                "-p$Password",
                "--default-character-set=utf8mb4"
            )
            if ($TabSeparated) {
                $args += "-Nse"
                $args += $Sql
            } else {
                if (-not [string]::IsNullOrWhiteSpace($Database)) {
                    $args += $Database
                }
                $args += "-e"
                $args += $Sql
            }
            Invoke-CheckedProcess -FilePath $mysqlExe -ArgumentList $args -StdOutPath $tmpOut -StdErrPath $tmpErr
        } else {
            $dockerArgs = @(
                "exec", $Connection.Container, "mysql",
                "-uroot", "-p$Password",
                "--default-character-set=utf8mb4"
            )
            if ($TabSeparated) {
                $dockerArgs += "-Nse"
                $dockerArgs += $Sql
            } else {
                if (-not [string]::IsNullOrWhiteSpace($Database)) {
                    $dockerArgs += $Database
                }
                $dockerArgs += "-e"
                $dockerArgs += $Sql
            }
            Invoke-DockerCapture -ArgumentList $dockerArgs -StdOutPath $tmpOut -StdErrPath $tmpErr
        }
        return (Get-Content -Path $tmpOut -Raw)
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $tmpOut, $tmpErr
    }
}

function Test-DatabaseExists {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$DbName
    )
    try {
        $null = Invoke-DatabaseQuery -Connection $Connection -User $User -Password $Password -Sql "SELECT 1" -Database $DbName -TabSeparated
        return $true
    } catch {
        if ($_.Exception.Message -match "Unknown database|Access denied") {
            return $false
        }
        throw
    }
}

function Invoke-DatabaseScalar {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$Sql
    )
    return (Invoke-DatabaseQuery -Connection $Connection -User $User -Password $Password -Sql $Sql -TabSeparated).Trim()
}

function Invoke-DatabaseList {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$Sql
    )
    $raw = Invoke-DatabaseQuery -Connection $Connection -User $User -Password $Password -Sql $Sql -TabSeparated
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }
    return @($raw -split "(`r`n|`n|`r)" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_.Trim() })
}

function Ensure-Database {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$DbName
    )
    $safeDbName = $DbName.Replace('`', '``')
    $sql = "CREATE DATABASE IF NOT EXISTS ``$safeDbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    $null = Invoke-DatabaseQuery -Connection $Connection -User $User -Password $Password -Sql $sql
}

function Drop-DatabaseIfExists {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$DbName
    )
    $safeDbName = $DbName.Replace('`', '``')
    $sql = "DROP DATABASE IF EXISTS ``$safeDbName``;"
    $null = Invoke-DatabaseQuery -Connection $Connection -User $User -Password $Password -Sql $sql
}

function Backup-DatabaseToFile {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$DbName,
        [string]$OutputPath
    )

    $errPath = "$OutputPath.err.log"
    try {
        if ($Connection.Mode -eq "native") {
            $dumpExe = Join-Path $Connection.MysqlBinDir "mysqldump.exe"
            $args = @(
                "--protocol=TCP",
                "-h127.0.0.1",
                "-P3306",
                "-u$User",
                "-p$Password",
                "--single-transaction",
                "--quick",
                "--routines",
                "--triggers",
                "--events",
                "--set-gtid-purged=OFF",
                "--default-character-set=utf8mb4",
                $DbName
            )
            Invoke-CheckedProcess -FilePath $dumpExe -ArgumentList $args -StdOutPath $OutputPath -StdErrPath $errPath
        } else {
            $dockerArgs = @(
                "exec", $Connection.Container, "mysqldump",
                "-uroot", "-p$Password",
                "--single-transaction", "--quick",
                "--routines", "--triggers", "--events",
                "--set-gtid-purged=OFF",
                "--default-character-set=utf8mb4",
                $DbName
            )
            Invoke-DockerCapture -ArgumentList $dockerArgs -StdOutPath $OutputPath -StdErrPath $errPath
        }
        if ((Get-Item $OutputPath).Length -le 0) {
            throw "El backup quedo vacio: $OutputPath"
        }
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $errPath
    }
}

function Restore-DatabaseFromFile {
    param(
        [hashtable]$Connection,
        [string]$User,
        [string]$Password,
        [string]$DbName,
        [string]$SqlPath
    )

    $tmpErr = [System.IO.Path]::GetTempFileName()
    try {
        if ($Connection.Mode -eq "native") {
            $mysqlExe = Join-Path $Connection.MysqlBinDir "mysql.exe"
            $args = @(
                "--protocol=TCP",
                "-h127.0.0.1",
                "-P3306",
                "-u$User",
                "-p$Password",
                "--default-character-set=utf8mb4",
                $DbName
            )
            $proc = Start-Process -FilePath $mysqlExe `
                -ArgumentList $args `
                -RedirectStandardInput $SqlPath `
                -RedirectStandardError $tmpErr `
                -NoNewWindow `
                -Wait `
                -PassThru
            if ($proc.ExitCode -ne 0) {
                $err = Get-Content -Path $tmpErr -Raw
                throw "mysql restore fallo (exit $($proc.ExitCode)): $err"
            }
        } else {
            $containerSql = "/tmp/restore-$DbName-$([Guid]::NewGuid().ToString('N')).sql"
            Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("cp", $SqlPath, "$($Connection.Container):$containerSql")
            try {
                $dockerArgs = @(
                    "exec", $Connection.Container, "mysql",
                    "-uroot", "-p$Password",
                    "--default-character-set=utf8mb4",
                    $DbName,
                    "-e", "source $containerSql"
                )
                $tmpOut = [System.IO.Path]::GetTempFileName()
                try {
                    Invoke-DockerCapture -ArgumentList $dockerArgs -StdOutPath $tmpOut -StdErrPath $tmpErr
                } finally {
                    Remove-Item -Force -ErrorAction SilentlyContinue $tmpOut
                }
            } finally {
                try {
                    Invoke-CheckedProcess -FilePath "docker" -ArgumentList @("exec", $Connection.Container, "rm", "-f", $containerSql)
                } catch {
                }
            }
        }
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $tmpErr
    }
}

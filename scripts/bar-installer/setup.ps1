param(
    [switch]$InstallTailscale,
    [string]$TailscaleAuthKey = "",
    [switch]$SkipPrereqs
)

$ErrorActionPreference = "Stop"

function Assert-Admin {
    $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentIdentity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        $args = @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $PSCommandPath
        )
        if ($InstallTailscale) {
            $args += "-InstallTailscale"
        }
        if (-not [string]::IsNullOrWhiteSpace($TailscaleAuthKey)) {
            $args += "-TailscaleAuthKey"
            $args += $TailscaleAuthKey
        }
        if ($SkipPrereqs) {
            $args += "-SkipPrereqs"
        }

        try {
            $proc = Start-Process -FilePath "powershell.exe" -Verb RunAs -ArgumentList $args -Wait -PassThru
            exit $proc.ExitCode
        } catch {
            throw "La instalacion requiere permisos de administrador. Acepta el aviso de UAC para continuar."
        }
    }
}

function New-Shortcut {
    param(
        [string]$Path,
        [string]$TargetPath,
        [string]$Arguments = "",
        [string]$WorkingDirectory = ""
    )
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($Path)
    $shortcut.TargetPath = $TargetPath
    $shortcut.Arguments = $Arguments
    if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $shortcut.WorkingDirectory = $WorkingDirectory
    }
    $shortcut.Save()
}

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host "`n==> $Title" -ForegroundColor Cyan
    & $Action
}

function Stop-ExistingInstall {
    param([string]$Root)

    if (-not (Test-Path $Root)) {
        return
    }

    $stopBackend = Join-Path $Root "scripts\stop-backend.ps1"
    if (Test-Path $stopBackend) {
        try {
            Start-Process -FilePath "powershell.exe" `
                -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $stopBackend, "-Force") `
                -Wait -NoNewWindow | Out-Null
        } catch {
            Write-Warning "No se pudo ejecutar stop-backend.ps1: $($_.Exception.Message)"
        }
    }

    $stopDb = Join-Path $Root "scripts\stop-db.cmd"
    if (Test-Path $stopDb) {
        try {
            Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $stopDb) -Wait -NoNewWindow | Out-Null
        } catch {
            Write-Warning "No se pudo ejecutar stop-db.cmd: $($_.Exception.Message)"
        }
    }

    Start-Sleep -Seconds 2
}

Assert-Admin

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$payloadZip = Join-Path $scriptDir "tpv-bar-payload.zip"
if (-not (Test-Path $payloadZip)) {
    throw "No se encontro tpv-bar-payload.zip junto al instalador."
}

$installRoot = "C:\TPV-Bar"
$stagingRoot = Join-Path $env:TEMP "tpv-bar-setup"
$extractRoot = Join-Path $stagingRoot "payload"
$offlineMediaRoot = Join-Path $installRoot "prereqs"
$mySqlInitSql = Join-Path $installRoot "config\mysql-init.sql"

Invoke-Step "Preparando carpetas de instalacion" {
    if (Test-Path $stagingRoot) {
        Remove-Item $stagingRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $extractRoot | Out-Null
}

Invoke-Step "Extrayendo paquete TPV" {
    Expand-Archive -Path $payloadZip -DestinationPath $extractRoot -Force
}

Invoke-Step "Copiando archivos a $installRoot" {
    $reuseExistingRoot = $false
    if (Test-Path $installRoot) {
        Stop-ExistingInstall -Root $installRoot
        $backupPath = "$installRoot.bak-" + (Get-Date -Format "yyyyMMdd-HHmmss")
        try {
            Move-Item $installRoot $backupPath -ErrorAction Stop
            Write-Host "Instalacion anterior movida a: $backupPath" -ForegroundColor Yellow
        } catch {
            $reuseExistingRoot = $true
            Write-Warning "No se pudo mover '$installRoot' a backup. Se actualizara en el mismo directorio. Motivo: $($_.Exception.Message)"
        }
    }
    if (-not $reuseExistingRoot) {
        New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
    }
    try {
        Copy-Item (Join-Path $extractRoot "*") $installRoot -Recurse -Force -ErrorAction Stop
    } catch {
        throw "No se pudieron copiar los archivos al directorio de instalacion '$installRoot'. Cierra cualquier app TPV abierta e intentalo de nuevo. Detalle: $($_.Exception.Message)"
    }
}

if (-not $SkipPrereqs) {
    Invoke-Step "Instalando prerequisitos" {
        $prereqScript = Join-Path $installRoot "scripts\install-portatil-prereqs.ps1"
        if (-not (Test-Path $prereqScript)) {
            throw "No se encontro script de prerequisitos: $prereqScript"
        }
        $args = @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $prereqScript,
            "-OfflineMediaRoot", $offlineMediaRoot,
            "-JdkTargetRoot", (Join-Path $installRoot "jdk"),
            "-MySqlTargetRoot", (Join-Path $installRoot "mysql"),
            "-MySqlDataRoot", (Join-Path $installRoot "data\mysql"),
            "-MySqlInitSql", $mySqlInitSql
        )
        if ($InstallTailscale) {
            $args += "-InstallTailscale"
            if (-not [string]::IsNullOrWhiteSpace($TailscaleAuthKey)) {
                $args += "-TailscaleAuthKey"
                $args += $TailscaleAuthKey
            }
        }
        $proc = Start-Process -FilePath "powershell.exe" -ArgumentList $args -Wait -NoNewWindow -PassThru
        if ($proc.ExitCode -ne 0) {
            throw "La instalacion de prerequisitos fallo con codigo $($proc.ExitCode)."
        }
    }

    Invoke-Step "Validando servicio MySQL" {
        $service = Get-Service -Name "TPVMySQL" -ErrorAction SilentlyContinue
        if (-not $service) {
            throw "No se encontro el servicio TPVMySQL tras instalar prerequisitos."
        }
        if ($service.Status -ne "Running") {
            Start-Service -Name "TPVMySQL"
            Start-Sleep -Seconds 2
        }
    }
}

Invoke-Step "Instalando TPV Desktop" {
    $desktopInstaller = Get-ChildItem (Join-Path $installRoot "installers\TPV-Desktop-*.exe") -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $desktopInstaller) {
        throw "No se encontro instalador TPV Desktop en $installRoot\installers"
    }
    $proc = Start-Process -FilePath $desktopInstaller.FullName -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        throw "El instalador de TPV Desktop fallo con codigo $($proc.ExitCode)."
    }
}

Invoke-Step "Creando accesos directos" {
    $desktop = [Environment]::GetFolderPath("Desktop")
    New-Shortcut `
        -Path (Join-Path $desktop "Iniciar TPV (Backend + UI).lnk") `
        -TargetPath "cmd.exe" `
        -Arguments "/c `"$installRoot\scripts\start-all.cmd`"" `
        -WorkingDirectory "$installRoot\scripts"

    New-Shortcut `
        -Path (Join-Path $desktop "Parar Backend TPV.lnk") `
        -TargetPath "powershell.exe" `
        -Arguments "-NoProfile -ExecutionPolicy Bypass -File `"$installRoot\scripts\stop-backend.ps1`" -Force" `
        -WorkingDirectory "$installRoot\scripts"
}

Write-Host ""
Write-Host "Instalacion completada." -ForegroundColor Green
Write-Host "Ruta de instalacion: $installRoot"
if (Test-Path $offlineMediaRoot) {
    Write-Host "Prerequisitos offline: $offlineMediaRoot"
}
Write-Host "Siguiente paso: ejecutar 'Iniciar TPV (Backend + UI)' desde el escritorio."

param(
    [string]$Version = "1.0.0-rc2",
    [switch]$SkipBuild,
    [switch]$SkipDesktopInstaller,
    [switch]$BundlePrereqs
)

$ErrorActionPreference = "Stop"

function Invoke-Step([string]$Message, [scriptblock]$Action) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
    & $Action
}

function Invoke-MavenModule([string]$ModulePath, [string[]]$Goals) {
    $moduleAbs = Join-Path $RepoRoot $ModulePath
    Push-Location $moduleAbs
    try {
        & .\mvnw.cmd @Goals
        if ($LASTEXITCODE -ne 0) {
            throw "Maven fallo en $ModulePath ($($Goals -join ' '))"
        }
    } finally {
        Pop-Location
    }
}

function Resolve-BootJar([string]$ModulePath) {
    $target = Join-Path (Join-Path $RepoRoot $ModulePath) "target"
    $jar = Get-ChildItem $target -Filter "*.jar" -File |
        Where-Object {
            $_.Name -notmatch "original|sources|javadoc|plain"
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) {
        throw "No se encontro boot jar en $ModulePath\target"
    }
    return $jar.FullName
}

function Ensure-Command([string]$Name, [string]$InstallHint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "No se encontro '$Name'. $InstallHint"
    }
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$InstallerVersion = "1.0.0"
if ($Version -match "(\d+)\.(\d+)\.(\d+)") {
    $InstallerVersion = "$($Matches[1]).$($Matches[2]).$($Matches[3])"
}
$DistRoot = Join-Path $RepoRoot ("dist\bar-package-" + $Version)
$BackendOut = Join-Path $DistRoot "backend"
$ScriptsOut = Join-Path $DistRoot "scripts"
$InstallersOut = Join-Path $DistRoot "installers"
$DocsOut = Join-Path $DistRoot "docs"
$DesktopInput = Join-Path $DistRoot "_desktop_input"
$ConfigOut = Join-Path $DistRoot "config"
$PrereqsOut = Join-Path $DistRoot "prereqs"

Invoke-Step "Preparando estructura de salida" {
    if (Test-Path $DistRoot) {
        Remove-Item $DistRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $BackendOut | Out-Null
    New-Item -ItemType Directory -Path $ScriptsOut | Out-Null
    New-Item -ItemType Directory -Path $InstallersOut | Out-Null
    New-Item -ItemType Directory -Path $DocsOut | Out-Null
    New-Item -ItemType Directory -Path $DesktopInput | Out-Null
    New-Item -ItemType Directory -Path $ConfigOut | Out-Null
}

if (-not $SkipBuild) {
    Invoke-Step "Compilando auth-service" {
        Invoke-MavenModule "services\auth-service" @("clean", "package", "-DskipTests")
    }
    Invoke-Step "Compilando pos-service" {
        Invoke-MavenModule "services\pos-service" @("clean", "package", "-DskipTests")
    }
    Invoke-Step "Compilando gateway" {
        Invoke-MavenModule "gateway" @("clean", "package", "-DskipTests")
    }
    Invoke-Step "Compilando tpv-desktop y copiando dependencias runtime" {
        Invoke-MavenModule "tpv-desktop" @("clean", "package", "dependency:copy-dependencies", "-DincludeScope=runtime", "-DskipTests")
    }
}

Invoke-Step "Copiando backend jars al paquete" {
    Copy-Item (Resolve-BootJar "services\auth-service") (Join-Path $BackendOut "auth-service.jar") -Force
    Copy-Item (Resolve-BootJar "services\pos-service") (Join-Path $BackendOut "pos-service.jar") -Force
    Copy-Item (Resolve-BootJar "gateway") (Join-Path $BackendOut "gateway.jar") -Force
}

Invoke-Step "Copiando scripts operativos (start/stop + backup/restore)" {
    Copy-Item (Join-Path $RepoRoot "scripts\bar-runtime\start-db.cmd") (Join-Path $ScriptsOut "start-db.cmd") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\bar-runtime\start-backend.cmd") (Join-Path $ScriptsOut "start-backend.cmd") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\bar-runtime\start-all.cmd") (Join-Path $ScriptsOut "start-all.cmd") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\bar-runtime\configure-pda-https.ps1") (Join-Path $ScriptsOut "configure-pda-https.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\bar-runtime\stop-backend.ps1") (Join-Path $ScriptsOut "stop-backend.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\bar-runtime\stop-db.cmd") (Join-Path $ScriptsOut "stop-db.cmd") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\db-common.ps1") (Join-Path $ScriptsOut "db-common.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\db-backup.ps1") (Join-Path $ScriptsOut "db-backup.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\db-restore.ps1") (Join-Path $ScriptsOut "db-restore.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\db-backup-restore-smoke.ps1") (Join-Path $ScriptsOut "db-backup-restore-smoke.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\install-portatil-prereqs.ps1") (Join-Path $ScriptsOut "install-portatil-prereqs.ps1") -Force
    Copy-Item (Join-Path $RepoRoot "scripts\download-bar-prereqs.ps1") (Join-Path $ScriptsOut "download-bar-prereqs.ps1") -Force
}

Invoke-Step "Copiando configuracion de MySQL" {
    Copy-Item (Join-Path $RepoRoot "docker\mysql\init.sql") (Join-Path $ConfigOut "mysql-init.sql") -Force
}

if ($BundlePrereqs) {
    Invoke-Step "Copiando prerequisitos offline al paquete" {
        $sourcePrereqs = Join-Path $RepoRoot "dist\prereqs"
        if (-not (Test-Path $sourcePrereqs)) {
            throw "No existe dist\prereqs. Ejecuta antes scripts\download-bar-prereqs.ps1"
        }
        New-Item -ItemType Directory -Path $PrereqsOut -Force | Out-Null
        Copy-Item (Join-Path $sourcePrereqs "*") $PrereqsOut -Recurse -Force
    }
}

Invoke-Step "Copiando documentacion de instalacion" {
    Copy-Item (Join-Path $RepoRoot "docs\install-bar-windows.md") (Join-Path $DocsOut "install-bar-windows.md") -Force
    if (Test-Path (Join-Path $RepoRoot "docs\pda-tailscale-setup.md")) {
        Copy-Item (Join-Path $RepoRoot "docs\pda-tailscale-setup.md") (Join-Path $DocsOut "pda-tailscale-setup.md") -Force
    }
    if (Test-Path (Join-Path $RepoRoot "docs\backup-restore.md")) {
        Copy-Item (Join-Path $RepoRoot "docs\backup-restore.md") (Join-Path $DocsOut "backup-restore.md") -Force
    }
}

if (-not $SkipDesktopInstaller) {
    Invoke-Step "Generando instalador EXE TPV Desktop (jpackage)" {
        Ensure-Command "jpackage" "Instala JDK 21+ (Temurin) y reinicia terminal."

        $wixLocal = Join-Path $RepoRoot "tools\wix314"
        if ((Test-Path (Join-Path $wixLocal "candle.exe")) -and (Test-Path (Join-Path $wixLocal "light.exe"))) {
            $env:PATH = "$wixLocal;$env:PATH"
        }

        $desktopJar = Get-ChildItem (Join-Path $RepoRoot "tpv-desktop\target") -Filter "tpv-desktop-*.jar" -File |
            Where-Object { $_.Name -notmatch "original|sources|javadoc" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if (-not $desktopJar) {
            throw "No se encontro jar de tpv-desktop. Ejecuta build sin -SkipBuild."
        }

        $depsDir = Join-Path $RepoRoot "tpv-desktop\target\dependency"
        if (-not (Test-Path $depsDir)) {
            throw "No se encontro directorio de dependencias runtime: $depsDir"
        }

        Copy-Item $desktopJar.FullName (Join-Path $DesktopInput $desktopJar.Name) -Force
        Copy-Item (Join-Path $depsDir "*.jar") $DesktopInput -Force

        $jpackageArgs = @(
            "--type", "exe",
            "--name", "TPV-Desktop",
            "--app-version", $InstallerVersion,
            "--vendor", "TPV",
            "--dest", $InstallersOut,
            "--input", $DesktopInput,
            "--main-jar", $desktopJar.Name,
            "--main-class", "com.tpv.desktop.App",
            "--win-shortcut",
            "--win-menu",
            "--java-options", "-Dtpv.mode=real",
            "--java-options", "-Dtpv.auto.login=false",
            "--java-options", "-Dfile.encoding=UTF-8"
        )

        & jpackage @jpackageArgs
        if ($LASTEXITCODE -ne 0) {
            throw "jpackage fallo al generar el instalador EXE."
        }
    }
}

Invoke-Step "Generando ZIP final" {
    $zipPath = Join-Path $RepoRoot ("dist\bar-package-" + $Version + ".zip")
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $DistRoot "*") -DestinationPath $zipPath -Force
    Write-Host "ZIP generado: $zipPath" -ForegroundColor Green
}

Write-Host "`nPaquete listo en: $DistRoot" -ForegroundColor Green
Write-Host "Siguiente paso: abrir docs\\install-bar-windows.md y ejecutar instalacion en el portatil del bar." -ForegroundColor Yellow

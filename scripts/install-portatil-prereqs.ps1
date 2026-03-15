param(
    [switch]$InstallTailscale,
    [string]$TailscaleAuthKey = "",
    [switch]$SkipDocker,
    [string]$OfflineMediaRoot = "",
    [string]$JdkTargetRoot = "C:\TPV-Bar\jdk"
)

$ErrorActionPreference = "Stop"

function Test-Command {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Ensure-Winget {
    if (Test-Command "winget") {
        Write-Host "[OK] winget disponible"
        return
    }
    throw @"
winget no esta disponible en este equipo.

Instala App Installer desde Microsoft Store:
https://apps.microsoft.com/detail/9NBLGGH4NNS1

O bien ejecuta este script con -OfflineMediaRoot apuntando a una carpeta con prerequisitos descargados.
"@
}

function Is-WingetPackageInstalled {
    param([string]$PackageId)
    $output = & winget list --id $PackageId --exact 2>$null | Out-String
    return $output -match [Regex]::Escape($PackageId)
}

function Ensure-WingetPackage {
    param(
        [string]$PackageId,
        [string]$FriendlyName
    )
    if (Is-WingetPackageInstalled -PackageId $PackageId) {
        Write-Host "[OK] $FriendlyName ya instalado ($PackageId)"
        return
    }
    Write-Host "[INFO] Instalando $FriendlyName ($PackageId) con winget..."
    & winget install --id $PackageId -e --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo instalar $FriendlyName ($PackageId)."
    }
    Write-Host "[OK] $FriendlyName instalado"
}

function Add-MachinePathEntry {
    param([string]$Entry)
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $parts = @()
    if (-not [string]::IsNullOrWhiteSpace($machinePath)) {
        $parts = $machinePath.Split(';', [System.StringSplitOptions]::RemoveEmptyEntries)
    }
    if ($parts -contains $Entry) {
        return
    }
    $newPath = (($parts + $Entry) -join ';').Trim(';')
    [Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
}

function Install-JdkFromZip {
    param([string]$ZipPath, [string]$TargetRoot)

    if (-not (Test-Path $ZipPath)) {
        throw "No existe ZIP de JDK: $ZipPath"
    }

    $tempExtract = Join-Path $env:TEMP "tpv-jdk-extract"
    if (Test-Path $tempExtract) {
        Remove-Item $tempExtract -Recurse -Force
    }
    New-Item -ItemType Directory -Path $tempExtract | Out-Null

    Write-Host "[INFO] Extrayendo JDK desde $ZipPath..."
    Expand-Archive -Path $ZipPath -DestinationPath $tempExtract -Force

    $jdkHome = Get-ChildItem $tempExtract -Directory | Select-Object -First 1
    if (-not $jdkHome) {
        throw "No se encontro carpeta JDK al extraer $ZipPath"
    }

    if (Test-Path $TargetRoot) {
        Remove-Item $TargetRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $TargetRoot) -Force | Out-Null
    Move-Item $jdkHome.FullName $TargetRoot

    [Environment]::SetEnvironmentVariable("JAVA_HOME", $TargetRoot, "Machine")
    Add-MachinePathEntry -Entry (Join-Path $TargetRoot "bin")
    $env:JAVA_HOME = $TargetRoot
    $env:Path = (Join-Path $TargetRoot "bin") + ";" + $env:Path

    Write-Host "[OK] JDK desplegado en $TargetRoot"
}

function Install-DockerOffline {
    param([string]$InstallerPath)
    if (-not (Test-Path $InstallerPath)) {
        throw "No existe instalador Docker: $InstallerPath"
    }
    Write-Host "[INFO] Instalando Docker Desktop desde medio local..."
    $proc = Start-Process -FilePath $InstallerPath -ArgumentList "install", "--quiet", "--accept-license" -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        throw "Docker Desktop Installer devolvio codigo $($proc.ExitCode)"
    }
    Write-Host "[OK] Docker Desktop instalado"
}

function Install-TailscaleOffline {
    param([string]$MsiPath)
    if (-not (Test-Path $MsiPath)) {
        throw "No existe MSI de Tailscale: $MsiPath"
    }
    Write-Host "[INFO] Instalando Tailscale desde medio local..."
    $proc = Start-Process -FilePath "msiexec.exe" -ArgumentList "/i", $MsiPath, "/qn", "/norestart" -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        throw "MSI de Tailscale devolvio codigo $($proc.ExitCode)"
    }
    Write-Host "[OK] Tailscale instalado"
}

function Ensure-TailscaleLogin {
    param([string]$AuthKey)
    if (-not (Test-Command "tailscale")) {
        $tailscaleExe = "C:\Program Files\Tailscale\tailscale.exe"
        if (Test-Path $tailscaleExe) {
            $env:Path = "C:\Program Files\Tailscale;" + $env:Path
        }
    }

    if (-not (Test-Command "tailscale")) {
        throw "tailscale no esta en PATH despues de instalar."
    }

    if (-not [string]::IsNullOrWhiteSpace($AuthKey)) {
        Write-Host "[INFO] Ejecutando tailscale up con auth key..."
        & tailscale up --authkey $AuthKey --reset
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo completar tailscale up con auth key."
        }
        Write-Host "[OK] Tailscale conectado con auth key"
        return
    }

    Write-Host "[INFO] Abriendo login interactivo de Tailscale..."
    & tailscale up
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "tailscale up no termino en verde. Puedes completar login manual desde la app Tailscale."
    } else {
        Write-Host "[OK] Tailscale conectado"
    }
}

function Resolve-OfflineFile {
    param(
        [string]$Root,
        [string[]]$Patterns,
        [string]$FriendlyName
    )

    if ([string]::IsNullOrWhiteSpace($Root) -or -not (Test-Path $Root)) {
        return $null
    }

    foreach ($pattern in $Patterns) {
        $match = Get-ChildItem -Path $Root -File -Filter $pattern -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($match) {
            Write-Host "[OK] Medio offline encontrado para ${FriendlyName}: $($match.Name)"
            return $match.FullName
        }
    }

    return $null
}

$jdkZip = Resolve-OfflineFile -Root $OfflineMediaRoot -Patterns @("OpenJDK*windows*hotspot*.zip", "*jdk*windows*.zip", "*temurin*21*.zip") -FriendlyName "JDK 21"
$dockerInstaller = Resolve-OfflineFile -Root $OfflineMediaRoot -Patterns @("Docker Desktop Installer*.exe", "DockerDesktopInstaller*.exe") -FriendlyName "Docker Desktop"
$tailscaleInstaller = Resolve-OfflineFile -Root $OfflineMediaRoot -Patterns @("tailscale-setup-*-amd64.msi", "tailscale-setup-latest-amd64.msi") -FriendlyName "Tailscale"

if ($jdkZip) {
    Install-JdkFromZip -ZipPath $jdkZip -TargetRoot $JdkTargetRoot
} else {
    Ensure-Winget
    Ensure-WingetPackage -PackageId "EclipseAdoptium.Temurin.21.JDK" -FriendlyName "JDK 21"
}

if (-not $SkipDocker) {
    if ($dockerInstaller) {
        Install-DockerOffline -InstallerPath $dockerInstaller
    } else {
        Ensure-Winget
        Ensure-WingetPackage -PackageId "Docker.DockerDesktop" -FriendlyName "Docker Desktop"
    }
}

if ($InstallTailscale) {
    if ($tailscaleInstaller) {
        Install-TailscaleOffline -MsiPath $tailscaleInstaller
    } else {
        Ensure-Winget
        Ensure-WingetPackage -PackageId "tailscale.tailscale" -FriendlyName "Tailscale"
    }
    Ensure-TailscaleLogin -AuthKey $TailscaleAuthKey
}

Write-Host ""
Write-Host "Instalacion de prerequisitos completada."
Write-Host "Siguiente paso recomendado:"
Write-Host "1) Reiniciar sesion de Windows si Docker o PATH no aparecen correctamente."
if (-not $SkipDocker) {
    Write-Host "2) Abrir Docker Desktop y esperar estado 'Engine running' la primera vez."
}
if ($InstallTailscale) {
    Write-Host "3) Verificar conectividad Tailscale desde movil PDA."
}

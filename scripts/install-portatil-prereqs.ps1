param(
    [switch]$InstallTailscale,
    [string]$TailscaleAuthKey = "",
    [switch]$SkipDocker
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

Luego vuelve a ejecutar este script.
"@
}

function Is-PackageInstalled {
    param([string]$PackageId)
    $output = & winget list --id $PackageId --exact 2>$null | Out-String
    return $output -match [Regex]::Escape($PackageId)
}

function Ensure-Package {
    param(
        [string]$PackageId,
        [string]$FriendlyName
    )
    if (Is-PackageInstalled -PackageId $PackageId) {
        Write-Host "[OK] $FriendlyName ya instalado ($PackageId)"
        return
    }
    Write-Host "[INFO] Instalando $FriendlyName ($PackageId)..."
    & winget install --id $PackageId -e --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo instalar $FriendlyName ($PackageId)."
    }
    Write-Host "[OK] $FriendlyName instalado"
}

function Ensure-TailscaleLogin {
    param([string]$AuthKey)
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

Ensure-Winget

Ensure-Package -PackageId "Git.Git" -FriendlyName "Git"
Ensure-Package -PackageId "EclipseAdoptium.Temurin.21.JDK" -FriendlyName "JDK 21"
if (-not $SkipDocker) {
    Ensure-Package -PackageId "Docker.DockerDesktop" -FriendlyName "Docker Desktop"
}

if ($InstallTailscale) {
    Ensure-Package -PackageId "tailscale.tailscale" -FriendlyName "Tailscale"
    Ensure-TailscaleLogin -AuthKey $TailscaleAuthKey
}

Write-Host ""
Write-Host "Instalacion de prerequisitos completada."
Write-Host "Siguiente paso recomendado:"
Write-Host "1) Reiniciar sesion de Windows (si java/git no aparecen en PATH)."
Write-Host "2) Abrir Docker Desktop y esperar estado 'Engine running'."
Write-Host "3) Clonar/actualizar repo TPV y arrancar servicios."
if ($InstallTailscale) {
    Write-Host "4) Verificar conectividad Tailscale desde movil PDA."
}


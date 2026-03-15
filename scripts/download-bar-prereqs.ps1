param(
    [string]$OutputDir = "",
    [switch]$SkipDocker,
    [switch]$SkipTailscale
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host "`n==> $Title" -ForegroundColor Cyan
    & $Action
}

function Download-File {
    param(
        [string]$Url,
        [string]$Destination
    )
    Write-Host "[INFO] Descargando $Url"
    curl.exe -L $Url -o $Destination
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Destination)) {
        throw "Fallo descargando $Url"
    }
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $RepoRoot "dist\prereqs"
}

Invoke-Step "Preparando carpeta de prerequisitos" {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

Invoke-Step "Resolviendo JDK 21 (Temurin) oficial" {
    $jdkAssets = Invoke-RestMethod -Uri "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&jvm_impl=hotspot&os=windows"
    $jdkZip = $jdkAssets |
        Where-Object { $_.binary.package.name -like "*.zip" } |
        Select-Object -First 1
    if (-not $jdkZip) {
        throw "No se pudo resolver ZIP de JDK 21 desde Adoptium API."
    }

    $jdkName = $jdkZip.binary.package.name
    $jdkUrl = $jdkZip.binary.package.link
    Download-File -Url $jdkUrl -Destination (Join-Path $OutputDir $jdkName)
}

if (-not $SkipDocker) {
    Invoke-Step "Descargando Docker Desktop oficial" {
        Download-File `
            -Url "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe" `
            -Destination (Join-Path $OutputDir "Docker Desktop Installer.exe")
    }
}

if (-not $SkipTailscale) {
    Invoke-Step "Resolviendo Tailscale MSI oficial" {
        $headers = curl.exe -I -L "https://pkgs.tailscale.com/stable/tailscale-setup-latest-amd64.msi" | Out-String
        $match = [regex]::Match($headers, "filename=""(?<name>tailscale-setup-[0-9.]+-amd64\.msi)""")
        $tailscaleName = "tailscale-setup-latest-amd64.msi"
        if ($match.Success) {
            $tailscaleName = $match.Groups["name"].Value
        }

        Download-File `
            -Url "https://pkgs.tailscale.com/stable/tailscale-setup-latest-amd64.msi" `
            -Destination (Join-Path $OutputDir $tailscaleName)
    }
}

Write-Host ""
Write-Host "Prerequisitos descargados en: $OutputDir" -ForegroundColor Green

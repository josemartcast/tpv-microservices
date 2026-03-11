param(
    [string]$BackendCommand = '',
    [string]$BackendWorkdir = (Get-Location).Path,
    [string]$BackendUrl = 'http://localhost:8080',
    [string]$CloudflaredPath = (Join-Path $PSScriptRoot 'tools\cloudflared.exe')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $CloudflaredPath)) {
    throw "No se encontro cloudflared en: $CloudflaredPath"
}

if (-not [string]::IsNullOrWhiteSpace($BackendCommand)) {
    Write-Host "Iniciando backend: $BackendCommand"
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $BackendCommand -WorkingDirectory $BackendWorkdir | Out-Null
    Start-Sleep -Seconds 5
} else {
    Write-Host 'BackendCommand vacio: se asume backend ya levantado en localhost:8080'
}

try {
    $status = (Invoke-WebRequest -Uri $BackendUrl -UseBasicParsing -TimeoutSec 5).StatusCode
    Write-Host "Backend responde con HTTP $status"
} catch {
    Write-Warning "No se pudo validar backend en $BackendUrl: $($_.Exception.Message)"
}

Write-Host "Abriendo Cloudflare Tunnel hacia $BackendUrl"
& $CloudflaredPath tunnel --url $BackendUrl --no-autoupdate

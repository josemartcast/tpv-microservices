param(
    [string]$GatewayUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"

function Write-Info([string]$Message) {
    Write-Host "[INFO] $Message"
}

function Write-Warn([string]$Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Get-TailscaleCommand {
    $cmd = Get-Command "tailscale" -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $fallback = "C:\Program Files\Tailscale\tailscale.exe"
    if (Test-Path $fallback) {
        return $fallback
    }
    return $null
}

function Invoke-Tailscale {
    param(
        [string]$Exe,
        [string[]]$CommandArgs
    )
    & $Exe @CommandArgs | Out-Host
    return [int]$LASTEXITCODE
}

$tailscaleExe = Get-TailscaleCommand
if (-not $tailscaleExe) {
    Write-Warn "Tailscale no esta instalado. Se omite configuracion HTTPS de PDA."
    exit 0
}

$normalizedGatewayUrl = $GatewayUrl.Trim()
if ([string]::IsNullOrWhiteSpace($normalizedGatewayUrl)) {
    $normalizedGatewayUrl = "http://127.0.0.1:8080"
}

$statusJsonRaw = & $tailscaleExe status --json 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($statusJsonRaw)) {
    Write-Warn "Tailscale no esta conectado. Ejecuta 'tailscale up' y vuelve a iniciar TPV."
    exit 0
}

try {
    $statusJson = $statusJsonRaw | ConvertFrom-Json
} catch {
    Write-Warn "No se pudo interpretar estado de Tailscale. Se omite configuracion HTTPS."
    exit 0
}

$dnsName = ""
if ($statusJson -and $statusJson.Self -and $statusJson.Self.DNSName) {
    $dnsName = [string]$statusJson.Self.DNSName
}
$dnsName = $dnsName.TrimEnd(".")

Write-Info "Configurando Tailscale Serve HTTPS -> $normalizedGatewayUrl"
$serveExit = Invoke-Tailscale -Exe $tailscaleExe -CommandArgs @("serve", "--yes", "--bg", "--https=443", $normalizedGatewayUrl)
if ($serveExit -ne 0) {
    Write-Warn "No se pudo configurar tailscale serve (codigo $serveExit)."
    exit 0
}

Write-Info "HTTPS activo en Tailscale Serve."
if (-not [string]::IsNullOrWhiteSpace($dnsName)) {
    Write-Host ""
    Write-Host "PDA web URL recomendada:" -ForegroundColor Green
    Write-Host "https://$dnsName/pda" -ForegroundColor Green
    Write-Host ""
}

exit 0

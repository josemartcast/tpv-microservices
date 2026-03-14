param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$jarNames = @(
    "auth-service.jar",
    "pos-service.jar",
    "gateway.jar"
)

$procs = Get-CimInstance Win32_Process | Where-Object {
    $proc = $_
    if ($proc.Name -ne "java.exe") {
        return $false
    }
    foreach ($jar in $jarNames) {
        if ($proc.CommandLine -like "*$jar*") {
            return $true
        }
    }
    return $false
}

if (-not $procs) {
    Write-Host "[INFO] No hay procesos backend activos." -ForegroundColor Yellow
    exit 0
}

foreach ($proc in $procs) {
    try {
        if ($Force) {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
        } else {
            Stop-Process -Id $proc.ProcessId -ErrorAction Stop
        }
        Write-Host "[OK] Proceso detenido: PID $($proc.ProcessId)" -ForegroundColor Green
    } catch {
        Write-Host "[WARN] No se pudo detener PID $($proc.ProcessId): $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

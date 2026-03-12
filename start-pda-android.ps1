param(
    [switch]$Clean,
    [switch]$Install,
    [switch]$OpenStudio
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Join-Path $repoRoot "pda-android"
$gradlew = Join-Path $projectDir "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    throw "No se encontro gradlew en $projectDir"
}

Push-Location $projectDir
try {
    function Invoke-GradleCommand([string[]]$GradleArgs) {
        & $gradlew @GradleArgs --no-daemon | Out-Host
        return [int]$LASTEXITCODE
    }

    function Clear-BuildDirs() {
        cmd /c "if exist app\\build rmdir /s /q app\\build"
        cmd /c "if exist build rmdir /s /q build"
    }

    if ($Clean) {
        Write-Host "[PDA] clean..."
        $cleanCode = Invoke-GradleCommand @("clean")
        if ($cleanCode -ne 0) { throw "[PDA] clean fallo con codigo $cleanCode" }
    }

    $taskArgs = if ($Install) { @(":app:installDebug") } else { @(":app:runDebug") }
    if ($Install) {
        Write-Host "[PDA] installDebug (requiere dispositivo/emulador ADB)..."
    } else {
        Write-Host "[PDA] runDebug (assembleDebug)..."
    }

    $firstCode = Invoke-GradleCommand $taskArgs
    if ($firstCode -ne 0) {
        Write-Warning "[PDA] primer intento fallido, limpiando build y reintentando..."
        Clear-BuildDirs
        $secondCode = Invoke-GradleCommand $taskArgs
        if ($secondCode -ne 0) { throw "[PDA] build fallo tras reintento, codigo $secondCode" }
    }

    $apkPath = Join-Path $projectDir "app\\build\\outputs\\apk\\debug\\app-debug.apk"
    if (-not $Install -and -not (Test-Path $apkPath)) {
        throw "[PDA] build finalizado sin APK en $apkPath"
    }

    Write-Host "[PDA] OK"
    if (-not $Install) {
        Write-Host "[PDA] APK: $apkPath"
    }
}
finally {
    Pop-Location
}

if ($OpenStudio) {
    $studioCandidates = @(
        "$Env:ProgramFiles\Android\Android Studio\bin\studio64.exe",
        "$Env:ProgramFiles\Android\Android Studio\bin\studio.exe"
    )
    $studioExe = $studioCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($studioExe) {
        Start-Process $studioExe -ArgumentList "`"$projectDir`""
    } else {
        Write-Warning "Android Studio no encontrado en rutas tipicas."
    }
}

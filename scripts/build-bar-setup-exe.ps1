param(
    [string]$Version = "1.0.0-rc2",
    [switch]$SkipPackageBuild
)

$ErrorActionPreference = "Stop"

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host "`n==> $Title" -ForegroundColor Cyan
    & $Action
}

function Ensure-CSharpCompiler {
    $csc = Get-Command csc.exe -ErrorAction SilentlyContinue
    if ($csc) {
        return $csc.Source
    }

    $frameworkCandidates = @(
        "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
        "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    )

    foreach ($candidate in $frameworkCandidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "No se encontro compilador C# (csc.exe)."
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$PayloadZip = Join-Path $RepoRoot ("dist\bar-package-" + $Version + ".zip")
$BuildDir = Join-Path $RepoRoot ("dist\setup-builder-" + $Version)
$BundleDir = Join-Path $BuildDir "bundle"
$BundleZip = Join-Path $BuildDir "bundle.zip"
$StubExe = Join-Path $BuildDir "TPV-Bar-Setup.stub.exe"
$OutputExe = Join-Path $RepoRoot ("dist\TPV-Bar-Setup-" + $Version + ".exe")
$BootstrapperCs = Join-Path $PSScriptRoot "bar-installer\SetupBootstrapper.cs"

if (-not $SkipPackageBuild) {
    Invoke-Step "Generando paquete base bar-package-$Version.zip" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "build-bar-installer.ps1") -Version $Version -BundlePrereqs
        if ($LASTEXITCODE -ne 0) {
            throw "Fallo build-bar-installer.ps1"
        }
    }
}

if (-not (Test-Path $PayloadZip)) {
    throw "No existe payload: $PayloadZip"
}

if (-not (Test-Path $BootstrapperCs)) {
    throw "No existe bootstrapper C#: $BootstrapperCs"
}

$cscPath = Ensure-CSharpCompiler

Invoke-Step "Preparando staging para instalador maestro" {
    if (Test-Path $BuildDir) {
        Remove-Item $BuildDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $BundleDir -Force | Out-Null

    Copy-Item $PayloadZip (Join-Path $BundleDir "tpv-bar-payload.zip") -Force
    Copy-Item (Join-Path $PSScriptRoot "bar-installer\setup.ps1") (Join-Path $BundleDir "setup.ps1") -Force
    Copy-Item (Join-Path $PSScriptRoot "bar-installer\setup-launcher.cmd") (Join-Path $BundleDir "setup-launcher.cmd") -Force
}

Invoke-Step "Generando bundle ZIP interno" {
    Compress-Archive -Path (Join-Path $BundleDir "*") -DestinationPath $BundleZip -Force
}

Invoke-Step "Compilando bootstrapper EXE" {
    & $cscPath /nologo /target:exe /out:$StubExe /reference:System.IO.Compression.FileSystem.dll $BootstrapperCs
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $StubExe)) {
        throw "No se pudo compilar el bootstrapper EXE."
    }
}

Invoke-Step "Adjuntando payload al EXE final" {
    $finalOutput = $OutputExe
    if (Test-Path $OutputExe) {
        try {
            Remove-Item $OutputExe -Force
        } catch {
            $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
            $finalOutput = Join-Path $RepoRoot ("dist\TPV-Bar-Setup-" + $Version + "-" + $timestamp + ".exe")
            Write-Warning "El instalador principal esta bloqueado. Se generara: $finalOutput"
        }
    }

    $marker = [System.Text.Encoding]::ASCII.GetBytes("TPVBUNDL")
    $payloadBytes = [System.IO.File]::ReadAllBytes($BundleZip)
    $lengthBytes = [System.BitConverter]::GetBytes([Int64]$payloadBytes.Length)

    $out = [System.IO.File]::Open($finalOutput, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        $stubBytes = [System.IO.File]::ReadAllBytes($StubExe)
        $out.Write($stubBytes, 0, $stubBytes.Length)
        $out.Write($payloadBytes, 0, $payloadBytes.Length)
        $out.Write($lengthBytes, 0, $lengthBytes.Length)
        $out.Write($marker, 0, $marker.Length)
    } finally {
        $out.Dispose()
    }

    $script:OutputExe = $finalOutput
}

Write-Host ""
Write-Host "Instalador maestro listo:" -ForegroundColor Green
Write-Host $OutputExe -ForegroundColor Green

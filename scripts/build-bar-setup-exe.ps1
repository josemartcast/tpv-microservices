param(
    [string]$Version = "1.0.0-rc2",
    [switch]$SkipPackageBuild
)

$ErrorActionPreference = "Stop"

function Invoke-Step([string]$Title, [scriptblock]$Action) {
    Write-Host "`n==> $Title" -ForegroundColor Cyan
    & $Action
}

function Ensure-Command([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "No se encontro '$Name'. $Hint"
    }
}

$RepoRoot = Split-Path -Parent $PSScriptRoot
$PayloadZip = Join-Path $RepoRoot ("dist\bar-package-" + $Version + ".zip")
$BuildDir = Join-Path $RepoRoot ("dist\setup-builder-" + $Version)
$OutputExe = Join-Path $RepoRoot ("dist\TPV-Bar-Setup-" + $Version + ".exe")
$SedPath = Join-Path $BuildDir "tpv-bar-setup.sed"

if (-not $SkipPackageBuild) {
    Invoke-Step "Generando paquete base bar-package-$Version.zip" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "build-bar-installer.ps1") -Version $Version
        if ($LASTEXITCODE -ne 0) {
            throw "Fallo build-bar-installer.ps1"
        }
    }
}

if (-not (Test-Path $PayloadZip)) {
    throw "No existe payload: $PayloadZip"
}

Ensure-Command "iexpress.exe" "IExpress viene con Windows. Verifica C:\Windows\System32\iexpress.exe"

Invoke-Step "Preparando staging para instalador maestro" {
    if (Test-Path $BuildDir) {
        Remove-Item $BuildDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $BuildDir | Out-Null

    Copy-Item $PayloadZip (Join-Path $BuildDir "tpv-bar-payload.zip") -Force
    Copy-Item (Join-Path $PSScriptRoot "bar-installer\setup.ps1") (Join-Path $BuildDir "setup.ps1") -Force
    Copy-Item (Join-Path $PSScriptRoot "bar-installer\setup-launcher.cmd") (Join-Path $BuildDir "setup-launcher.cmd") -Force
}

$sourceDir = (Resolve-Path $BuildDir).Path + "\"
$targetExePath = (Resolve-Path (Join-Path $RepoRoot "dist")).Path + "\" + ("TPV-Bar-Setup-" + $Version + ".exe")

$sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=0
UseLongFileName=1
InsideCompressed=1
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=
DisplayLicense=
FinishMessage=Instalacion completada.
TargetName=$targetExePath
FriendlyName=TPV Bar Setup
AppLaunched=setup-launcher.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[SourceFiles]
SourceFiles0=$sourceDir
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
[Strings]
FILE0=setup-launcher.cmd
FILE1=setup.ps1
FILE2=tpv-bar-payload.zip
"@

Invoke-Step "Generando fichero SED" {
    Set-Content -Path $SedPath -Value $sed -Encoding ASCII
}

Invoke-Step "Construyendo TPV-Bar-Setup.exe con IExpress" {
    if (Test-Path $OutputExe) {
        Remove-Item $OutputExe -Force
    }

    $proc = Start-Process -FilePath "iexpress.exe" -ArgumentList @("/N", $SedPath) -Wait -PassThru
    $iexit = $proc.ExitCode

    # IExpress puede devolver exit code 1 aun generando correctamente el EXE.
    # Validamos por artefacto final para evitar falsos negativos.
    if (-not (Test-Path $OutputExe)) {
        if ($iexit -ne 0) {
            throw "IExpress devolvio codigo $iexit y no genero el instalador."
        }
        throw "No se genero el instalador esperado: $OutputExe"
    }

    if ($iexit -ne 0) {
        Write-Warning "IExpress devolvio codigo $iexit pero el instalador se genero correctamente."
    }
}

Write-Host ""
Write-Host "Instalador maestro listo:" -ForegroundColor Green
Write-Host $OutputExe -ForegroundColor Green

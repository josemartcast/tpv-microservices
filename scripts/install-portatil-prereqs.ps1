param(
    [switch]$InstallTailscale,
    [string]$TailscaleAuthKey = "",
    [string]$OfflineMediaRoot = "",
    [string]$JdkTargetRoot = "C:\TPV-Bar\jdk",
    [string]$MySqlTargetRoot = "C:\TPV-Bar\mysql",
    [string]$MySqlDataRoot = "C:\TPV-Bar\data\mysql",
    [string]$MySqlServiceName = "TPVMySQL",
    [string]$MySqlRootPassword = "root",
    [string]$MySqlAppUser = "tpv_user",
    [string]$MySqlAppPassword = "tpv_pass",
    [string]$MySqlInitSql = ""
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

function Expand-ZipToTemp {
    param([string]$ZipPath, [string]$TempName)
    $tempExtract = Join-Path $env:TEMP $TempName
    if (Test-Path $tempExtract) {
        Remove-Item $tempExtract -Recurse -Force
    }
    New-Item -ItemType Directory -Path $tempExtract | Out-Null
    Expand-Archive -Path $ZipPath -DestinationPath $tempExtract -Force
    return $tempExtract
}

function Install-JdkFromZip {
    param([string]$ZipPath, [string]$TargetRoot)

    if (-not (Test-Path $ZipPath)) {
        throw "No existe ZIP de JDK: $ZipPath"
    }

    $tempExtract = Expand-ZipToTemp -ZipPath $ZipPath -TempName "tpv-jdk-extract"
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
    $tailscaleExe = "C:\Program Files\Tailscale\tailscale.exe"
    if ((-not (Test-Command "tailscale")) -and (Test-Path $tailscaleExe)) {
        $env:Path = "C:\Program Files\Tailscale;" + $env:Path
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

function Write-MySqlConfig {
    param(
        [string]$ConfigPath,
        [string]$BaseDir,
        [string]$DataDir
    )

    $config = @"
[mysqld]
basedir=$(($BaseDir -replace '\\','/'))
datadir=$(($DataDir -replace '\\','/'))
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default_authentication_plugin=mysql_native_password
max_connections=100
sql-mode=

[client]
port=3306
default-character-set=utf8mb4
"@
    Set-Content -Path $ConfigPath -Value $config -Encoding ASCII
}

function Wait-MySqlReady {
    param(
        [string]$MySqlBin,
        [string]$RootPassword
    )
    $mysqladmin = Join-Path $MySqlBin "mysqladmin.exe"
    for ($i = 0; $i -lt 90; $i++) {
        & $mysqladmin "--host=localhost" "--port=3306" "--user=root" "--password=$RootPassword" ping --silent *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "MySQL no respondio a tiempo en 127.0.0.1:3306"
}

function Ensure-MySqlServiceAbsent {
    param([string]$ServiceName)
    $service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($service) {
        if ($service.Status -ne "Stopped") {
            Stop-Service -Name $ServiceName -Force -ErrorAction Stop
            Start-Sleep -Seconds 2
        }
        $mysqldPath = Join-Path $MySqlTargetRoot "bin\mysqld.exe"
        if (Test-Path $mysqldPath) {
            & $mysqldPath --remove $ServiceName *> $null
        } else {
            sc.exe delete $ServiceName *> $null
        }
        Start-Sleep -Seconds 2
    }
}

function Install-MySqlFromZip {
    param(
        [string]$ZipPath,
        [string]$TargetRoot,
        [string]$DataRoot,
        [string]$ServiceName,
        [string]$RootPassword,
        [string]$AppUser,
        [string]$AppPassword,
        [string]$InitSqlPath
    )

    if (-not (Test-Path $ZipPath)) {
        throw "No existe ZIP de MySQL: $ZipPath"
    }

    $tempExtract = Expand-ZipToTemp -ZipPath $ZipPath -TempName "tpv-mysql-extract"
    $mysqlHome = Get-ChildItem $tempExtract -Directory | Select-Object -First 1
    if (-not $mysqlHome) {
        throw "No se encontro carpeta MySQL al extraer $ZipPath"
    }

    Ensure-MySqlServiceAbsent -ServiceName $ServiceName

    if (Test-Path $TargetRoot) {
        Remove-Item $TargetRoot -Recurse -Force
    }
    if (Test-Path $DataRoot) {
        Remove-Item $DataRoot -Recurse -Force
    }

    New-Item -ItemType Directory -Path (Split-Path -Parent $TargetRoot) -Force | Out-Null
    New-Item -ItemType Directory -Path $DataRoot -Force | Out-Null
    Move-Item $mysqlHome.FullName $TargetRoot

    $configDir = Join-Path (Split-Path -Parent $TargetRoot) "config"
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
    $configPath = Join-Path $configDir "mysql-my.ini"
    Write-MySqlConfig -ConfigPath $configPath -BaseDir $TargetRoot -DataDir $DataRoot

    $binDir = Join-Path $TargetRoot "bin"
    $mysqld = Join-Path $binDir "mysqld.exe"
    $mysql = Join-Path $binDir "mysql.exe"

    Write-Host "[INFO] Inicializando MySQL..."
    & $mysqld "--defaults-file=$configPath" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) {
        throw "mysqld --initialize-insecure fallo con codigo $LASTEXITCODE"
    }

    Write-Host "[INFO] Registrando servicio MySQL '$ServiceName'..."
    & $mysqld "--install" $ServiceName "--defaults-file=$configPath"
    if ($LASTEXITCODE -ne 0) {
        throw "mysqld --install fallo con codigo $LASTEXITCODE"
    }

    Start-Service -Name $ServiceName

    for ($i = 0; $i -lt 90; $i++) {
        & $mysql "--host=localhost" "--port=3306" "--user=root" -e "SELECT 1;" *> $null
        if ($LASTEXITCODE -eq 0) {
            break
        }
        Start-Sleep -Seconds 2
    }

    $normalizedInitSql = ""
    if (-not [string]::IsNullOrWhiteSpace($InitSqlPath)) {
        $normalizedInitSql = $InitSqlPath -replace '\\','/'
    }

    $bootstrapSql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$RootPassword';
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '$RootPassword';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
CREATE USER IF NOT EXISTS '$AppUser'@'%' IDENTIFIED BY '$AppPassword';
CREATE USER IF NOT EXISTS '$AppUser'@'localhost' IDENTIFIED BY '$AppPassword';
CREATE USER IF NOT EXISTS '$AppUser'@'127.0.0.1' IDENTIFIED BY '$AppPassword';
"@
    if (-not [string]::IsNullOrWhiteSpace($normalizedInitSql)) {
        $bootstrapSql += "`nsource $normalizedInitSql;"
    }

    & $mysql "--host=localhost" "--port=3306" "--user=root" -e $bootstrapSql
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo bootstrapear MySQL."
    }

    Add-MachinePathEntry -Entry $binDir
    $env:Path = $binDir + ";" + $env:Path

    Write-Host "[OK] MySQL desplegado en $TargetRoot y servicio $ServiceName instalado"
}

$jdkZip = Resolve-OfflineFile -Root $OfflineMediaRoot -Patterns @("OpenJDK*windows*hotspot*.zip", "*jdk*windows*.zip", "*temurin*21*.zip") -FriendlyName "JDK 21"
$mysqlZip = Resolve-OfflineFile -Root $OfflineMediaRoot -Patterns @("mysql-*-winx64.zip") -FriendlyName "MySQL Server"
$tailscaleInstaller = Resolve-OfflineFile -Root $OfflineMediaRoot -Patterns @("tailscale-setup-*-amd64.msi", "tailscale-setup-latest-amd64.msi") -FriendlyName "Tailscale"

if ($jdkZip) {
    Install-JdkFromZip -ZipPath $jdkZip -TargetRoot $JdkTargetRoot
} else {
    Ensure-Winget
    Ensure-WingetPackage -PackageId "EclipseAdoptium.Temurin.21.JDK" -FriendlyName "JDK 21"
}

if ($mysqlZip) {
    Install-MySqlFromZip `
        -ZipPath $mysqlZip `
        -TargetRoot $MySqlTargetRoot `
        -DataRoot $MySqlDataRoot `
        -ServiceName $MySqlServiceName `
        -RootPassword $MySqlRootPassword `
        -AppUser $MySqlAppUser `
        -AppPassword $MySqlAppPassword `
        -InitSqlPath $MySqlInitSql
} else {
    throw "No se ha encontrado medio offline de MySQL. Ejecuta scripts\\download-bar-prereqs.ps1 antes de generar el instalador."
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
Write-Host "1) Reiniciar sesion de Windows si PATH o servicios no aparecen correctamente."
Write-Host "2) Verificar servicio MySQL '$MySqlServiceName' en ejecucion."
if ($InstallTailscale) {
    Write-Host "3) Verificar conectividad Tailscale desde movil PDA."
}

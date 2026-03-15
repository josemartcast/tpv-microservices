# Instalacion Windows Bar (TPV + Backend)

## 1. Generar instalador maestro en equipo de desarrollo

Desde la raiz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-bar-installer.ps1 -Version 1.0.0-rc2
powershell -ExecutionPolicy Bypass -File .\scripts\build-bar-setup-exe.ps1 -Version 1.0.0-rc2 -SkipPackageBuild
```

Salida:

- Carpeta: `dist\bar-package-1.0.0-rc2\`
- ZIP: `dist\bar-package-1.0.0-rc2.zip`
- Instalador maestro: `dist\TPV-Bar-Setup-1.0.0-rc2.exe`

## 2. Instalacion en portatil limpio (recomendado)

Copiar `dist\TPV-Bar-Setup-<version>.exe` al portatil destino y ejecutar **como Administrador**.

El instalador maestro hace:

- copia runtime a `C:\TPV-Bar`
- instala prerequisitos (Git, JDK 21, Docker Desktop; Tailscale opcional)
- instala TPV Desktop
- crea accesos directos en escritorio:
  - `Iniciar TPV (Backend + UI)`
  - `Parar Backend TPV`

## 3. Modo manual (si no usas instalador maestro)

Copiar el contenido de `dist\bar-package-<version>\` al portatil destino.

Abrir PowerShell como administrador y ejecutar:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-portatil-prereqs.ps1 -InstallTailscale
```

Esto instala:

- Git
- JDK 21
- Docker Desktop (si no existe)
- Tailscale (si se usa opcion `-InstallTailscale`)

## 4. Instalar TPV Desktop (manual)

En `.\installers\`, ejecutar el instalador `TPV-Desktop-*.exe`.

## 5. Arrancar sistema

### Backend (auth + pos + gateway)

```cmd
.\scripts\start-backend.cmd
```

### Todo (backend + abrir TPV Desktop)

```cmd
.\scripts\start-all.cmd
```

## 6. Parar backend

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-backend.ps1 -Force
```

## 7. Base de datos: backup y restore

Backup:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1 -Compress
```

Restore:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 -FilePath "C:\ruta\backup.zip"
```

## 8. Configuracion PDA

Mantener Tailscale activo en portatil y PDA.  
En la PDA usar base URL del gateway del portatil (Tailscale):

```text
http://<nombre-o-ip-tailscale>:8080
```

Si usas MagicDNS:

```text
http://<host>.tail<id>.ts.net:8080
```

## 9. Notas operativas

- No abrir puertos del router.
- Validar impresoras en `Settings` del TPV.
- Antes de cambios de catalogo/precios en produccion, ejecutar backup.

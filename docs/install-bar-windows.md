# Instalacion Windows en bar (TPV + backend)

Estado documentado a fecha: 2026-04-13.

## 1) Generar paquete en equipo de desarrollo

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\download-bar-prereqs.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-bar-installer.ps1 -Version 1.0.0-rcX -BundlePrereqs
powershell -ExecutionPolicy Bypass -File .\scripts\build-bar-setup-exe.ps1 -Version 1.0.0-rcX -SkipPackageBuild
```

Salida esperada:

- `dist\bar-package-1.0.0-rcX\`
- `dist\bar-package-1.0.0-rcX.zip`
- `dist\TPV-Bar-Setup-1.0.0-rcX.exe`

## 2) Instalar en portatil destino

1. Copiar `TPV-Bar-Setup-*.exe`.
2. Ejecutar **como Administrador**.
3. Esperar a que complete prerequisitos e instalacion TPV.

El instalador prepara normalmente:

- `C:\TPV-Bar\jdk`
- `C:\TPV-Bar\mysql`
- servicio MySQL `TPVMySQL`
- runtime scripts (`start-all.cmd`, `start-backend.cmd`, `stop-backend.ps1`)

## 3) Arranque y parada

Arranque completo:

```cmd
C:\TPV-Bar\scripts\bar-runtime\start-all.cmd
```

Solo backend:

```cmd
C:\TPV-Bar\scripts\bar-runtime\start-backend.cmd
```

Parada backend:

```powershell
powershell -ExecutionPolicy Bypass -File C:\TPV-Bar\scripts\bar-runtime\stop-backend.ps1 -Force
```

## 4) Comprobaciones post-instalacion

- Servicio `TPVMySQL` en running.
- `http://localhost:8080/pda` responde.
- TPV Desktop abre en modo real.
- Login admin funcional.
- Impresoras visibles y mapeadas en ajustes.

## 5) Problemas tipicos y solucion

### Error: debe ejecutarse como Administrador

- Cerrar instalador.
- Reabrir EXE con click derecho > Ejecutar como administrador.

### Error MSI 1638

- Ya existe version instalada.
- El instalador intenta modo interactivo; seguir asistente o desinstalar previa.

### Java no encontrado al iniciar

- Reejecutar instalador/prerequisitos.
- Verificar `C:\TPV-Bar\jdk` y variables de entorno.

### Backend no arranca

- Revisar `TPVMySQL`.
- Revisar logs en carpeta runtime/logs.

## 6) PDA remota

Configurar Tailscale en portatil y movil.

- PDA web: `http://<host>.tail<id>.ts.net:8080/pda`
- PDA nativa: `http://<host>.tail<id>.ts.net:8080`

## 7) Backup antes de operar

Antes de tocar catalogo/precios o actualizar version:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1 -Compress
```

Ver guia completa: `docs/backup-restore.md`.

# Runbook Backup/Restore MySQL (TPV)

Base de datos: MySQL en contenedor `tpv-mysql`  
BDs de negocio: `tpv_auth` y `tpv_pos`

## 1) Operativa diaria (backup)
Ejecutar al inicio o fin de turno:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1
```

Opcional con compresion:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1 -Compress
```

Resultado esperado:
- carpeta nueva en `.backups\yyyyMMdd-HHmmss\`
- `tpv_auth.sql` y `tpv_pos.sql` (o `.sql.gz`)
- `backup-meta.json`

## 2) Restore de prueba (recomendado antes de tocar produccion)
Nunca restaurar directo sobre produccion sin validar primero:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 `
  -BackupDir .\.backups\20260314-072537 `
  -TargetAuthDb tpv_auth_restore_test `
  -TargetPosDb tpv_pos_restore_test
```

## 3) Restore de incidencia sobre produccion
Usar solo en incidente real y con doble confirmacion operativa:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 `
  -BackupDir .\.backups\20260314-072537 `
  -AllowProductionRestore
```

## 4) Smoke automatico end-to-end (backup + restore + validacion)
Script recomendado para pre-release y QA operativo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup-restore-smoke.ps1 `
  -Container tpv-mysql `
  -RootPassword root `
  -OutputRoot .backups
```

Este smoke:
1. crea backup de `tpv_auth` y `tpv_pos`
2. restaura en BDs temporales `*_smoke_<timestamp>`
3. compara set de tablas y `COUNT(*)` por tabla entre origen y restaurada
4. elimina BDs temporales al finalizar

Para inspeccionar BDs temporales manualmente:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup-restore-smoke.ps1 -KeepRestoredDbs
```

## 5) Validacion SQL minima post-restore
Comprobaciones rapidas en `tpv_pos`:

```sql
SELECT COUNT(*) AS categories FROM categories;
SELECT COUNT(*) AS products FROM products;
SELECT COUNT(*) AS tickets FROM tickets;
SELECT COUNT(*) AS cash_sessions FROM cash_sessions;
```

En `tpv_auth`:

```sql
SELECT COUNT(*) AS users FROM users;
```

## 6) Integracion en TPV Desktop
En `Settings` hay seccion **Copias de seguridad** con:
- `Crear backup`
- `Restaurar backup`
- `Abrir carpeta backups`

La UI llama internamente a:
- `scripts/db-backup.ps1`
- `scripts/db-restore.ps1`

## 7) Checklist RC (obligatorio)
Antes de tag RC:
1. backup diario generado sin error
2. smoke `db-backup-restore-smoke.ps1` en verde
3. restore de prueba validado
4. evidencia guardada (ruta de backup + fecha/hora + responsable)

## 8) Notas de seguridad operativa
- No guardar backups en repositorio Git.
- Conservar al menos las ultimas 3 copias verificadas.
- Ejecutar restore productivo solo con ventana operativa definida.

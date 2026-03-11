# Backup y Restore de Base de Datos (pre-RC)

Este proyecto usa MySQL en el contenedor `tpv-mysql`.

## 1) Crear backup

Comando basico:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1
```

Opcional (gzip):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1 -Compress
```

Salida:
- Carpeta: `.backups\yyyyMMdd-HHmmss\`
- Ficheros: `tpv_auth.sql` y `tpv_pos.sql` (o `.sql.gz`)
- Metadata: `backup-meta.json`

## 2) Restaurar backup en BDs de prueba (recomendado)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 `
  -BackupDir .\.backups\20260311-120000 `
  -TargetAuthDb tpv_auth_restore_test `
  -TargetPosDb tpv_pos_restore_test
```

## 3) Restaurar sobre BDs productivas

Solo cuando quieras sobreescribir `tpv_auth`/`tpv_pos`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 `
  -BackupDir .\.backups\20260311-120000 `
  -AllowProductionRestore
```

## 4) Verificacion minima post-restore

```sql
SELECT COUNT(*) FROM products;
SELECT COUNT(*) FROM categories;
SELECT COUNT(*) FROM cash_sessions;
SELECT COUNT(*) FROM tickets;
```

## 5) Notas operativas

- No se borran BDs destino; se restaura via `mysql < dump.sql`.
- El restore crea la DB destino si no existe.
- Para RC se recomienda: backup + restore de prueba + validacion de conteos.

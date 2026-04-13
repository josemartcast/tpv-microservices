# Backup / Restore MySQL (TPV)

Estado documentado a fecha: 2026-04-13.

## Contexto

Bases de datos de negocio:

- `tpv_auth`
- `tpv_pos`

Soporte:

- MySQL nativo (`TPVMySQL`) en portatil de bar
- MySQL en docker para desarrollo

## 1) Crear backup

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1
```

Con compresion:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1 -Compress
```

Salida tipica:

- `.backups\yyyyMMdd-HHmmss\`
- `tpv_auth.sql` / `tpv_pos.sql` (o `.gz`)
- `backup-meta.json`

## 2) Restore en entorno de prueba (recomendado)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 `
  -BackupDir .\.backups\20260413-083000 `
  -TargetAuthDb tpv_auth_restore_test `
  -TargetPosDb tpv_pos_restore_test
```

## 3) Restore sobre produccion (solo incidente real)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-restore.ps1 `
  -BackupDir .\.backups\20260413-083000 `
  -AllowProductionRestore
```

## 4) Smoke automatizado backup+restore

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup-restore-smoke.ps1 -Mode auto -RootPassword root
```

Valida:

- dump
- restore temporal
- tablas y conteos
- limpieza final

## 5) Integracion en TPV Desktop

En Settings del TPV hay acciones para:

- crear backup
- restaurar backup
- abrir carpeta de backups

## 6) SQL de comprobacion rapida

`tpv_pos`:

```sql
SELECT COUNT(*) AS categories FROM categories;
SELECT COUNT(*) AS products FROM products;
SELECT COUNT(*) AS tickets FROM tickets;
SELECT COUNT(*) AS cash_sessions FROM cash_sessions;
```

`tpv_auth`:

```sql
SELECT COUNT(*) AS users FROM users;
```

## 7) Politica recomendada en bar

- Backup al inicio o cierre de turno.
- Backup obligatorio antes de:
  - actualizar version
  - carga masiva de catalogo
  - cambios fiscales
- Conservar minimo 3 copias verificadas.
- No subir backups al repositorio.

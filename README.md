# TPV Microservices (Desktop + PDA)

[![PDA E2E Smoke](https://github.com/josemartcast/tpv-microservices/actions/workflows/pda-e2e-smoke.yml/badge.svg)](https://github.com/josemartcast/tpv-microservices/actions/workflows/pda-e2e-smoke.yml)
[![DB Backup Restore Smoke](https://github.com/josemartcast/tpv-microservices/actions/workflows/db-backup-restore-smoke.yml/badge.svg)](https://github.com/josemartcast/tpv-microservices/actions/workflows/db-backup-restore-smoke.yml)

Repositorio principal del TPV para bar/restaurante.

Estado documentado a fecha: **2026-04-13**.

## Que incluye el proyecto

- `services/auth-service`: autenticacion JWT y administracion de usuarios/roles.
- `services/pos-service`: negocio TPV (mesas, tickets, caja, comandas, cobros, facturas, catalogo, salones, alias, impresoras, fiscal).
- `gateway`: entrada unica, proxy a servicios y hosting de PDA web en `/pda`.
- `tpv-desktop`: cliente JavaFX principal (operativa de caja y sala).
- `pda-android`: app nativa Android para camareros.
- `scripts/`: instalacion Windows, backup/restore, QA smoke.

## Funcionalidad actual (resumen)

- Mapa de mesas por salon con estados: libre, ocupada, bloqueada, pendiente envio, precuenta pedida.
- Locking multi terminal con heartbeat y recovery.
- Comandas por destino (BAR/COCINA/POSTRES) con autoimpresion y reimpresion.
- Cobro total, parcial y parcial por lineas.
- Reapertura de ticket pagado (roles autorizados) sin duplicar cobros ya registrados.
- Caja: apertura, incidencias, cierre con resumen impreso.
- Facturacion y reimpresion de facturas desde historial.
- Gestion de categorias/productos con IVA y destino de impresion.
- Gestion de usuarios y roles (ADMIN, ENCARGADO, CAJERO, CAMARERO).
- Gestion de salones y alias de mesa (alias visible en operativa, no en comprobante cliente).
- Backup/restore MySQL integrado en scripts y en UI TPV.
- PDA web y PDA nativa con flujo operativo completo.

## Seguridad actual

- JWT obligatorio en API.
- Matriz de permisos por rol en `auth-service` y `pos-service`.
- Guardia anti caja desde PDA en gateway:
  - `POST /api/v1/pos/cash-sessions/open` -> `403` con `X-Client-App: PDA`
  - `POST /api/v1/pos/cash-sessions/{id}/close` -> `403` con `X-Client-App: PDA`

## Arranque rapido desarrollo (Windows)

1. Levantar servicios backend:

```powershell
cd services/auth-service
.\mvnw.cmd -q -DskipTests spring-boot:run
```

```powershell
cd services/pos-service
.\mvnw.cmd -q -DskipTests spring-boot:run
```

```powershell
cd gateway
.\mvnw.cmd -q -DskipTests spring-boot:run
```

2. Levantar TPV Desktop:

```powershell
cd tpv-desktop
.\mvnw.cmd -q -Dtpv.mode=real -Dtpv.auto.login=false javafx:run
```

3. PDA web:

- Abrir `http://localhost:8080/pda`

4. PDA Android:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-pda-android.ps1 -Install
```

## Instalacion para bar (Windows)

Flujo recomendado:

1. Generar paquete e instalador con scripts de `scripts/`.
2. Instalar en portatil destino con permisos de administrador.
3. Arrancar con scripts runtime:
   - `start-all.cmd`
   - `stop-backend.ps1`
4. Configurar impresoras, negocio, usuarios y catalogo.
5. Configurar Tailscale para acceso PDA por datos moviles.

Detalles en:

- `docs/install-bar-windows.md`
- `docs/pda-tailscale-setup.md`
- `docs/backup-restore.md`

## QA y release

- Smoke automatizado PDA: `scripts/pda-e2e-smoke.ps1`
- Smoke automatizado backup/restore: `scripts/db-backup-restore-smoke.ps1`
- Checklist de salida: `docs/release-checklist.md`
- Ultimo estado QA: `docs/qa-final-report.md`

## Documentacion complementaria

- Arquitectura: `architecture.md`
- Funcionalidades: `features.md`
- API: `api.md`
- Flujo de contribucion: `CONTRIBUTING.md`

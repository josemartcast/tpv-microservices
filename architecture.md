# Arquitectura del sistema TPV

Estado documentado a fecha: 2026-04-13.

## Vista general

El sistema se organiza en cinco piezas principales:

1. `auth-service` (Spring Boot)
2. `pos-service` (Spring Boot)
3. `gateway` (Spring Cloud Gateway)
4. `tpv-desktop` (JavaFX)
5. `pda-android` y `gateway:/pda` (cliente movil nativo y cliente web)

## Flujo de red

- Desktop y PDA consumen siempre el `gateway`.
- `gateway` enruta a `auth-service` y `pos-service`.
- `gateway` sirve la PDA web en `/pda`.
- Base de datos principal: MySQL (desarrollo en local, instalacion bar en servicio nativo `TPVMySQL`).

## Servicios backend

### auth-service

Responsabilidades:

- Login (`/api/v1/auth/login`)
- Perfil de sesion (`/api/v1/auth/me`)
- Admin de usuarios (`/api/v1/auth/admin/users/**`)
- Emision y validacion JWT

Roles soportados:

- `ADMIN`
- `ENCARGADO`
- `CAJERO`
- `CAMARERO`

### pos-service

Responsabilidades:

- Salones, mesas y bloqueo de mesa
- Tickets, lineas, notas, descuentos y movimientos
- Comandas y preview de envio
- Cobros, caja e incidencias
- Historial, facturas y negocio fiscal
- Catalogo (categorias/productos) y destino de impresion
- Perfil del negocio

## Gateway y politicas

`gateway` aplica capa de entrada unica y reglas de seguridad transversales.

Caso importante en produccion:

- Filtro `PdaCashGuardFilter` bloquea apertura/cierre de caja desde PDA (header `X-Client-App: PDA`) devolviendo `403`.

## Clientes

### TPV Desktop (JavaFX)

- Operativa completa de sala/caja/historial/facturacion/admin.
- Integracion de impresoras por destinos (`BAR`, `COCINA`, `POSTRES`, `GENERAL`).
- Cola local de impresion con retry y diagnostico.

### PDA web

- UI en `gateway/src/main/resources/static/pda`.
- Flujo rapido de mesas y comandas desde navegador movil.

### PDA Android nativa

- Kotlin + Jetpack Compose.
- Login real, lock/heartbeat, ticket, notas, comanda, cobro, move-table, combinado de copas.
- Manejo de reconexion y mensajes de error amigables.

## Estado compartido y consistencia

Patrones aplicados:

- Lock de mesa por terminal + heartbeat.
- Endpoints idempotentes para escenarios de red inestable.
- `cancel-empty` para liberar mesa sin basura operativa.
- Control de concurrencia en pago/move-table/send.

## Impresion

Arquitectura de impresion en Desktop:

- `DesktopComandaAutoPrintService` genera payloads por destino.
- `LocalPrintQueueService` enruta a impresoras mapeadas por `PrinterSettingsStore`.
- Fallback de PDF para flujos de comprobante donde aplica.
- Reenviar comanda usa snapshot por destino y respeta impresoras reales configuradas.

## Operacion en bar

- Instalacion empaquetada para Windows con scripts de prerequisitos, arranque y parada.
- Backup/restore desde scripts y desde UI.
- Acceso remoto PDA recomendado por Tailscale (sin abrir puertos de router).

## CI y calidad

Workflows activos:

- `pda-e2e-smoke.yml`
- `db-backup-restore-smoke.yml`

Objetivo: asegurar estabilidad de flujo critico antes de release candidate.

# QA Final Report - TPV/PDA

Fecha: 2026-02-13
Estado general: `GO PRODUCCION CONTROLADA`
Version/tag objetivo: `v1.0.0-rc2`

## Alcance validado
- Flujo TPV/PDA con backend real (`auth-service`, `pos-service`, `gateway`).
- Concurrencia multi-terminal en locks de mesa.
- Concurrencia en cambio de mesa (`move-table`).
- Concurrencia en cobro (evitar doble cargo).
- Replay idempotente en reconexion para `SEND` y `PAYMENT`.

## Evidencia automatizada
- Workflow: `.github/workflows/pda-e2e-smoke.yml`
- Script: `scripts/pda-e2e-smoke.ps1`
- Referencia: `docs/pda-e2e-smoke.md`

Cobertura ejecutada en CI:
- Login y carga PDA.
- Apertura/reuso de ticket.
- Lock race paralelo (`1x success` + `1x denied`).
- Heartbeat y unlock.
- Alta de linea, send preview, send comanda.
- Cobro total y cierre pendiente.
- Replay idempotente (`SEND`/`PAYMENT` con misma `Idempotency-Key`).
- Move-table race paralelo (`1x success` + `1x denied`).
- Payment race paralelo (`1x success` + `1x denied`, sin doble cobro persistido).

## Criterio de salida alcanzado
- No edicion simultanea efectiva de la misma mesa.
- No doble aplicacion en acciones idempotentes de reconexion.
- No doble cobro en carrera concurrente.
- Resultado estable en pipeline (`check verde`).
- `move-table` protegido ante carrera concurrente (backend + test de integracion).

## Riesgo residual (no bloqueante para piloto)
- Impresion fisica en hardware real (colas, cortes, reconexion impresora).
- Prueba de red inestable real en PDA (cortes largos, roaming, latencia extrema).
- Carga sostenida con mas terminales simultaneos (escenario pico de servicio).

## Checklist piloto
- Configurar IDs de terminal unicos por dispositivo.
- Verificar reloj/fecha del equipo y zona horaria.
- Mantener backup de DB y plan de rollback.
- Activar monitorizacion basica de errores de gateway/pos.
- Definir protocolo operativo ante lock conflict (quien libera, cuando, como).

## Recomendacion
- Ejecutar despliegue controlado con observabilidad reforzada en primeras 72h.
- Si no hay incidencias criticas, consolidar el tag `v1.0.0-rc2` como base operativa.
- Usar checklist de salida: `docs/release-checklist.md`.

## Firma QA tecnica
- Decision: `GO PRODUCCION CONTROLADA`
- Fecha: `2026-02-13`
- Base de validacion: CI `PDA E2E Smoke` + tests de `pos-service` en verde.

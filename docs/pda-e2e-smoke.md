# PDA E2E Smoke

## Objetivo
Validar de extremo a extremo el flujo minimo de PDA contra backend real:
- carga del frontend `/pda`
- login
- lock multi-terminal
- conflicto de lock en paralelo (dos PDAs misma mesa)
- apertura/reuso de ticket
- alta de linea
- envio de comanda
- cobro
- liberacion de lock
- replay idempotente de acciones en reconexion (`SEND` y `PAYMENT` con la misma `Idempotency-Key`)
- carrera concurrente de `move-table` (dos tickets compitiendo por misma mesa destino)
- carrera concurrente de cobro (dos pagos simultaneos por el mismo pendiente)

## Script
`scripts/pda-e2e-smoke.ps1`

## Requisitos
1. Servicios levantados:
- `gateway` en `:8080`
- `auth-service` en `:8081`
- `pos-service` en `:8082`

2. Usuario admin disponible:
- `admin / admin123`

## Ejecucion
Desde raiz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pda-e2e-smoke.ps1
```

Opcional con parametros:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pda-e2e-smoke.ps1 `
  -GatewayBaseUrl "http://localhost:8080" `
  -Username "admin" `
  -Password "admin123" `
  -TerminalA "QA-A" `
  -TerminalB "QA-B"
```

## Criterio de exito
El script termina con:

`PDA E2E smoke PASSED`

Si falla, corta en el primer assert con detalle de endpoint, status esperado y cuerpo de respuesta.

Nota lock race:
- En la prueba paralela de lock, el perdedor puede devolver `409` o `403` segun el timing interno del backend; ambos se consideran conflicto valido.

## CI
Workflow incluido:

`.github/workflows/pda-e2e-smoke.yml`

Este pipeline levanta `auth-service`, `pos-service` y `gateway` contra MySQL efimero y ejecuta el smoke automaticamente en `push` y `pull_request`.

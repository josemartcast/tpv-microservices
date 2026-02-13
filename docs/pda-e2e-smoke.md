# PDA E2E Smoke

## Objetivo
Validar de extremo a extremo el flujo minimo de PDA contra backend real:
- carga del frontend `/pda`
- login
- lock multi-terminal
- apertura/reuso de ticket
- alta de linea
- envio de comanda
- cobro
- liberacion de lock

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

## CI
Workflow incluido:

`.github/workflows/pda-e2e-smoke.yml`

Este pipeline levanta `auth-service`, `pos-service` y `gateway` contra MySQL efimero y ejecuta el smoke automaticamente en `push` y `pull_request`.

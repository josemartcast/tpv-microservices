# PDA E2E Smoke

Estado documentado a fecha: 2026-04-13.

Script principal:

- `scripts/pda-e2e-smoke.ps1`

Workflow CI:

- `.github/workflows/pda-e2e-smoke.yml`

## Objetivo

Validar E2E de PDA contra backend real en los flujos criticos:

- login
- carga de `/pda`
- lock race
- heartbeat
- alta de linea
- envio de comanda
- cobro
- unlock
- replay idempotente (`SEND` y `PAYMENT`)
- move-table race
- payment race

## Requisitos

- Gateway en `8080`
- Auth en `8081`
- POS en `8082`
- Usuario admin valido (`admin/admin123` por defecto de test)

## Ejecucion

Desde raiz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pda-e2e-smoke.ps1
```

Con parametros:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pda-e2e-smoke.ps1 `
  -GatewayBaseUrl "http://127.0.0.1:8080" `
  -Username "admin" `
  -Password "admin123" `
  -TerminalA "CI-A" `
  -TerminalB "CI-B"
```

## Criterio de exito

Salida final esperada:

- `PDA E2E smoke PASSED`

## Notas operativas

- En lock race, segun timing interno, el perdedor puede devolver `403` o `409`; ambos son conflicto valido.
- El script falla en el primer assert para facilitar diagnostico rapido.
- Si CI falla por puertos no abiertos, revisar logs de arranque de `pos-service`/`gateway`.

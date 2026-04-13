# QA multi terminal (Desktop + PDA)

Estado documentado a fecha: 2026-04-13.

Objetivo: validar consistencia de mesas/tickets cuando varios terminales actuan en paralelo.

## Precondiciones

- `auth-service` en `8081`
- `pos-service` en `8082`
- `gateway` en `8080`
- Dos clientes conectados contra backend real:
  - TPV Desktop A (`T-A`)
  - TPV Desktop B o PDA (`T-B`)

## Smoke inicial

1. Ambos terminales en `MODE REAL`.
2. IDs de terminal diferentes.
3. Mapa de mesas sincroniza sin error en ambos.

## Casos obligatorios

## 1) Lock collision

- A abre mesa N.
- B intenta abrir la misma mesa N.
- Esperado: B bloqueado por conflicto de lock.

## 2) Heartbeat estable

- A permanece en mesa N > 2 min.
- Esperado: lock vivo, sin expulsiones inesperadas.

## 3) Unlock por salida

- A sale de mesa N sin ticket en uso.
- Esperado: mesa queda libre.

## 4) cancel-empty

- A abre mesa, no anade lineas, vuelve atras.
- Esperado: se cancela ticket vacio y mesa libre.

## 5) Cobro y liberacion

- A cobra ticket completo.
- Esperado: mesa libre en ambos mapas.

## 6) Move-table race

- Dos tickets compiten por la misma mesa destino.
- Esperado: un solo ganador, sin inconsistencias.

## 7) Payment race

- Dos cobros simultaneos sobre mismo pendiente.
- Esperado: sin doble cobro persistido.

## 8) Reapertura ticket pagado

- Reabrir ticket pagado con rol permitido.
- Volver a cobrar.
- Esperado: no se duplica el total de cierre de caja por pagos anteriores.

## 9) Estado precuenta

- Solicitar/Imprimir precuenta.
- Esperado: estado `PRECUENTA_PEDIDA` visible en mapa Desktop y PDA.

## 10) Comanda PDA

- PDA envia varias lineas (incluyendo similares, ejemplo COPA y COPA SIN).
- Esperado: todas las lineas aparecen en ticket y en impresion de comanda.

## Criterio de pase

- No hay doble edicion efectiva sobre misma mesa.
- No hay locks zombies tras pago/salida/move.
- No hay doble cobro en carreras.
- Mesmo estado de mesa en todos los terminales tras refresco.

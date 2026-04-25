# Onboarding junior (primeros dias)

Esta guia te ayuda a entrar al proyecto sin saturarte.

## 1) Objetivo de la primera semana

- Entender el flujo completo de negocio.
- Levantar el sistema en local.
- Hacer al menos un cambio pequeno con PR.

## 2) Orden recomendado de lectura

1. `README.md`
2. `architecture.md`
3. `api.md`
4. `runbook`
5. modulo que vayas a tocar

## 3) Mapa mental del codigo

- `auth-service`: quien eres y que puedes hacer.
- `pos-service`: que pasa en la operacion del bar.
- `gateway`: puerta de entrada y reglas cross-cutting.
- `tpv-desktop`: UI principal del negocio.
- `pda-android`: UI movil de camareros.

Si no sabes donde tocar:

- regla de negocio -> `pos-service/service`
- endpoint -> `controller + dto + service`
- bug de pantalla desktop -> `tpv-desktop/ui`
- bug de pantalla PDA -> `pda-android/ui`

## 4) Entorno local minimo

1. levantar MySQL en Docker.
2. arrancar auth, pos y gateway.
3. abrir `/pda`.
4. arrancar Desktop.
5. login con `admin/admin123`.

Cuando puedas hacer eso sin ayuda, ya tienes base para desarrollar.

## 5) Flujo funcional que debes dominar

1. abrir caja
2. abrir mesa
3. anadir linea
4. enviar comanda
5. cobrar
6. cerrar caja

Este flujo toca casi todas las piezas criticas del sistema.

## 6) Como depurar sin perder tiempo

1. reproduce el problema con pasos exactos.
2. mira status code (`401`, `403`, `409`, `500`).
3. ubica endpoint en controller.
4. sigue al service que implementa la regla.
5. valida en DB solo si hace falta.

Tip: un `409` suele ser conflicto real de concurrencia, no siempre un bug.

## 7) Primera contribucion recomendada

Escoge una tarea pequena de una de estas:

- mejora de mensaje de error
- ajuste de validacion simple
- test faltante en service
- mejora de documentacion operativa

## 8) Checklist antes de pedir review

- [ ] cambio pequeno y entendible
- [ ] compila
- [ ] tests del modulo en verde
- [ ] no sube builds/logs/backups
- [ ] docs actualizadas si cambias comportamiento

## 9) Buenas practicas de equipo

- pregunta pronto si estas bloqueado mas de 30-45 min.
- comparte pasos para reproducir, no solo "no funciona".
- evita refactors grandes en tu primer PR.
- prioriza cambios que reduzcan riesgo operativo (caja, cobro, locks).


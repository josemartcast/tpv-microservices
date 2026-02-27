# Release Checklist - Produccion TPV/PDA

Fecha base: 2026-02-13
Objetivo: pasar de piloto controlado a operacion estable con riesgo acotado.

## 1) Pre-release tecnico
Estado: COMPLETADO (2026-02-27)
- Confirmar `PDA E2E Smoke` en verde en el ultimo commit de `main`.
- Confirmar version/tag de release definida (ejemplo: `v1.0.0`).
- Congelar cambios no criticos durante ventana de despliegue.
- Verificar migraciones DB en entorno de staging.
- Verificar backups recientes y restauracion de prueba.

## 2) Seguridad y acceso
- JWT secret no default en produccion.
- Credenciales fuera de repo (variables seguras o vault).
- Terminal IDs unicos por dispositivo.
- Revisar roles y permisos (ADMIN/CAJERO/CAMARERO).
- Revisar expiracion y renovacion de token.

## 3) Operativa de servicios
- Healthcheck de `auth-service`, `pos-service`, `gateway`.
- Logs activos y rotacion configurada.
- NTP/hora correcta en portatil TPV y dispositivos PDA.
- Prueba de arranque en frio (reinicio completo y recuperacion).

## 4) Impresion y perifericos
- Prueba de comanda BAR/COCINA en impresora termica real.
- Prueba de pre-cuenta PDF (apertura y legibilidad).
- Verificar reconexion de impresora tras desconexion fisica.

## 5) Flujo funcional critico
- Apertura mesa, add lines, send comanda, cobro parcial y total.
- Cierre de ticket libera mesa correctamente.
- Move-table mantiene consistencia (origen/destino).
- Conflicto multi-terminal bloquea segunda edicion.
- Reconexion PDA mantiene idempotencia (sin duplicados).

## 6) Observabilidad y soporte
- Definir tablero minimo: errores 4xx/5xx, latencia, conflictos lock.
- Definir alertas: caida de gateway, picos de 5xx, fallos de impresion.
- Definir runbook de incidencias (quien actua, SLA, escalado).
- Guardar artefactos CI y logs de release.

## 7) Rollback
- Criterio de rollback claro:
- error critico de cobro
- inconsistencia de mesas
- caida sostenida > X min
- Procedimiento:
- revert a tag estable
- restaurar DB si aplica
- validacion smoke post-rollback

## 8) Go/No-Go final
- `GO` si todos los puntos 1-7 estan validados.
- `NO-GO` si hay un riesgo critico abierto en cobro, bloqueo de mesas o impresion.

## 9) Post-release (24-72h)
- Monitorizacion reforzada.
- Recoleccion de incidencias reales.
- Mini retro tecnica y ajuste de backlog.

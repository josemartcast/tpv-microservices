# Release checklist (pre RC)

Estado documentado a fecha: 2026-04-13.

Objetivo: validar salida a release candidate con riesgo operativo controlado.

## 1. Build y CI

- [ ] `pda-e2e-smoke.yml` en verde en `main`.
- [ ] `db-backup-restore-smoke.yml` en verde en `main`.
- [ ] Sin errores de compilacion local en:
  - `auth-service`
  - `pos-service`
  - `gateway`
  - `tpv-desktop`
  - `pda-android`

## 2. Seguridad y permisos

- [ ] Login JWT funcionando en Desktop/PDA.
- [ ] Matriz de roles validada (ADMIN, ENCARGADO, CAJERO, CAMARERO).
- [ ] Bloqueo de caja desde PDA validado (`403` con `X-Client-App: PDA`).
- [ ] Terminal IDs unicos por dispositivo.

## 3. Operativa core TPV

- [ ] Apertura de caja.
- [ ] Apertura de mesa y lock multi terminal.
- [ ] Alta/edicion/borrado de lineas.
- [ ] Envio de comandas por destino.
- [ ] Cobro total y parcial.
- [ ] Cobro parcial por lineas.
- [ ] Cierre de caja con resumen impreso.

## 4. Facturacion e historial

- [ ] Generar factura desde ticket.
- [ ] Reimprimir factura desde historial.
- [ ] Reabrir ticket pagado (rol permitido) sin duplicar cobros en cierre.

## 5. Impresion

- [ ] Comandas BAR/COCINA/POSTRES en impresora real.
- [ ] Precuenta en impresora general.
- [ ] Ticket cliente al cobrar.
- [ ] Reenviar comanda usa impresora real (no Print to PDF forzado).
- [ ] Ticket largo (mas de 22 lineas) imprime completo en continuo y corta al final.

## 6. PDA (web + nativa)

- [ ] Abrir mesa, anadir linea, editar, nota, enviar, cobrar, mover mesa.
- [ ] Modal al salir de mesa con pendientes de comanda.
- [ ] Liberacion de mesa vacia (`cancel-empty`).
- [ ] Estado `PRECUENTA_PEDIDA` visible en mapa.
- [ ] Flujo combinado COPAS + REFRESCOS correcto.

## 7. Datos y backup

- [ ] Backup previo a despliegue realizado.
- [ ] Restore de prueba validado.
- [ ] Carpeta de backup fuera de Git.

## 8. Instalacion portatil bar

- [ ] Instalador EXE ejecuta como administrador.
- [ ] MySQL nativo `TPVMySQL` levantado.
- [ ] `start-all.cmd` levanta backend + UI.
- [ ] Tailscale operativo en portatil y PDA.

## 9. Observabilidad minima

- [ ] Sin crecimiento anomalo de RAM tras sesion larga de prueba.
- [ ] Logs de errores revisados (gateway/pos/desktop).
- [ ] Incidencias raras de comanda monitorizadas con trazas activas.

## 10. Go / No-Go

- `GO` si no hay bloqueantes en cobro, locks, impresion o consistencia de tickets.
- `NO-GO` si persiste cualquier incidencia critica de comanda/cobro.

## 11. Tag release candidate

Solo cuando puntos 1-10 esten cerrados:

```powershell
git tag v1.0.0-rcX
git push origin v1.0.0-rcX
```

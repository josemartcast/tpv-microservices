# Funcionalidades implementadas

Documento de alcance funcional actual, con foco en lo que un junior necesita para orientarse.

## 1) Autenticacion y roles

- Login JWT para Desktop y PDA.
- Gestion admin de usuarios (alta, cambio de rol, reset password, activacion/desactivacion, borrado).
- Roles operativos: `ADMIN`, `ENCARGADO`, `CAJERO`, `CAMARERO`.
- Rol `USER` se considera legado y se migra a `CAMARERO` al arrancar auth-service.

## 2) Operativa de sala

- Vista de mesas por salon.
- Apertura de ticket por mesa.
- Lock por terminal + heartbeat.
- Estados de mesa (libre/ocupada/bloqueada/pendiente de envio/precuenta pedida).
- Alias de mesa por salon.

## 3) Gestion de tickets

- Alta de lineas simples y combo.
- Edicion de cantidad/precio/nota.
- Eliminacion de lineas.
- Descuento.
- Cancelacion de ticket (`cancel` y `cancel-empty`).
- Move-table.
- Reapertura de ticket pagado (segun rol).

## 4) Comandas e impresion

- Preview de pendientes.
- Envio por destino (`BAR`, `COCINA`, `POSTRES`) o global.
- Reenvio de comanda.
- Integracion de cola de impresion en Desktop con configuracion por destino.

## 5) Cobro y caja

- Cobro total y parcial.
- Cobro por lineas (consume-payment).
- Metodos principales: efectivo, tarjeta, bizum.
- Apertura/cierre de caja.
- Incidencias de caja (IN/OUT).
- Resumen de cierre y resumen fiscal.

## 6) Fiscal, historial y factura

- Historial de tickets.
- Generacion y consulta de factura por ticket.
- Listado de facturas.
- Apertura/cierre de ejercicio fiscal.

## 7) Catalogo y negocio

- CRUD de categorias y productos.
- Activar/desactivar categorias y productos.
- Seed de catalogo admin.
- Perfil de negocio editable.

## 8) Clientes soportados

### TPV Desktop

- Flujo completo (caja, sala, historial, administracion, ajustes).

### PDA web

- Disponible desde `gateway` en `/pda`.
- Flujo de camarero optimizado para navegador movil.

### PDA Android

- Kotlin/Compose.
- Flujo operativo de sala conectado al backend real.

## 9) Seguridad operativa

- JWT obligatorio en API.
- Autorizacion por rol.
- Guardia anti-caja desde PDA en gateway (`403`).
- Control de concurrencia en locks, move-table y pagos.

## 10) QA automatizado disponible

- Smoke E2E PDA: `scripts/pda-e2e-smoke.ps1`.
- Smoke backup/restore: `scripts/db-backup-restore-smoke.ps1`.

## 11) Limites conocidos (importante para soporte)

- Si hay procesos concurrentes sobre la misma mesa/ticket, pueden aparecer `409` esperados.
- En red inestable conviene usar operaciones idempotentes para reintentos seguros.
- Caja desde PDA esta bloqueada por diseno.


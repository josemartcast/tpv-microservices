# Funcionalidades implementadas

Estado documentado a fecha: 2026-04-13.

## 1. Usuarios, sesion y roles

- Login JWT en Desktop y PDA.
- Admin de usuarios desde TPV:
  - crear
  - cambiar rol
  - reset password
  - activar/desactivar
  - eliminar
- Roles operativos en backend:
  - `ADMIN`
  - `ENCARGADO`
  - `CAJERO`
  - `CAMARERO`

## 2. Mapa de mesas y salones

- Salones configurables (crear/renombrar/eliminar con validaciones).
- Filtro por salon en TPV y PDA.
- Alias de mesa por salon (visible en operativa, no en ticket cliente/factura).
- Estado de mesa visible en mapa:
  - libre
  - ocupada
  - bloqueada
  - pendiente de envio
  - precuenta pedida

## 3. Bloqueo multi terminal

- Lock por mesa y terminal.
- Heartbeat periodico para mantener lock.
- Recuperacion de lock en cortes breves.
- Unlock en eventos de salida/cierre.
- Flujo `cancel-empty` para liberar mesa vacia sin dejar ticket abierto.

## 4. Tickets y lineas

- Apertura/reuso de ticket por mesa.
- Alta de productos y combos (copa + refresco).
- Edicion de linea:
  - cantidad
  - precio
  - nota
  - eliminacion
- Eliminacion de ticket vacio.
- Descuentos.
- Move-table.

## 5. Comandas

- Preview de lineas pendientes.
- Envio por destino (`BAR`, `COCINA`, `POSTRES`) o unificado.
- Formato de comanda en negrita/columnado y con notas.
- Reimpresion de ultima comanda desde TPV.
- Reenviar comanda por impresoras reales configuradas (no forzado a PDF).

## 6. Cobro y caja

- Cobro total y parcial.
- Cobro parcial por lineas (consume lineas pagadas y deja pendiente lo no pagado).
- Soporte de metodos:
  - EFECTIVO
  - TARJETA
  - BIZUM
- Reapertura de ticket pagado con protecciones para no duplicar totales en cierre.
- Caja:
  - apertura
  - incidencias IN/OUT
  - cierre con confirmacion y resumen impreso

## 7. Historial y facturas

- Historial de tickets de caja actual.
- Modificacion de ticket pagado para roles autorizados.
- Generacion de factura desde ticket.
- Reimpresion de factura desde historial.
- Formato fiscal con base imponible, desglose IVA y total.

## 8. Catalogo e impresoras

- Categorias y productos administrables desde TPV.
- IVA por producto (4/10/21).
- Destino de impresion por categoria.
- Gestion de impresoras del negocio:
  - crear perfiles
  - mapear a impresora del sistema
  - habilitar/deshabilitar

## 9. PDA web y PDA nativa

- Login contra backend real.
- Mapa de mesas y filtros por salon.
- Flujo completo de mesa:
  - abrir mesa
  - anadir/editar/borrar lineas
  - nota por linea
  - enviar comanda
  - pedir precuenta
  - cobrar
  - mover mesa
- Dialogo de envio de comanda al salir de mesa si hay pendientes.
- UI responsive para portrait y landscape.

## 10. Seguridad operativa

- PDA no puede abrir ni cerrar caja (403 en gateway por `X-Client-App: PDA`).
- Controles de permisos por endpoint en `pos-service`.
- Mensajes de error operativos en Desktop y PDA.

## 11. Backups y despliegue

- Scripts de backup y restore MySQL.
- Smoke de backup/restore automatizado.
- Instalador Windows para portatil de bar con prerequisitos.

## 12. Estado general

El sistema esta operativo en entorno real y en fase de endurecimiento pre-release.

Pendientes tipicos de esta fase:

- ajustes UX finos
- seguimiento de incidencias raras en impresiones/comandas
- empaquetado de version estable para despliegue final

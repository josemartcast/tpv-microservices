# API principal (resumen operativo)

Estado documentado a fecha: 2026-04-13.

Base URL via gateway:

- `http://localhost:8080`

Version API:

- `/api/v1`

Autenticacion:

- `Authorization: Bearer <jwt>`

Header de terminal (clientes TPV/PDA):

- `X-Terminal-Id: <terminal-id>`

Header de app (usado para guardas PDA):

- `X-Client-App: PDA`

## Auth service

### Login

- `POST /api/v1/auth/login`

### Sesion actual

- `GET /api/v1/auth/me`

### Admin usuarios

- `GET /api/v1/auth/admin/users`
- `POST /api/v1/auth/admin/users`
- `PATCH /api/v1/auth/admin/users/{id}/role`
- `PATCH /api/v1/auth/admin/users/{id}/password`
- `PATCH /api/v1/auth/admin/users/{id}/active`
- `PATCH /api/v1/auth/admin/users/{id}/deactivate`
- `DELETE /api/v1/auth/admin/users/{id}`

## POS service - salones/mesas

- `GET /api/v1/pos/salon/tables`
- `POST /api/v1/pos/salon/tables/{tableNumber}/open-ticket`
- `POST /api/v1/pos/salon/tables/{tableNumber}/lock`
- `POST /api/v1/pos/salon/tables/{tableNumber}/heartbeat`
- `POST /api/v1/pos/salon/tables/{tableNumber}/unlock`
- `PUT /api/v1/pos/salon/tables/{tableNumber}/alias`

Admin salones:

- `GET /api/v1/pos/admin/salons`
- `POST /api/v1/pos/admin/salons`
- `PUT /api/v1/pos/admin/salons/{id}`
- `DELETE /api/v1/pos/admin/salons/{id}`
- `GET /api/v1/pos/admin/salons/{id}/table-aliases`
- `PUT /api/v1/pos/admin/salons/{id}/tables/{tableNumber}/alias`

## POS service - tickets y lineas

- `POST /api/v1/pos/tickets`
- `GET /api/v1/pos/tickets/open`
- `GET /api/v1/pos/tickets/history/current-cash`
- `GET /api/v1/pos/tickets/{id}`
- `GET /api/v1/pos/tickets/{id}/summary`
- `GET /api/v1/pos/tickets/{id}/payment-summary`

Lineas:

- `POST /api/v1/pos/tickets/{id}/lines`
- `POST /api/v1/pos/tickets/{id}/lines/combo`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}/price`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}/note`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}/consume-payment`
- `DELETE /api/v1/pos/tickets/{id}/lines/{lineId}`

Operaciones de ticket:

- `POST /api/v1/pos/tickets/{id}/cancel`
- `POST /api/v1/pos/tickets/{id}/cancel-empty`
- `POST /api/v1/pos/tickets/{id}/bill-requested`
- `POST /api/v1/pos/tickets/{id}/move-table`
- `POST /api/v1/pos/tickets/{id}/discount`
- `POST /api/v1/pos/tickets/{id}/reopen-paid`

## POS service - comandas

- `GET /api/v1/pos/tickets/{id}/send-preview`
- `POST /api/v1/pos/tickets/{id}/send`

## POS service - pagos

- `POST /api/v1/pos/tickets/{ticketId}/payments`
- `POST /api/v1/pos/tickets/{ticketId}/refunds`

## POS service - caja/fiscal

- `GET /api/v1/pos/cash-sessions/current`
- `POST /api/v1/pos/cash-sessions/open`
- `POST /api/v1/pos/cash-sessions/{id}/close`
- `GET /api/v1/pos/cash-sessions/{id}/close-summary`
- `GET /api/v1/pos/cash-sessions/{id}/incidents`
- `POST /api/v1/pos/cash-sessions/{id}/incidents`
- `GET /api/v1/pos/cash-sessions/{id}/open-tickets`
- `POST /api/v1/pos/cash-sessions/{id}/resolve-open-tickets`
- `GET /api/v1/pos/cash-sessions/{id}/fiscal-summary`
- `GET /api/v1/pos/cash-sessions/{id}/fiscal-closure`

Ejercicios fiscales:

- `GET /api/v1/pos/fiscal-exercises`
- `GET /api/v1/pos/fiscal-exercises/current`
- `POST /api/v1/pos/fiscal-exercises/open`
- `POST /api/v1/pos/fiscal-exercises/{id}/close`

## POS service - catalogo

Categorias:

- `GET /api/v1/pos/categories`
- `GET /api/v1/pos/categories/{id}`
- `POST /api/v1/pos/categories`
- `PUT /api/v1/pos/categories/{id}`
- `DELETE /api/v1/pos/categories/{id}`
- `PATCH /api/v1/pos/categories/{id}/activate`
- `PATCH /api/v1/pos/categories/{id}/deactivate`

Productos:

- `GET /api/v1/pos/products`
- `GET /api/v1/pos/products/{id}`
- `POST /api/v1/pos/products`
- `PUT /api/v1/pos/products/{id}`
- `DELETE /api/v1/pos/products/{id}`
- `PATCH /api/v1/pos/products/{id}/activate`
- `PATCH /api/v1/pos/products/{id}/deactivate`

Semilla catalogo admin:

- `POST /api/v1/pos/admin/seed-catalog`

## POS service - clientes/facturas/negocio

- `GET /api/v1/pos/customers`
- `GET /api/v1/pos/customers/{id}`
- `POST /api/v1/pos/customers`
- `PUT /api/v1/pos/customers/{id}`
- `DELETE /api/v1/pos/customers/{id}`

- `GET /api/v1/pos/tickets/{id}/invoice`
- `POST /api/v1/pos/tickets/{id}/invoice`
- `GET /api/v1/pos/invoices`

- `GET /api/v1/pos/business-profile`
- `PUT /api/v1/pos/business-profile`

## Health y auditoria

- `GET /api/v1/pos/health`
- `GET /api/v1/pos/audit/events`

## Guardias de seguridad especiales

Con `X-Client-App: PDA`:

- `POST /api/v1/pos/cash-sessions/open` -> `403`
- `POST /api/v1/pos/cash-sessions/{id}/close` -> `403`

Motivo: caja solo se opera desde TPV Desktop.

## Errores frecuentes

- `401`: token invalido/caducado
- `403`: sin permisos o bloqueado por politica
- `404`: recurso no encontrado
- `409`: conflicto de estado (lock, ticket, caja, etc.)
- `5xx`: error servidor

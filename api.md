# API principal (guia operativa)

Esta guia resume la API para desarrollo diario.
No pretende sustituir el codigo; para comportamiento exacto manda siempre el controller/service real.

## 1) Base y convenciones

- Base URL (via gateway): `http://localhost:8080`
- Version: `/api/v1`
- Auth: `Authorization: Bearer <jwt>`
- Contexto terminal: `X-Terminal-Id: <terminal-id>`
- Contexto PDA: `X-Client-App: PDA`

## 2) Flujo minimo de autenticacion

1. `POST /api/v1/auth/login`
2. guardar `accessToken`
3. usar token en llamadas siguientes
4. validar sesion con `GET /api/v1/auth/me`

## 3) Endpoints mas usados por dominio

### Auth y usuarios

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/auth/admin/users`
- `POST /api/v1/auth/admin/users`
- `PATCH /api/v1/auth/admin/users/{id}/role`
- `PATCH /api/v1/auth/admin/users/{id}/password`
- `PATCH /api/v1/auth/admin/users/{id}/active`
- `PATCH /api/v1/auth/admin/users/{id}/deactivate`
- `DELETE /api/v1/auth/admin/users/{id}`

### Salon y mesas

- `GET /api/v1/pos/salon/tables`
- `POST /api/v1/pos/salon/tables/{tableNumber}/open-ticket`
- `POST /api/v1/pos/salon/tables/{tableNumber}/lock`
- `POST /api/v1/pos/salon/tables/{tableNumber}/heartbeat`
- `POST /api/v1/pos/salon/tables/{tableNumber}/unlock`
- `PUT /api/v1/pos/salon/tables/{tableNumber}/alias`

### Tickets, lineas y comandas

- `POST /api/v1/pos/tickets`
- `GET /api/v1/pos/tickets/open`
- `GET /api/v1/pos/tickets/{id}`
- `GET /api/v1/pos/tickets/{id}/summary`
- `GET /api/v1/pos/tickets/{id}/payment-summary`
- `POST /api/v1/pos/tickets/{id}/lines`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}/price`
- `PATCH /api/v1/pos/tickets/{id}/lines/{lineId}/note`
- `DELETE /api/v1/pos/tickets/{id}/lines/{lineId}`
- `GET /api/v1/pos/tickets/{id}/send-preview`
- `POST /api/v1/pos/tickets/{id}/send`

### Cobros, caja y fiscal

- `POST /api/v1/pos/tickets/{ticketId}/payments`
- `POST /api/v1/pos/tickets/{ticketId}/refunds`
- `GET /api/v1/pos/cash-sessions/current`
- `POST /api/v1/pos/cash-sessions/open`
- `POST /api/v1/pos/cash-sessions/{id}/close`
- `GET /api/v1/pos/cash-sessions/{id}/close-summary`
- `GET /api/v1/pos/cash-sessions/{id}/fiscal-summary`
- `GET /api/v1/pos/cash-sessions/{id}/fiscal-closure`

### Catalogo y negocio

- `GET /api/v1/pos/categories`
- `POST /api/v1/pos/categories`
- `GET /api/v1/pos/products`
- `POST /api/v1/pos/products`
- `POST /api/v1/pos/admin/seed-catalog`
- `GET /api/v1/pos/business-profile`
- `PUT /api/v1/pos/business-profile`

### Facturacion, auditoria y salud

- `POST /api/v1/pos/tickets/{id}/invoice`
- `GET /api/v1/pos/tickets/{id}/invoice`
- `GET /api/v1/pos/invoices`
- `GET /api/v1/pos/audit/events`
- `GET /api/v1/pos/health`

## 4) Restriccion especial de seguridad (PDA)

Si mandas `X-Client-App: PDA`:

- `POST /api/v1/pos/cash-sessions/open` -> `403`
- `POST /api/v1/pos/cash-sessions/{id}/close` -> `403`

Motivo: apertura/cierre de caja solo desde Desktop.

## 5) Errores frecuentes

- `400`: payload invalido.
- `401`: token ausente/invalido/caducado.
- `403`: rol sin permisos o politica bloqueante.
- `404`: recurso no encontrado.
- `409`: conflicto de estado/concurrencia.
- `5xx`: error interno.

## 6) Recomendacion para trabajar rapido

Cuando toques una funcionalidad:

1. localiza el endpoint en controller.
2. revisa validaciones y reglas en service.
3. revisa tests del modulo.

`rg "@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@PatchMapping|@DeleteMapping" services/*/src/main/java`


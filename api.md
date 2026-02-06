# API – TPV Desktop

Este documento describe los **endpoints principales** de la API consumida por el cliente **TPV Desktop**.

La API sigue un estilo **REST**, utiliza **JSON** como formato de intercambio y está protegida mediante **JWT** con control de acceso por roles.

---

## Información general

- Protocolo: HTTP / HTTPS
- Formato: JSON
- Autenticación: JWT
- Versión API: `/api/v1`

La URL base del backend es configurable desde el cliente TPV (pantalla **Settings**).

Ejemplo:
http://localhost:8080/api/v1


---

## Autenticación

### Login
POST /api/v1/auth/login


Autentica al usuario y devuelve un token JWT.

**Request**
json
{
  "username": "admin",
  "password": "password"
}
Response

{
  "accessToken": "jwt-token",
  "expiresInSeconds": 3600,
  "roles": ["ADMIN"]
}
El token debe enviarse en cada petición posterior:

Authorization: Bearer <token>
Cash Sessions (Caja)
Obtener caja actual (OPEN)
GET /api/v1/pos/cash-sessions/current
Devuelve la caja actualmente abierta.

Uso

Comprobar estado de caja

Precondición para ventas y operaciones fiscales

Abrir caja
POST /api/v1/pos/cash-sessions/open
Roles

ADMIN

Request

{
  "openingCashCents": 5000,
  "note": "Cambio inicial"
}
Cerrar caja
POST /api/v1/pos/cash-sessions/{id}/close
Roles

ADMIN

Request

{
  "closingCashCents": 4820,
  "note": "Cierre turno mañana"
}
Registra el cierre de la caja, el efectivo contado y la diferencia para auditoría.

Fiscal Summary
GET /api/v1/pos/cash-sessions/{id}/fiscal-summary
Devuelve un resumen fiscal informativo de la caja.

Incluye

Tickets pagados

Tickets cancelados

Ventas brutas, netas e IVA

Pagos agrupados por método

Fiscal Closure (preview)
GET /api/v1/pos/cash-sessions/{id}/fiscal-closure
Devuelve los datos necesarios para el cierre de caja antes de confirmarlo.

Incluye

Efectivo inicial

Ventas en efectivo

Efectivo esperado

Tickets
Crear ticket
POST /api/v1/pos/tickets
Crea un nuevo ticket asociado a la caja abierta.

Obtener ticket por ID
GET /api/v1/pos/tickets/{id}
Devuelve el ticket con sus líneas actuales.

Listar tickets abiertos
GET /api/v1/pos/tickets/open
Devuelve todos los tickets en estado OPEN.

Añadir línea al ticket
POST /api/v1/pos/tickets/{id}/lines
Request

{
  "productId": 3,
  "qty": 2
}
Modificar cantidad de una línea
PATCH /api/v1/pos/tickets/{id}/lines/{lineId}
Request

{
  "qty": 3
}
Eliminar línea del ticket
DELETE /api/v1/pos/tickets/{id}/lines/{lineId}
Cobrar ticket
POST /api/v1/pos/tickets/{id}/pay
Registra el pago del ticket y lo marca como PAID.

Cancelar ticket
POST /api/v1/pos/tickets/{id}/cancel
Roles

ADMIN

Cancela un ticket no cobrado.

Resumen completo de ticket
GET /api/v1/pos/tickets/{id}/summary
Devuelve:

Líneas del ticket

Pagos registrados

Totales

Cantidad pendiente

Resumen de pagos
GET /api/v1/pos/tickets/{id}/payment-summary
Devuelve:

Total del ticket

Total pagado

Pendiente

Productos y categorías
Categorías
GET    /api/v1/pos/categories
POST   /api/v1/pos/categories
PUT    /api/v1/pos/categories/{id}
DELETE /api/v1/pos/categories/{id}
Productos
GET    /api/v1/pos/products
POST   /api/v1/pos/products
PUT    /api/v1/pos/products/{id}
DELETE /api/v1/pos/products/{id}
Seguridad y roles
Roles disponibles
ADMIN

USER

Restricciones
Apertura y cierre de caja: ADMIN

Cierre fiscal: ADMIN

Cancelación de tickets: ADMIN

Ventas y consultas: USER / ADMIN

Errores comunes
401 Unauthorized
Token inválido o expirado

403 Forbidden
Rol insuficiente para la acción solicitada

404 Not Found
Recurso inexistente

409 Conflict
Caja cerrada

Ticket no editable

Estado inválido

Notas técnicas
Todos los importes se manejan en céntimos (int) para evitar errores de precisión.

La API está diseñada para ser reutilizada por otros clientes (web, móvil).

El cliente TPV nunca accede directamente a la base de datos.

# Arquitectura – TPV Desktop

Este proyecto está compuesto por un **backend de microservicios (Spring Boot)** y un **cliente de escritorio (JavaFX)** que consume la API REST mediante JWT.

---

## Visión general

- **Cliente (tpv-desktop)**: JavaFX (FXML + Controllers)
- **Backend (microservicios)**: Spring Boot
- **Seguridad**: JWT + roles (ADMIN / USER)
- **Comunicación**: HTTP REST (JSON)
- **Persistencia**: Base de datos relacional (según entorno)

---

## Componentes

### Cliente (JavaFX)
Responsabilidades:
- UI (pantallas + navegación)
- Gestión de estado de sesión (token JWT)
- Consumo de API (ApiClient + DTOs)
- Validaciones básicas y UX (mensajes, bloqueos)

Estructura típica:
- `com.tpv.desktop.ui.*` → pantallas (sales, cash, history, fiscal, settings)
- `com.tpv.desktop.api.*` → ApiClient, DTOs y APIs específicas (CashApi, FiscalApi…)
- `com.tpv.desktop.core.*` → navegación (Nav), estado (AppState), settings, utilidades

---

### Backend (Microservicios)
Responsabilidades:
- Autenticación y autorización
- Reglas de negocio (tickets, pagos, caja)
- Auditoría (expected cash, closing cash, diferencias)
- Exposición de endpoints REST

Servicios principales (nombres orientativos):
- **gateway**: entrada única, routing y filtros
- **auth-service**: login, emisión de JWT
- **pos-service**: caja, tickets, pagos, fiscal

---

## Flujo de autenticación (JWT)

1. Usuario hace login en el cliente.
2. `auth-service` devuelve:
   - `accessToken`
   - roles
3. El cliente guarda el token en `AuthStore`.
4. Cada petición REST añade:
   - `Authorization: Bearer <token>`
5. El backend valida el JWT y aplica roles.

---

## Flujos de negocio principales

### Apertura de caja → ventas → cierre

1. **Abrir caja**
   - Se crea una Cash Session (OPEN)
2. **Ventas**
   - Se crea ticket
   - Se añaden líneas
   - Se registran pagos
   - Ticket pasa a PAID
3. **Fiscal Summary**
   - Consulta de resumen del turno (informativo)
4. **Fiscal Closure**
   - Preview de esperado
   - Introducción de efectivo contado
   - Cierre de caja (CLOSED)

---

## Endpoints clave (via gateway)

Cash session:
- `GET  /api/v1/pos/cash-sessions/current`
- `POST /api/v1/pos/cash-sessions/open`
- `POST /api/v1/pos/cash-sessions/{id}/close`
- `GET  /api/v1/pos/cash-sessions/{id}/fiscal-summary`
- `GET  /api/v1/pos/cash-sessions/{id}/fiscal-closure`

Tickets:
- `POST /api/v1/pos/tickets`
- `GET  /api/v1/pos/tickets/open`
- `GET  /api/v1/pos/tickets/{id}`
- `GET  /api/v1/pos/tickets/{id}/summary`
- `POST /api/v1/pos/tickets/{id}/lines`
- `PATCH/DELETE /api/v1/pos/tickets/{id}/lines/{lineId}`

---

## Decisiones técnicas destacables

- Cliente desacoplado del backend: el backend puede reutilizarse para web/móvil.
- DTOs en el cliente alineados con el contrato REST.
- Manejo de configuración (URL API) en Settings con persistencia local.
- Separación por módulos/pantallas para escalar el proyecto sin “mega-controllers”.

---

## Próximas extensiones posibles

- Impresión/exportación de tickets
- Listado de tickets pagados por rango de fechas
- Usuarios y permisos más granulares
- Export fiscal (CSV/PDF)

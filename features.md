# Funcionalidades – TPV Desktop

Este documento describe de forma estructurada las **funcionalidades implementadas** en el sistema TPV Desktop, explicando el comportamiento real del sistema desde el punto de vista funcional y técnico.

El objetivo del proyecto es ofrecer un **TPV completo y funcional**, desacoplado del backend y preparado para ampliaciones futuras.

---

## 1. Autenticación y roles

### Login
- Acceso mediante usuario y contraseña.
- Autenticación gestionada por el backend.
- Recepción de token JWT.

### Roles
- **ADMIN**
  - Apertura y cierre de caja
  - Cierre fiscal
  - Cancelación de tickets
  - Acceso completo
- **USER**
  - Ventas
  - Consulta de tickets e información fiscal

El cliente envía el token JWT en cada petición REST.

---

## 2. Gestión de caja (Cash Session)

### Apertura de caja
- Introducción de efectivo inicial.
- La caja pasa a estado **OPEN**.
- Se habilitan las ventas.

### Estado de caja
- El sistema valida que exista una caja abierta antes de permitir ventas.
- Solo puede existir **una caja abierta a la vez**.

### Cierre de caja
- Se registra:
  - efectivo esperado
  - efectivo contado
  - diferencia
- La caja pasa a estado **CLOSED**.
- Las ventas quedan bloqueadas hasta una nueva apertura.

---

## 3. Ventas (Sales)

### Creación de ticket
- Creación automática de ticket asociado a la caja abierta.
- Cada ticket mantiene su estado:
  - OPEN
  - PAID
  - CANCELLED

### Gestión de líneas
- Añadir productos al ticket.
- Modificar cantidades.
- Eliminar líneas.
- Recalculo automático de totales.

### Cobro
- Métodos soportados:
  - Efectivo
  - Tarjeta
  - Bizum
- Registro de pagos asociado al ticket.
- Cambio de estado del ticket a **PAID**.

---

## 4. Historial de tickets

Desde la pantalla **Tickets History** se puede:

- Listar tickets existentes.
- Consultar el detalle completo:
  - líneas
  - pagos
  - totales
- Reabrir un ticket en la pantalla de ventas (si aplica).

Esta funcionalidad permite:
- auditoría
- corrección de errores
- revisión de ventas pasadas

---

## 5. Fiscal Summary

Pantalla informativa del estado fiscal de la caja.

### Información mostrada
- Número de tickets pagados
- Número de tickets cancelados
- Ventas:
  - bruto
  - neto
  - IVA
- Pagos agrupados por método

### Características
- No modifica datos.
- Puede consultarse múltiples veces durante el turno.
- Pensada para control y seguimiento.

---

## 6. Fiscal Closure (Cierre fiscal)

Proceso crítico de cierre del turno.

### Preview de cierre
- Efectivo inicial
- Ventas en efectivo
- Efectivo esperado

### Proceso de cierre
1. Introducción del efectivo contado.
2. Cálculo automático de la diferencia.
3. Confirmación del cierre.
4. Persistencia del cierre para auditoría.

### Resultado
- Caja cerrada definitivamente.
- Bloqueo de ventas.
- Datos preparados para revisión fiscal.

---

## 7. Settings

Pantalla de configuración del cliente.

### Funcionalidades
- Configuración de la URL del backend (API Gateway).
- Test de conexión.
- Logout del usuario.

### Persistencia
- Los ajustes se guardan localmente.
- Se mantienen entre ejecuciones del cliente.

---

## 8. Seguridad y validaciones

- Validación de estado de caja antes de vender.
- Restricción de acciones críticas por rol.
- Manejo de errores claros en el cliente.
- Importes gestionados en céntimos para evitar errores de precisión.

---

## 9. Experiencia de usuario (UX)

- Navegación centralizada por vistas.
- Feedback visual ante errores y acciones críticas.
- Estados bloqueados cuando no se cumplen precondiciones.
- Flujo natural de trabajo de un TPV real.

---

## 10. Estado actual del sistema

El TPV Desktop es **funcional de extremo a extremo**:

- Desde apertura de caja
- Hasta cierre fiscal

El sistema está preparado para:
- ampliaciones funcionales
- nuevos clientes (web / móvil)
- uso en entornos reales de hostelería o comercio

---

## 11. Posibles mejoras futuras

- Exportación fiscal (PDF / CSV).
- Impresión de tickets.
- Gestión de usuarios avanzada.
- Multi-caja y multi-turno.
- Informes por rango de fechas.

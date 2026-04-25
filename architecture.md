# Arquitectura del sistema TPV

Objetivo: explicar como se conectan los modulos y donde tocar codigo segun el tipo de cambio.

## 1) Vista general

```text
TPV Desktop / PDA Web / PDA Android
                |
                v
          Gateway (:8080)
           |           |
           v           v
     Auth Service   POS Service
       (:8081)       (:8082)
            \       /
              MySQL
     (tpv_auth + tpv_pos)
```

## 2) Responsabilidad de cada modulo

### `services/auth-service`

- Login (`/api/v1/auth/login`).
- Perfil de sesion (`/api/v1/auth/me`).
- Gestion admin de usuarios y roles.
- Emision/validacion de JWT.

Roles existentes en codigo:

- `ADMIN`
- `ENCARGADO`
- `CAJERO`
- `CAMARERO`
- `USER` (legacy; se migra automaticamente a `CAMARERO` en bootstrap)

### `services/pos-service`

- Dominio operativo del TPV:
  - mesas/salones/locks
  - tickets/lineas/comandas
  - cobros/reembolsos
  - caja y cierre
  - facturacion
  - catalogo
  - auditoria

### `gateway`

- Punto unico de entrada para clientes.
- Routing a auth y pos.
- Hosting de PDA web en `/pda`.
- Politica especial `PdaCashGuardFilter`: bloquea apertura/cierre de caja desde PDA (`X-Client-App: PDA`).

### `tpv-desktop`

- Cliente JavaFX con flujo completo de negocio.
- Consume APIs via gateway.
- Gestiona impresion local y ajustes de impresora.

### `pda-android` + PDA web

- Flujo rapido de sala: abrir mesa, editar ticket, enviar comanda, cobrar, mover mesa.
- Siempre consumen gateway.

## 3) Datos y persistencia

- `tpv_auth`: usuarios/roles/sesion.
- `tpv_pos`: dominio de negocio (tickets, pagos, caja, etc).

En local puedes usar Docker (`docker/docker-compose.yml`).

## 4) Seguridad y contexto de request

Headers que importan:

- `Authorization: Bearer <jwt>`
- `X-Terminal-Id: <terminal-id>`
- `X-Client-App: PDA` (solo clientes PDA)

Impacto:

- permisos por rol en backend
- trazabilidad por terminal
- reglas especiales por tipo de cliente (ejemplo: caja desde PDA bloqueada)

## 5) Consistencia y concurrencia

Patrones principales:

- Lock de mesa + heartbeat para evitar doble edicion en varios terminales.
- Idempotencia en operaciones criticas (comanda/cobro/cierre) para tolerar reintentos.
- Reglas de conflicto (`409`) cuando dos actores compiten por el mismo recurso.

## 6) Mapa rapido para depurar

Si falla login/permisos:

- revisar `auth-service` y config JWT compartida.

Si falla una operacion de ticket/cobro:

- revisar `pos-service` en `controller -> service -> repository`.

Si falla solo en PDA pero backend funciona:

- revisar gateway y headers `X-Client-App` / `X-Terminal-Id`.

Si falla impresion:

- revisar `tpv-desktop` (servicios de cola de impresion y settings locales).

## 7) Donde extender sin romper mucho

- Nueva regla de negocio: `pos-service/service`.
- Nuevo endpoint: `controller` + `dto` + `service`.
- Nueva pantalla desktop: `tpv-desktop/ui` + viewmodel/controller.
- Nueva accion PDA: `pda-android/ui` + `data/api`.


# TPV Desktop – Sistema de Punto de Venta

TPV Desktop es un sistema de punto de venta desarrollado en **Java** orientado a negocios de hostelería y comercio minorista.  
El proyecto está diseñado con una arquitectura **backend desacoplada (microservicios)** y un **cliente de escritorio JavaFX**, siguiendo buenas prácticas de desarrollo profesional.

---

## Funcionalidades principales

- Autenticación con roles (ADMIN / USER)
- Apertura y cierre de caja
- Gestión de ventas (tickets)
- Cobro por múltiples métodos (efectivo, tarjeta, bizum)
- Historial de tickets con reapertura
- Resumen fiscal de la caja
- Cierre fiscal con control de descuadres
- Configuración de entorno (URL API, logout)

---

## Arquitectura

El sistema está compuesto por:

- **Backend**: Microservicios Java (Spring Boot)
- **Cliente**: Aplicación de escritorio JavaFX
- **Comunicación**: API REST + JWT
- **Persistencia**: Base de datos relacional
- **Configuración**: Entornos desacoplados mediante settings

El cliente TPV consume exclusivamente la API REST, lo que permite:
- reutilizar el backend para otros clientes (web, móvil)
- escalar el sistema fácilmente

---

## Cliente TPV (JavaFX)

El cliente de escritorio está desarrollado con:
- Java 21
- JavaFX (FXML + Controllers)
- Arquitectura modular por vistas
- Navegación centralizada
- Gestión de estado y sesión

Pantallas principales:
- Login
- Ventas (Sales)
- Caja
- Historial de tickets
- Fiscal Summary
- Fiscal Closure
- Settings

---

## Requisitos

- Java JDK 21+
- Maven
- Backend en ejecución (API REST)

---

## Ejecución del cliente

```bash
mvn clean javafx:run

# Diseño de mejora del portfolio GitHub

Fecha: 2026-07-28

## Objetivo

Hacer que el perfil público de José Ángel comunique en menos de 30 segundos una propuesta de valor honesta y relevante para puestos Java Backend/Spring Boot junior.

El mensaje principal será:

> Desarrollador Java Backend junior con formación DAM y experiencia práctica construyendo un TPV real con Java 21, Spring Boot, APIs REST, seguridad, persistencia, pruebas y clientes Desktop/Android.

## Contexto observado

El repositorio `tpv-microservices` demuestra más nivel técnico del que comunica:

- Java 21 y Spring Boot 3.5.9.
- Gateway, autenticación JWT y autorización por roles.
- Reglas reales de tickets, comandas, pagos, caja y facturación.
- Tratamiento de concurrencia e idempotencia.
- MySQL mediante Docker Compose.
- Clientes JavaFX y Android/Kotlin.
- 27 archivos de pruebas y 101 métodos `@Test`.
- Workflows y scripts de smoke test.
- 121 commits.

La presentación pública actual no tiene biografía profesional ni README de perfil. Barix carece de descripción, topics, capturas y una introducción orientada a portfolio.

## Enfoques considerados

### A. Cambio mínimo

Actualizar solamente biografía, descripción y topics.

Ventaja: se completa muy rápido.  
Desventaja: el README seguiría ocultando el valor técnico y el carácter real del proyecto.

### B. Presentación primero

Crear el README de perfil, rehacer la portada de Barix, añadir metadatos y preparar espacios para capturas. No modificar todavía código de negocio, persistencia ni despliegue.

Ventaja: mayor impacto inmediato con bajo riesgo.  
Desventaja: CI, credenciales locales y limpieza interna quedarían para una segunda fase.

### C. Reforma integral

Combinar presentación, limpieza del repositorio, CI, migraciones y endurecimiento de configuración en un único cambio.

Ventaja: resultado técnico más completo.  
Desventaja: mezcla objetivos distintos, aumenta el riesgo y retrasa la mejora visible del portfolio.

## Decisión

Se elige el enfoque B.

La primera fase incluirá:

1. README del perfil público.
2. Nuevo README de Barix orientado a recruiter y técnico.
3. Descripción corta y topics de los repositorios públicos.
4. Orden recomendado de repositorios pineados.
5. Preparación de `docs/images/` para capturas reales.

La segunda fase, fuera de este cambio, cubrirá:

- limpieza de `.run/` y `.run-logs/`;
- CI para toda la suite;
- reactivación del E2E;
- licencia y releases;
- credenciales exclusivamente por entorno;
- migraciones versionadas;
- revisión del módulo `billing-service`.

## Diseño del perfil público

La portada tendrá:

1. Presentación breve como DAM y Java Backend junior.
2. Barix como proyecto destacado.
3. Tres proyectos secundarios.
4. Tecnologías relevantes para el puesto objetivo.
5. Estado actual y disponibilidad geográfica.

No se usarán frases como “experto”, “arquitectura empresarial” o afirmaciones de experiencia profesional no acreditada.

Biografía:

> Desarrollador Java Backend Junior | Java 21, Spring Boot, REST, SQL y Docker | Técnico Superior DAM | Jaén · remoto o híbrido en Madrid

## Diseño del README de Barix

Orden de lectura:

1. Nombre Barix TPV.
2. Stack y propuesta de valor.
3. Origen como solución para una necesidad real.
4. Capturas de Desktop, ticket y PDA.
5. Funcionalidades.
6. decisiones backend diferenciadoras.
7. Diagrama Mermaid.
8. Stack por áreas.
9. Evidencia de pruebas y automatización.
10. Ejecución local.
11. Resumen de endpoints.
12. Estado, próximos pasos y autoría.

La documentación interna existente seguirá enlazada al final. El README dejará de funcionar principalmente como onboarding y pasará a funcionar primero como escaparate técnico.

## Capturas

Se reservarán tres posiciones iniciales:

- pantalla principal de mesas;
- edición de ticket o comanda;
- PDA Android.

No se inventarán imágenes. Hasta que José Ángel proporcione capturas reales, el borrador mantendrá marcadores explícitos que no deben publicarse como versión final.

Las imágenes se guardarán en `docs/images/` con nombres estables:

- `barix-desktop-mesas.png`;
- `barix-desktop-ticket.png`;
- `barix-pda-android.png`.

## Metadatos

Descripción de Barix:

> Barix TPV: sistema para hostelería con Java 21, Spring Boot, microservicios, REST, JWT, MySQL, JavaFX y Android/Kotlin.

Topics:

`java`, `spring-boot`, `microservices`, `rest-api`, `mysql`, `jwt`, `docker-compose`, `javafx`, `kotlin`, `android`, `point-of-sale`, `hospitality`

Orden de repositorios:

1. `tpv-microservices`.
2. `reservas-api-spring-boot`.
3. `profich`.
4. `agendaRestaurante`.

## Badges

La primera versión solo mostrará badges estáticos verificables:

- Java 21.
- Spring Boot 3.5.
- MySQL 8.
- Docker Compose.

El badge de CI se añadirá después de crear un workflow que ejecute la suite completa y esté en verde. No se mostrará cobertura hasta medirla.

## Seguridad y honestidad

Las credenciales de demostración se identificarán como exclusivamente locales. El README indicará que cualquier despliegue debe proporcionar secretos y contraseñas mediante variables de entorno.

La sección de autoría explicará que el proyecto es individual y que se han utilizado documentación y herramientas de IA como apoyo, sin atribuir a José Ángel experiencia profesional formal que todavía no tiene.

## Validación

Antes de publicar:

- verificar enlaces Markdown;
- confirmar que las cifras de tests y commits siguen siendo correctas;
- comprobar que el diagrama Mermaid se renderiza en GitHub;
- revisar que no se publican marcadores de capturas;
- comprobar que la guía de arranque coincide con los módulos actuales;
- revisar el diff completo y mantener fuera cualquier cambio de código de negocio.

## Criterio de éxito

La mejora será correcta si un visitante puede identificar, sin abrir el código:

- el puesto objetivo;
- el stack principal;
- que Barix resuelve una necesidad real;
- qué partes implementó José Ángel;
- la arquitectura general;
- qué calidad y pruebas existen;
- cómo ejecutar el sistema;
- el estado real y los próximos pasos.


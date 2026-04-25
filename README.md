# TPV Microservices (Desktop + PDA)

Repositorio principal de TPV para bar/restaurante.

Este proyecto combina backend Java, cliente Desktop JavaFX y clientes PDA (web + Android).
El objetivo de esta documentacion es que alguien nuevo pueda levantar el sistema, entender el flujo y empezar a aportar sin perderse.

## 1) Que incluye el repo

- `services/auth-service`: login, JWT, usuarios y roles.
- `services/pos-service`: logica TPV (mesas, tickets, comandas, cobros, caja, fiscal, catalogo).
- `gateway`: entrada unica para clientes y hosting de PDA web en `/pda`.
- `tpv-desktop`: cliente JavaFX para caja/sala/admin.
- `pda-android`: app Android nativa para camareros.
- `docker/`: MySQL para desarrollo.
- `scripts/`: automatizaciones de build, backup/restore y QA smoke.

## 2) Arquitectura en una frase

Clientes (Desktop/PDA) -> Gateway (`:8080`) -> Auth (`:8081`) y POS (`:8082`) -> MySQL (`tpv_auth`, `tpv_pos`).

Mas detalle en `architecture.md`.

## 3) Requisitos de desarrollo

- Windows + PowerShell (scripts principales estan pensados para Windows).
- JDK 21.
- Docker Desktop (para MySQL local).
- Git.
- Android Studio si vas a tocar `pda-android`.

## 4) Arranque rapido (primera vez)

### 4.1 Levantar base de datos

```powershell
cd docker
docker compose up -d mysql
```

### 4.2 Levantar backend (3 terminales)

```powershell
cd services/auth-service
.\mvnw.cmd -q -DskipTests spring-boot:run
```

```powershell
cd services/pos-service
.\mvnw.cmd -q -DskipTests spring-boot:run
```

```powershell
cd gateway
.\mvnw.cmd -q -DskipTests spring-boot:run
```

### 4.3 Verificar que responde

- PDA web: `http://localhost:8080/pda`
- Health POS: `http://localhost:8080/api/v1/pos/health`

Usuario admin de bootstrap:

- usuario: `admin`
- password: `admin123`

### 4.4 Levantar Desktop

```powershell
cd tpv-desktop
.\mvnw.cmd -q -Dtpv.mode=real -Dtpv.auto.login=false javafx:run
```

### 4.5 Levantar PDA Android (opcional)

Desde raiz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-pda-android.ps1 -Install
```

## 5) Flujo funcional minimo para probar todo

1. Login con `admin/admin123`.
2. Abrir caja.
3. Abrir mesa.
4. Anadir linea al ticket.
5. Enviar comanda.
6. Cobrar ticket.
7. Cerrar caja.

Si este flujo funciona, el stack principal esta sano.

## 6) Donde empezar si eres junior

1. Lee `docs/onboarding-junior.md`.
2. Sigue `architecture.md` para entender responsabilidades por modulo.
3. Mira `api.md` para headers y endpoints base.
4. Revisa `runbook` para operacion real e incidencias.

## 7) Comandos utiles

Smoke E2E PDA:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pda-e2e-smoke.ps1
```

Smoke backup+restore:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup-restore-smoke.ps1 -Mode auto -RootPassword root
```

Backup manual:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\db-backup.ps1 -Compress
```

## 8) Documentacion por tema

- Arquitectura: `architecture.md`
- API operativa: `api.md`
- Funcionalidades: `features.md`
- Contribucion: `CONTRIBUTING.md`
- Operacion diaria y soporte: `runbook`
- Instalacion en bar: `docs/install-bar-windows.md`
- Backup/restore: `docs/backup-restore.md`
- Onboarding junior: `docs/onboarding-junior.md`

## 9) Notas de higiene del repo

No subir al repo:

- builds (`target/`, `build/`, `dist/`)
- logs (`.runlogs/`, `.run-logs/*.log`)
- backups (`.backups/`)


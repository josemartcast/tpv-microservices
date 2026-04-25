# Contributing Guide

Guia para colaborar sin romper el flujo del equipo.

## 1) Modelo de ramas

- `main`: estable (release).
- `dev`: integracion diaria.
- `feature/<tema>`: nueva funcionalidad.
- `fix/<tema>`: bug normal.
- `hotfix/<tema>`: bug urgente sobre `main`.

Ejemplos:

- `feature/pda-send-preview`
- `fix/ticket-lock-timeout`
- `hotfix/cash-close-conflict`

## 2) Flujo diario recomendado

```bash
git checkout dev
git pull
# cambios
git add .
git commit -m "feat: ..."
git push
```

Abrir PR contra `dev`.

## 3) Antes de abrir PR

1. Compilar modulos tocados.
2. Ejecutar tests relevantes.
3. Revisar que no subes artefactos (`target`, `build`, `dist`, logs, backups).
4. Actualizar documentacion si cambias flujo operativo o endpoints.

## 4) Comandos base de validacion

Backend:

```powershell
cd services/auth-service
.\mvnw.cmd test
```

```powershell
cd services/pos-service
.\mvnw.cmd test
```

```powershell
cd gateway
.\mvnw.cmd test
```

Desktop:

```powershell
cd tpv-desktop
.\mvnw.cmd test
```

Android (si aplica):

```powershell
cd pda-android
.\gradlew.bat testDebugUnitTest
```

## 5) Estilo de commits

Prefijos recomendados:

- `feat:`
- `fix:`
- `refactor:`
- `test:`
- `docs:`
- `build:`
- `chore:`

Ejemplos:

- `feat: add table alias endpoint for salon`
- `fix: prevent duplicate payment in race scenario`
- `docs: update onboarding for junior developers`

## 6) Checklist de PR (practico)

- [ ] Cambio pequeno y entendible.
- [ ] No rompe flujo caja -> ticket -> cobro.
- [ ] No rompe permisos por rol.
- [ ] Maneja conflictos (`409`) de forma explicita si aplica.
- [ ] Tiene tests o justificacion de por que no aplica test.
- [ ] Documentacion alineada.

## 7) Reglas de oro

1. Prefiere PRs pequenas.
2. No mezcles refactor grande con bugfix urgente.
3. Si tocas seguridad o caja, pide segunda revision.
4. Si cambias contrato API, avisa a Desktop/PDA.


# Contributing Guide

Este repositorio usa un flujo simple: `main` estable y `dev` para trabajo diario.

## Branching model

- `main`: rama estable, lista para bar/produccion.
- `dev`: integracion continua de cambios.
- `feature/<tema>`: nueva funcionalidad.
- `fix/<tema>`: bug normal.
- `hotfix/<tema>`: bug urgente en produccion.
- `release/<version>` (opcional): congelacion previa a merge en `main`.

Ejemplos:

- `feature/pda-precuenta-estado`
- `fix/comanda-missing-line`
- `hotfix/print-fallback`

## Daily workflow

```bash
git checkout dev
git pull
# cambios
git add .
git commit -m "feat: ..."
git push
```

## Release workflow

1. Asegurar `dev` en verde (CI + smoke).
2. Merge `dev -> main`.
3. Tag de version.

```bash
git checkout main
git pull
git merge --no-ff dev
git push
git tag v1.0.0
git push origin v1.0.0
```

## Hotfix workflow

```bash
git checkout main
git pull
git checkout -b hotfix/<tema>
# fix
git add .
git commit -m "fix: ..."
git push -u origin hotfix/<tema>
```

Despues del merge del hotfix en `main`, sincronizar tambien en `dev`.

## Commit style

Usar prefijos cortos:

- `feat:`
- `fix:`
- `refactor:`
- `perf:`
- `test:`
- `docs:`
- `build:`
- `chore:`

Ejemplos:

- `feat: add prebill state in table map`
- `fix: avoid duplicated partial payment totals`
- `build: harden desktop login defaults`

## Pull request checklist

- Compila local en los modulos tocados.
- Tests locales basicos en verde.
- No incluir logs/runtime (`.runlogs`, `.run-logs`, `dist` temporales).
- Si toca TPV/PDA: validar flujo minimo (mesa, comanda, cobro).
- Si toca impresion: probar ticket + precuenta + factura.
- Si toca seguridad: validar roles y endpoints bloqueados.

## Version tags

- RC: `v1.0.0-rc1`
- Final: `v1.0.0`
- Patch: `v1.0.1`

## Notes for this project

- Mantener `main` libre de cambios experimentales.
- Preferir PRs pequenas y revisables.
- Documentar cambios operativos en `docs/` cuando afecten instalacion o uso en bar.

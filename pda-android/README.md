# PDA Android nativa

Estado documentado a fecha: 2026-04-13.

App Android para operativa de camarero conectada al backend actual (sin backend paralelo).

## Funcionalidad actual

- Login real (`/api/v1/auth/login`).
- Mapa de mesas con filtros por salon y estados operativos.
- Apertura de mesa con lock + heartbeat.
- Ticket completo:
  - anadir producto
  - anadir combinado COPA + REFRESCO
  - editar cantidad
  - editar precio
  - editar nota
  - borrar linea
- Enviar comanda por destino.
- Solicitar precuenta.
- Cobro (total/parcial por metodo).
- Mover mesa.
- Salida de mesa con modal de envio si hay pendientes.
- `cancel-empty` al salir sin consumo.
- Mensajeria de error amigable para usuario final.
- UI responsive en portrait y landscape.

## Seguridad y headers

Cada request a POS se envia con:

- `Authorization: Bearer <token>`
- `X-Terminal-Id: <terminal-id>`
- `X-Client-App: PDA`

## Configuracion de servidor

Base URL en pantalla de login (persistida localmente).

Ejemplos:

- local: `http://10.0.2.2:8080` (emulador Android)
- LAN: `http://192.168.x.x:8080`
- Tailscale/MagicDNS: `http://<host>.tail<id>.ts.net:8080`

## Ejecutar desde CLI

Desde raiz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-pda-android.ps1
```

Opciones utiles:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-pda-android.ps1 -Clean -Install
```

## Build APK

```powershell
cd pda-android
.\gradlew.bat :app:assembleDebug
```

APK debug:

- `pda-android\app\build\outputs\apk\debug\app-debug.apk`

## Instalar en dispositivo por ADB

```powershell
cd pda-android
.\gradlew.bat :app:installDebug
```

## Observaciones operativas

- Si hay error de cleartext en emulador, usar `10.0.2.2` o URL permitida por config de red.
- Si no conecta por datos moviles, revisar que Tailscale este activo en movil y portatil.
- Si hay expiracion de sesion, relogin y revisar reloj/dispositivo.

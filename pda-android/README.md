# TPV PDA Android (Native)

App Android nativa inicial para la PDA, conectada al backend actual sin cambiar servicios.

## Estado actual

- Login real contra `POST /api/v1/auth/login`
- Mesas reales desde `GET /api/v1/pos/salon/tables`
- Headers de seguridad:
  - `Authorization: Bearer <token>`
  - `X-Terminal-Id: <terminal>`
- URL de servidor configurable desde la app y persistida localmente.

## Uso

1. Abre `pda-android` con Android Studio.
2. Espera a que sincronice Gradle.
3. Ejecuta la app en móvil/emulador.
4. En login, configura el servidor:
   - Local: `http://<ip-local>:8080`
   - Tailscale/MagicDNS: `https://jose.tail079f7b.ts.net`
5. Inicia sesión con usuario real (`admin` / `admin123` o el que uses).

## Build rapido (CLI)

Desde raiz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-pda-android.ps1
```

Opciones:

- `-Clean` limpia antes de compilar.
- `-Install` instala en dispositivo/emulador ADB conectado.
- `-OpenStudio` abre Android Studio al terminar.

Tambien disponible tarea Gradle:

```powershell
cd pda-android
.\gradlew.bat :app:runDebug
```

## Siguiente bloque recomendado

- Pantalla de ticket (abrir mesa, líneas, editar, borrar, enviar comanda, cobro).
- Cola offline con Room.
- Move table y alias de mesa.

# PDA remota con Tailscale (operativa)

Objetivo: que la PDA funcione por datos moviles sin abrir puertos del router.

Arquitectura:
- PDA (movil) -> red Tailscale -> portatil TPV -> `http://localhost:8080/pda`

## 1) Instalar Tailscale en portatil y movil
- Portatil Windows: app Tailscale Desktop.
- Movil Android/iOS: app Tailscale.
- Iniciar sesion con la misma cuenta/tailnet.

## 2) Verificar conexion entre equipos
En portatil, comprobar IP Tailscale:

```powershell
tailscale ip -4
```

Ejemplo: `100.88.42.10`

En movil, abrir:

`http://100.88.42.10:8080/pda`

## 3) Recomendado: MagicDNS
Si tienes MagicDNS activo en Tailscale, usa hostname estable:

`http://NOMBRE-EQUIPO.tailXXXX.ts.net:8080/pda`

Ventaja: no dependes de memorizar IP.

## 4) Seguridad minima recomendada
- No exponer `8080` a Internet publica.
- Mantener acceso solo via red Tailscale.
- Usar usuarios/roles TPV normales (no compartir admin en PDA).

## 5) Integracion en instalador (MVP)
Se puede incluir Tailscale en instalador, pero:
- login requiere cuenta del cliente (interactivo) o auth key de despliegue.
- no se recomienda embebar credenciales fijas dentro del instalador.

Propuesta:
1. instalador principal TPV
2. paso opcional "Instalar Tailscale"
3. asistente abre `tailscale up` para login del negocio
4. guardar en Settings la URL base PDA usando hostname MagicDNS

## 6) Script de preinstalacion (portatil)
Incluido en repo:

`scripts/install-portatil-prereqs.ps1`

Ejemplos:

Instalar prerequisitos base (sin Tailscale):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-portatil-prereqs.ps1
```

Instalar tambien Tailscale (login interactivo):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-portatil-prereqs.ps1 -InstallTailscale
```

Instalar Tailscale con auth key (despliegue gestionado):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-portatil-prereqs.ps1 -InstallTailscale -TailscaleAuthKey "tskey-xxxxx"
```


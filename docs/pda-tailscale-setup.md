# PDA remota con Tailscale

Estado documentado a fecha: 2026-04-13.

Objetivo: usar PDA por datos moviles sin abrir puertos del router.

## Topologia

PDA -> red Tailscale -> portatil TPV -> gateway `:8080`

## 1) Instalacion

Instalar Tailscale en:

- portatil TPV (Windows)
- movil PDA (Android/iOS)

Iniciar sesion con la misma cuenta/tailnet.

## 2) Direccion recomendada

Usar MagicDNS para no depender de IP dinamica:

```text
http://<host>.tail<id>.ts.net:8080
```

Para PDA web, abrir:

```text
http://<host>.tail<id>.ts.net:8080/pda
```

Para PDA Android nativa, configurar base URL:

```text
http://<host>.tail<id>.ts.net:8080
```

## 3) Comprobacion rapida

En portatil:

```powershell
tailscale status
tailscale ip -4
```

En movil (navegador):

```text
http://<host>.tail<id>.ts.net:8080/pda
```

## 4) Buenas practicas

- No abrir puertos del router.
- No exponer 8080 a internet publica.
- Mantener usuarios/roles separados por dispositivo.
- Definir `Terminal ID` unico por PDA.

## 5) Instalacion con script

Script de prerequisitos del portatil:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-portatil-prereqs.ps1 -InstallTailscale
```

Con auth key (despliegue gestionado):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-portatil-prereqs.ps1 -InstallTailscale -TailscaleAuthKey "tskey-xxxxx"
```

## 6) Problemas comunes

- `No se encuentra el servidor`: revisar que Tailscale este activo en ambos dispositivos.
- `ERR_SSL_PROTOCOL_ERROR`: usar `http://` en MagicDNS interno (no forzar `https://` sin cert).
- Cambiaste de red y no conecta: refrescar Tailscale y volver a probar URL.

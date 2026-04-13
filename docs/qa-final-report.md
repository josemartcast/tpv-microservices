# QA final report (estado actual)

Fecha de corte: 2026-04-13.

Estado global: `ESTABLE EN PRODUCCION CONTROLADA`.

## Validado

- Flujo core TPV Desktop en backend real.
- PDA web y PDA Android con operativa completa de mesa.
- Roles y seguridad (incluido bloqueo de caja desde PDA).
- Caja, cobros parciales/totales, cierre con resumen.
- Historial, facturas y reimpresion.
- Catalogo, salones, alias, impresoras por destino.
- CI smoke:
  - `PDA E2E Smoke`
  - `DB Backup Restore Smoke`

## Mejoras ya cerradas recientemente

- Reenviar comanda usa impresora real configurada.
- Formato de ticket/precuenta/factura ajustado para 80mm.
- Ticket largo en modo continuo con corte final.
- Correcciones de codificacion de caracteres en impresion.
- Ajustes responsive PDA (portrait/landscape).
- `PRECUENTA_PEDIDA` visible en mapas.
- Correccion de cobro duplicado al reabrir ticket pagado.

## Riesgo residual monitorizado

- Incidencia rara e intermitente reportada en bar:
  - alguna linea de comanda enviada desde PDA no llega a impresora en casos aislados.
- Mitigacion en curso:
  - trazas de send/preview/lineas
  - serializacion de acciones criticas en PDA
  - seguimiento en sesiones largas reales

## Recomendacion operativa

- Mantener despliegue controlado durante semana de alta carga.
- Registrar cada incidencia de comanda con:
  - hora
  - mesa
  - ticketId
  - linea esperada vs impresa
- Si no aparecen nuevos casos en ventana de observacion, marcar candidato de release.

## Veredicto actual

`APTO PARA CONTINUAR PRE-RELEASE`, con vigilancia activa del flujo de comanda PDA.

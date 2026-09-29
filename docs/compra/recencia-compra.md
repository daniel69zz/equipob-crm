# Recencia de compra del cliente (SCRUM-19)

La recencia informa el tiempo exacto transcurrido desde la compra vigente confirmada más reciente
del cliente. Se calcula al consultar los indicadores; no se guarda en el perfil ni en otra tabla.

## Fuente y selección

- La fuente es `comportamiento.compra.fecha`, que RIO-CRM-02 define como el momento en que se
  confirmó la compra. `registrada_en` no se usa porque solo indica cuándo la recibió el CRM.
- Se combinan los pares `ORIGEN:idCliente` del perfil, igual que en el historial y el ticket promedio.
- La base obtiene `MAX(fecha)` entre las compras vigentes. Una devolución parcial sigue vigente y
  una compra anulada por completo queda fuera, conforme a RIO-CRM-05.
- Sin identificadores o sin compras vigentes, la recencia queda sin datos.

## Cálculo

La diferencia se calcula entre instantes, no entre fechas locales:

```text
tiempoTranscurrido = instante actual del servicio - instante de la última compra
```

Se usa un `Clock` UTC reemplazable en pruebas. La duración se expone como ISO-8601 para conservar
la precisión sin imponer días, horas ni redondeos no definidos por la historia. Si una fecha futura
ya registrada llega a la consulta, se conserva la duración negativa: SCRUM-19 no agrega una regla de
validación o corrección que el contrato de compra no define.

## Consulta

Se amplía el endpoint existente:

```http
GET /api/comportamiento/clientes/{clienteId}/indicadores
    ?identificador=MARKETPLACE:mp-user-3307
    &identificador=VENTAS:CLI-5521
```

Ejemplo:

```json
{
  "ticketPromedio": {
    "valor": 240.25,
    "compras": 2,
    "montoAcumulado": 480.50,
    "sinDatos": false
  },
  "recencia": {
    "ultimaCompra": "2026-09-27T19:28:10Z",
    "tiempoTranscurrido": "PT49H30M",
    "sinDatos": false
  }
}
```

Cliente sin compras vigentes:

```json
{
  "recencia": {
    "ultimaCompra": null,
    "tiempoTranscurrido": null,
    "sinDatos": true
  }
}
```

El endpoint conserva `INDICADORES_CONSULTAR`, la autorización y la auditoría ya configuradas para
el Sistema Agente de Atención al Cliente.

# Vinculación de una interacción con una compra (SCRUM-54)

El Agente de Atención al Cliente vincula una interacción con una compra del historial del cliente para relacionar
un reclamo con la compra que lo originó (RF-46, EPIC-09). Se apoya en el registro (SCRUM-52), el seguimiento
(SCRUM-55) y el historial del cliente (SCRUM-53), y en el historial de compras de `servicio-comportamiento`
(SCRUM-16). Diagramas en `docs/interacciones/vinculacion-compras-diagramas.md`.

## Regla de asociación (SCRUM-443)

### Referencia libre y compra vinculada

Una interacción ya tenía `referenciaCompra` (SCRUM-433): un **texto libre** que escribe el agente con lo que dice
el cliente, sin comprobar. La vinculación es distinta: es una **compra comprobada** del historial del cliente, con
su fecha, su monto y su estado tal como estaban al vincularla.

| | `referenciaCompra` | `compra` (vinculada) |
|---|---|---|
| Quién la pone | El agente al registrar o corregir (`POST` y `PUT`) | El agente al vincular (`PUT …/compra`) |
| Se comprueba | No | Sí: debe ser una compra del mismo cliente |
| Qué guarda | El texto | Referencia, fecha, monto total y estado de la compra, quién la vinculó y cuándo |

Una interacción tiene **como máximo una** compra vinculada. Vincular otra la **reemplaza**, y el cambio queda en la
auditoría con la anterior y la nueva.

### Cuándo se puede vincular

| Regla | Si no se cumple |
|---|---|
| La compra es **del mismo cliente** que la interacción: aparece en su historial de compras (criterio de aceptación 2) | `400` con `campos.referencia` "no es una compra del cliente" |
| La interacción **no está cerrada** (una cerrada es constancia del caso, SCRUM-453) | `409` "La interacción está cerrada: reábrala para modificarla" |
| La `version` es la que el usuario tenía en pantalla | `409` con `campos.version` |
| La compra no es la que ya está vinculada | `400` "La interacción ya está vinculada a esa compra" |
| `servicio-comportamiento` responde: sin él no se puede comprobar la compra | `503`, y no se guarda nada |
| El cliente tiene identificadores de Marketplace y Ventas | Sin ellos no tiene compras: `400` con `campos.referencia` |

- **Tipos de interacción:** se vinculan los tres. Lo habitual es un **reclamo** o una **consulta**; una **nota**
  también puede referirse a una compra.
- **Estado de la compra:** se vinculan compras **confirmadas, con devolución parcial o anuladas**. Un reclamo puede
  ser justamente por una compra anulada.
- **Búsqueda de la compra:** se busca entre las **500 más recientes** del cliente, que son las que puede elegir el
  agente en pantalla.
- **Desvincular:** `DELETE …/compra` quita la compra vinculada con las mismas reglas de estado y versión. Queda en
  la auditoría.

### Quién vincula

- El **Agente de Atención** o el **Administrador de CRM**, con `INTERACCIONES_REGISTRAR` y con
  `INDICADORES_CONSULTAR`, que es el permiso que da acceso al historial de compras.
- **No hace falta haber registrado la interacción.** Igual que el seguimiento, un caso pasa de un agente a otro
  durante la atención.
- El **Gerente Comercial** ve la compra vinculada, pero no la cambia: el Gateway le responde `403`, porque no tiene
  `INTERACCIONES_REGISTRAR`.

### Auditoría (criterio de aceptación 3)

Cada vinculación y cada desvinculación dejan, en la misma transacción que el cambio:

- En `interacciones.interaccion_auditoria`: la operación `COMPRA_VINCULADA` o `COMPRA_DESVINCULADA`, el usuario,
  su rol, la fecha y `cambios.compra` con la referencia anterior y la nueva.
- En la auditoría compartida (`auditoria.evento`): `INTERACCION_COMPRA_VINCULADA` o
  `INTERACCION_COMPRA_DESVINCULADA`, con la entidad `CLIENTE` y la referencia de la compra en el detalle.
- En la propia interacción: `compra.vinculadaPor` y `compra.vinculadaEn`.

## API

Todas bajo el cliente, como pide `docs/seguridad/convencion-rutas-clientes.md`.

### Compras del cliente para elegir (SCRUM-446)

```
GET /api/interacciones/clientes/{clienteId}/compras?texto=V-10&limite=20
```

- Devuelve las compras del cliente, de la más reciente a la más antigua.
- `texto` es opcional y filtra por la referencia, sin distinguir mayúsculas.
- `limite` va de 1 a 100, por defecto 20.
- Si Comportamiento no responde, devuelve `disponible: false` y una lista vacía, con un aviso.
- Exige el rol de Agente o Administrador con `INTERACCIONES_CONSULTAR` e `INDICADORES_CONSULTAR`, como el
  historial del cliente (SCRUM-438).

```json
{
  "compras": [
    { "fecha": "2026-10-20T15:30:00Z", "referencia": "V-1001", "montoTotal": 350.00, "estado": "CONFIRMADA",
      "items": [{ "categoria": "Electrónica", "cantidad": 1, "monto": 350.00 }] }
  ],
  "disponible": true,
  "avisos": []
}
```

### Vincular y desvincular (SCRUM-445 y SCRUM-447)

```
PUT    /api/interacciones/clientes/{clienteId}/interacciones/{id}/compra   { "referencia": "V-1001", "version": 2 }
DELETE /api/interacciones/clientes/{clienteId}/interacciones/{id}/compra?version=3
```

Responden la interacción actualizada, con la versión nueva y la compra vinculada:

```json
{
  "id": 7, "tipo": "RECLAMO", "estado": "EN_PROCESO", "version": 3,
  "compra": {
    "referencia": "V-1001", "fecha": "2026-10-20T15:30:00Z", "montoTotal": 350.00, "estado": "CONFIRMADA",
    "vinculadaPor": "agente.ana", "vinculadaEn": "2026-11-09T14:05:00Z"
  }
}
```

Una interacción sin compra vinculada tiene `compra: null`. El Gateway registra `CLIENTE_MODIFICADO` porque la
ruta es de cliente.

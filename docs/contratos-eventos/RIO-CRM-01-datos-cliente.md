# RIO-CRM-01 · Datos del cliente

Evento que Marketplace y Ventas publica cuando registra un cliente o cambia sus datos. El CRM lo usa para crear y mantener el perfil único del cliente.

| | |
|---|---|
| **Emisor** | Marketplace y Ventas (canal `MARKETPLACE` o `VENTAS`) |
| **Receptor** | `servicio-perfil` |
| **Transporte** | RabbitMQ, mensaje JSON en UTF-8 |
| **Exchange** | `ventas.eventos` (tipo `topic`, durable) |
| **Routing keys** | `cliente.registrado`, `cliente.actualizado` |
| **Cola del CRM** | `crm.perfil.clientes` (durable, enlazada con `cliente.*`) |

Cada evento trae la **foto completa** del cliente en el sistema de origen, no solo lo que cambió: un campo ausente o vacío significa que el cliente no tiene ese dato.

## Campos

| Campo | Tipo | Obligatorio | Descripción |
|---|---|:---:|---|
| `idEvento` | texto (UUID, máx. 64) | ✅ | Identificador único del mensaje |
| `tipoEvento` | `CLIENTE_REGISTRADO` \| `CLIENTE_ACTUALIZADO` | ✅ | Alta o cambio en el sistema de origen |
| `origen` | `MARKETPLACE` \| `VENTAS` | ✅ | Sistema que registró el cambio |
| `fechaEmision` | fecha y hora ISO-8601 con zona | ✅ | Momento en que se publicó el evento |
| `responsable` | texto (máx. 100) | | Usuario del sistema de origen que hizo el cambio. Si no viene, se registra `sincronizacion-automatica` |
| `cliente.idCliente` | texto (máx. 64) | ✅ | Identificador del cliente en el sistema de origen |
| `cliente.fechaActualizacion` | fecha y hora ISO-8601 con zona | | Momento del cambio en el sistema de origen. Ordena los eventos: uno más antiguo que el último aplicado se descarta. Si no viene, se usa `fechaEmision` |
| `cliente.nombres` | texto (máx. 100) | ✅* | Nombres |
| `cliente.apellidos` | texto (máx. 100) | ✅* | Apellidos |
| `cliente.tipoDocumento` | `CI` \| `NIT` \| `PASAPORTE` \| `CE` | ✅* | Tipo de documento de identidad |
| `cliente.numeroDocumento` | texto, 4 a 20 letras, números o guiones | ✅* | Número de documento |
| `cliente.contacto.email` | correo electrónico (máx. 150) | ✅** | Correo del cliente |
| `cliente.contacto.telefono` | texto, 7 a 20 dígitos, `+`, espacios, guiones o paréntesis | ✅** | Teléfono del cliente |
| `cliente.direcciones` | lista | | Direcciones registradas. Puede estar vacía |
| `cliente.direcciones[].idDireccion` | texto (máx. 64) | ✅ | Identificador de la dirección en el sistema de origen |
| `cliente.direcciones[].tipo` | `ENTREGA` \| `FACTURACION` \| `OTRA` | | Uso de la dirección. Si no viene, `OTRA` |
| `cliente.direcciones[].calle` | texto (máx. 200) | ✅ | Calle o avenida |
| `cliente.direcciones[].numero` | texto (máx. 20) | | Número de puerta |
| `cliente.direcciones[].zona` | texto (máx. 100) | | Zona o barrio |
| `cliente.direcciones[].ciudad` | texto (máx. 100) | ✅ | Ciudad |
| `cliente.direcciones[].referencia` | texto (máx. 300) | | Indicaciones para llegar |
| `cliente.direcciones[].principal` | booleano | | Dirección principal del cliente |

\* Obligatorio para que el perfil quede **completo**. Si falta o está mal formado, el perfil se crea igual y queda **incompleto** (ver "Qué hace el CRM al recibirlo").
\** Se necesita al menos uno de los dos medios de contacto.

Las direcciones son opcionales, pero cada dirección informada debe traer `idDireccion`, `calle` y `ciudad`: si no, esa dirección no se guarda y el perfil queda incompleto.

El evento **no transporta el consentimiento** de tratamiento de datos: el CRM lo registra aparte (SCRUM-14).

## Ejemplo

```json
{
  "idEvento": "7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f",
  "tipoEvento": "CLIENTE_REGISTRADO",
  "origen": "VENTAS",
  "fechaEmision": "2026-09-20T10:15:02-04:00",
  "responsable": "vendedor.jperez",
  "cliente": {
    "idCliente": "CLI-5521",
    "fechaActualizacion": "2026-09-20T10:15:00-04:00",
    "nombres": "Ana María",
    "apellidos": "Pérez Rojas",
    "tipoDocumento": "CI",
    "numeroDocumento": "4455667",
    "contacto": { "email": "ana.perez@correo.com", "telefono": "+591 70012345" },
    "direcciones": [
      {
        "idDireccion": "D-1",
        "tipo": "ENTREGA",
        "calle": "Av. 6 de Agosto",
        "numero": "2150",
        "zona": "Sopocachi",
        "ciudad": "La Paz",
        "referencia": "Edificio Illimani, piso 3",
        "principal": true
      }
    ]
  }
}
```

Más ejemplos en `herramientas/simulador-eventos/eventos/`.

## Qué hace el CRM al recibirlo

| Situación | Perfil | Bitácora de sincronización |
|---|---|---|
| Cliente nuevo (el par `origen` + `idCliente` no está vinculado a ningún perfil) | Se **crea** el perfil y se vincula el identificador de origen | `PROCESADO` |
| Cliente ya vinculado | Se **actualiza** el mismo perfil; nunca se crea otro | `PROCESADO` |
| `CLIENTE_ACTUALIZADO` de un cliente que el CRM aún no conoce (el alta se perdió o llegó después) | Se crea el perfil | `PROCESADO` |
| Dato obligatorio vacío o mal formado | Se guarda lo válido, el dato inválido queda vacío y el perfil queda **incompleto** con el motivo | `INCOMPLETO` con el motivo |
| Evento más antiguo que el último aplicado para ese cliente, o el mismo evento reentregado | Sin cambios | `DESCARTADO` con el motivo |
| Mensaje ilegible, `origen` o `tipoEvento` desconocidos, o sin `cliente.idCliente` | Sin cambios | `FALLIDO` con la causa y el mensaje original |

Cada creación o cambio del perfil queda registrado con fecha, sistema de origen, responsable y qué campos cambiaron.

# Contratos de eventos

Definición de los mensajes que intercambian el CRM y Marketplace y Ventas.

| Contrato | Tipo | Descripción |
|---|---|---|
| [RIO-CRM-01](RIO-CRM-01-datos-cliente.md) | Evento (RabbitMQ) | Datos del cliente |
| [RIO-CRM-02](RIO-CRM-02-compra-confirmada.md) | Evento (RabbitMQ) | Compra confirmada |
| RIO-CRM-03 | Evento (RabbitMQ) | Redención de puntos |
| RIO-CRM-04 | Consulta síncrona (REST) | Saldo y nivel de puntos de un cliente |
| [RIO-CRM-05](RIO-CRM-05-anulacion-compra.md) | Evento (RabbitMQ) | Anulación o devolución de compra |

Cada contrato se documenta en su propio archivo con sus campos, tipos y un ejemplo en JSON.

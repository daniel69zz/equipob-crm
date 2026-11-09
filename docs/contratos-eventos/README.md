# Contratos de eventos

Definición de los mensajes que intercambian el CRM y el módulo **Marketplace y Ventas** del ERP, su única integración. Los eventos RIO-CRM-01, 02, 03 y 05 vienen de ese módulo; RIO-CRM-06 lo publica el CRM. Los clientes, compras y anulaciones se identifican por su identificador en Marketplace y Ventas.

| Contrato | Tipo | Descripción |
|---|---|---|
| [RIO-CRM-01](RIO-CRM-01-datos-cliente.md) | Evento (RabbitMQ) | Datos del cliente |
| [RIO-CRM-02](RIO-CRM-02-compra-confirmada.md) | Evento (RabbitMQ) | Compra confirmada |
| [RIO-CRM-03](RIO-CRM-03-movimiento-puntos.md) | Evento (RabbitMQ) | Movimiento de puntos: acumulación, redención y reversa |
| [RIO-CRM-04](RIO-CRM-04-consulta-puntos.md) | Consulta síncrona (REST) | Saldo, nivel y caducidad de puntos de un cliente, para Marketplace y Ventas |
| [RIO-CRM-05](RIO-CRM-05-anulacion-compra.md) | Evento (RabbitMQ) | Anulación o devolución de compra |

Cada contrato se documenta en su propio archivo con sus campos, tipos y un ejemplo en JSON.

Salida del CRM: [RIO-CRM-06 · Saldo y nivel de puntos actualizados](RIO-CRM-06-saldo-puntos-actualizado.md),
evento Google Cloud Pub/Sub hacia Marketplace y Ventas, con bandeja de salida transaccional (SCRUM-594).
